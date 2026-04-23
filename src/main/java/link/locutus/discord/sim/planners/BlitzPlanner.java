package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Group blitz target assignment planner.
 *
 * <p>Algorithm (layered by cost):
 * <ol>
 *   <li><b>Candidate generation</b>: war-range filter + treaty filter + kernel-EV viability probe
 *       effort-tiering with top {@link SimTuning#candidatesPerAttacker()} retention per attacker.</li>
 *   <li><b>Initial assignment</b>: primitive-array min-cost assignment over dense candidate edges.
 *       Tie-breakers are baked into edge cost — no separate post-pass.</li>
 *   <li><b>Local search refinement</b> (optional, budget-capped): 2-opt, 1-opt add/drop/move
 *       with planner-local exact validation.</li>
 * </ol>
 *
 * <p>Inputs are immutable {@link DBNationSnapshot}s. Candidate generation/scoring runs on
 * planner-local state with snapshot-native activity inputs.</p>
 */
public final class BlitzPlanner {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();

    private final SimTuning tuning;
    private final TreatyProvider treatyProvider;
    private final OverrideSet overrides;
    private final TeamScoreObjective objective;
    private final SnapshotActivityProvider snapshotActivityProvider;

    public BlitzPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, TeamScoreObjective objective) {
        this(tuning, treatyProvider, overrides, objective, SnapshotActivityProvider.BASELINE);
    }

    public BlitzPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, TeamScoreObjective objective, SnapshotActivityProvider activityProvider) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.treatyProvider = Objects.requireNonNull(treatyProvider, "treatyProvider");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.objective = Objects.requireNonNull(objective, "objective");
        this.snapshotActivityProvider = Objects.requireNonNull(activityProvider, "activityProvider");
    }

    /** Convenience constructor with no overrides, no treaty filter, and {@link link.locutus.discord.sim.DamageObjective}. */
    public BlitzPlanner(SimTuning tuning) {
        this(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new link.locutus.discord.sim.DamageObjective(), SnapshotActivityProvider.BASELINE);
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Produces a target assignment for the given attacker and defender sets.
     *
     * @param attackers immutable snapshots of the attacking nations
     * @param defenders immutable snapshots of the defending nations
     * @return the recommended assignment and structured diagnostics
     */
    public BlitzAssignment assign(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders) {
        return assign(attackers, defenders, 0);
    }

    public BlitzAssignment assign(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int currentTurn) {
        Objects.requireNonNull(attackers, "attackers");
        Objects.requireNonNull(defenders, "defenders");

        List<DBNationSnapshot> attList = List.copyOf(attackers);
        List<DBNationSnapshot> defList = List.copyOf(defenders);

        Map<Integer, Float> activityWeights = PlannerSimSupport.compileActivityWeights(
            snapshotActivityProvider.withWartimeUplift(tuning.wartimeActivityUplift()),
            currentTurn,
            overrides,
            combinedSnapshots(attList, defList)
        );

        CompiledScenario compiledScenario = SCENARIO_COMPILER.compile(
            attList,
            defList,
            overrides,
            treatyProvider,
            activityWeights
        );

        // ---- Step 1: candidate generation ----------------------------------------
        // Effective slot counts (after overrides applied)
        int[] attCaps = computeAttackerCaps(compiledScenario);
        int[] defCaps = computeDefenderCaps(compiledScenario);

        // Attacker strength rank (0 = strongest) for tie-breaking in edge cost
        int[] attStrengthRank = computeStrengthRanks(compiledScenario);

        GeneratedCandidates candidates = generateCandidates(compiledScenario, attCaps, defCaps);

        List<PlannerDiagnostic> diagnostics = new ArrayList<>();
        collectDiagnostics(attList, defList, diagnostics);

        if (candidates.edgeTable().edgeCount() == 0) {
            return new BlitzAssignment(Map.of(), diagnostics, 0.0);
        }

        // ---- Step 2: initial assignment -------------------------------------------
        Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
            candidates.edgeTable(),
            compiledScenario.attackerCount(),
            compiledScenario.defenderCount(),
            attCaps,
            defCaps,
            attStrengthRank,
            attackerNationIds(compiledScenario),
            defenderNationIds(compiledScenario)
        );

        Map<Integer, Integer> attCapsByNationId = capsByNationId(compiledScenario, attCaps, true);
        Map<Integer, Integer> defCapsByNationId = capsByNationId(compiledScenario, defCaps, false);

        // ---- Step 3: local search refinement (budget-capped) ----------------------
        assignment = localSearch(assignment, candidates, attCapsByNationId, defCapsByNationId, attList, defList);
        Map<Integer, List<Integer>> finalAssignment = assignment;

        ScoreSummary objectiveSummary = PlannerSimSupport.summarizeScores(tuning, sampleIndex -> {
            SimTuning scoringTuning = tuning.stateResolutionMode() == link.locutus.discord.sim.combat.ResolutionMode.STOCHASTIC
                ? PlannerSimSupport.sampleTuning(tuning, sampleIndex)
                : tuning;
            return PlannerSimSupport.scoreAssignment(scoringTuning, overrides, objective, finalAssignment, attList, defList);
        });
        return new BlitzAssignment(finalAssignment, diagnostics, objectiveSummary.mean(), objectiveSummary);
    }

    // ============================================================
    // Candidate generation
    // ============================================================

    private GeneratedCandidates generateCandidates(
            CompiledScenario compiledScenario,
            int[] attCaps,
            int[] defCaps) {
        CandidateEdgeTable edges = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
            compiledScenario,
            tuning,
            overrides,
            objective,
            attCaps,
            defCaps,
            edges
        );

        Map<Integer, List<Integer>> candidateDefendersByAttacker = new HashMap<>();
        Map<Long, Float> edgeScoresByPair = new HashMap<>(Math.max(16, edges.edgeCount() * 2));

        for (int edge = 0; edge < edges.edgeCount(); edge++) {
            int attackerIndex = edges.attackerIndex(edge);
            int defenderIndex = edges.defenderIndex(edge);
            int attackerNationId = compiledScenario.attackerNationId(attackerIndex);
            int defenderNationId = compiledScenario.defenderNationId(defenderIndex);

            candidateDefendersByAttacker
                .computeIfAbsent(attackerNationId, ignored -> new ArrayList<>())
                .add(defenderNationId);
            edgeScoresByPair.put(pairKey(attackerNationId, defenderNationId), edges.scalarScore(edge));
        }

        return new GeneratedCandidates(edges, candidateDefendersByAttacker, edgeScoresByPair);
    }

    // ============================================================
    // Local search
    // ============================================================

    private Map<Integer, List<Integer>> localSearch(
            Map<Integer, List<Integer>> assignment,
            GeneratedCandidates candidates,
            Map<Integer, Integer> attCaps,
            Map<Integer, Integer> defCaps,
            List<DBNationSnapshot> attList,
            List<DBNationSnapshot> defList) {

        long budgetMs = tuning.localSearchBudgetMs();
        int maxIter = tuning.localSearchMaxIterations();
        if (budgetMs <= 0 || maxIter <= 0) return assignment;

        long deadline = System.currentTimeMillis() + budgetMs;

        // Collect all defenders that have remaining cap
        Map<Integer, Integer> defUsed = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : assignment.entrySet()) {
            for (int dId : e.getValue()) {
                defUsed.merge(dId, 1, Integer::sum);
            }
        }

        Map<Integer, List<Integer>> best = copyAssignment(assignment);
        int attackerTeamId = attList.isEmpty() ? 1 : attList.get(0).teamId();
        RefinementAggregates aggregates = RefinementAggregates.fromAssignment(best, attCaps, defCaps, attList, defList);

        int iter = 0;
        while (iter < maxIter && System.currentTimeMillis() < deadline) {
            iter++;

            // Try 2-opt swaps
            List<Integer> attackerIds = new ArrayList<>(best.keySet());
            boolean improved = false;
            outer:
            for (int i = 0; i < attackerIds.size(); i++) {
                for (int j = i + 1; j < attackerIds.size(); j++) {
                    int a1 = attackerIds.get(i);
                    int a2 = attackerIds.get(j);
                    List<Integer> d1List = best.get(a1);
                    List<Integer> d2List = best.get(a2);
                    if (d1List == null || d2List == null) continue;

                    for (int di1 = 0; di1 < d1List.size(); di1++) {
                        for (int di2 = 0; di2 < d2List.size(); di2++) {
                            int d1 = d1List.get(di1);
                            int d2 = d2List.get(di2);
                            if (d1 == d2) continue;

                            // Check if (a1->d2) and (a2->d1) are valid candidates
                            boolean a1d2 = candidates.containsPair(a1, d2);
                            boolean a2d1 = candidates.containsPair(a2, d1);
                            if (!a1d2 || !a2d1) continue;
                            if (containsOtherAssignment(d1List, d2, di1) || containsOtherAssignment(d2List, d1, di2)) {
                                continue;
                            }

                            // Perform swap and score
                            Map<Integer, List<Integer>> candidate = swapOverlay(best, a1, di1, d2, a2, di2, d1);
                            double surrogateDelta = aggregates.swapDelta(a1, d1, d2, a2, d2, d1, candidates);
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                            if (exactDelta > 1e-9) {
                                applySwap(best, a1, di1, d2, a2, di2, d1);
                                aggregates.applySwap();
                                improved = true;
                                break outer;
                            }
                        }
                    }
                    if (System.currentTimeMillis() > deadline) break outer;
                }
            }

            // Try 1-opt move: retarget one attacker slot from defender A to defender B
            if (!improved) {
                outerMove:
                for (int aId : attackerIds) {
                    List<Integer> assignedDefenders = best.get(aId);
                    if (assignedDefenders == null || assignedDefenders.isEmpty()) {
                        continue;
                    }
                    List<Integer> candidateDefenders = candidates.candidateDefendersByAttacker().getOrDefault(aId, List.of());
                    for (int assignedIndex = 0; assignedIndex < assignedDefenders.size(); assignedIndex++) {
                        int previousDefenderId = assignedDefenders.get(assignedIndex);
                        for (int nextDefenderId : candidateDefenders) {
                            if (nextDefenderId == previousDefenderId) {
                                continue;
                            }
                            if (containsOtherAssignment(assignedDefenders, nextDefenderId, assignedIndex)) {
                                continue;
                            }
                            int nextUsed = defUsed.getOrDefault(nextDefenderId, 0);
                            int nextCap = defCaps.getOrDefault(nextDefenderId, 0);
                            if (nextUsed >= nextCap) {
                                continue;
                            }

                            Map<Integer, List<Integer>> candidate = moveOverlay(best, aId, assignedIndex, nextDefenderId);
                            double surrogateDelta = aggregates.moveDelta(aId, previousDefenderId, nextDefenderId, candidates);
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                            if (exactDelta > 1e-9) {
                                applyMove(best, aId, assignedIndex, nextDefenderId);
                                aggregates.applyMove(previousDefenderId, nextDefenderId);
                                defUsed.merge(previousDefenderId, -1, Integer::sum);
                                if (defUsed.getOrDefault(previousDefenderId, 0) <= 0) {
                                    defUsed.remove(previousDefenderId);
                                }
                                defUsed.merge(nextDefenderId, 1, Integer::sum);
                                improved = true;
                                break outerMove;
                            }
                        }
                        if (System.currentTimeMillis() > deadline) {
                            break outerMove;
                        }
                    }
                }
            }

            // Try 1-opt add: assign an attacker with free capacity to a defender with free capacity
            if (!improved) {
                for (DBNationSnapshot att : attList) {
                    int aId = att.nationId();
                    int cap = attCaps.getOrDefault(aId, 0);
                    int used = best.getOrDefault(aId, List.of()).size();
                    if (used >= cap) continue;

                    List<Integer> candidateDefenders = candidates.candidateDefendersByAttacker().getOrDefault(aId, List.of());
                    for (int dId : candidateDefenders) {
                        int dUsedCount = defUsed.getOrDefault(dId, 0);
                        int dCap = defCaps.getOrDefault(dId, 0);
                        if (dUsedCount >= dCap) continue;

                        // Check not already assigned
                        List<Integer> existing = best.getOrDefault(aId, List.of());
                        if (existing.contains(dId)) continue;

                        Map<Integer, List<Integer>> candidate = addOverlay(best, aId, dId);
                        double surrogateDelta = aggregates.addDelta(aId, dId, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                        if (exactDelta > 1e-9) {
                            applyAdd(best, aId, dId);
                            aggregates.applyAdd(aId, dId);
                            defUsed.merge(dId, 1, Integer::sum);
                            improved = true;
                            break;
                        }
                    }
                    if (improved) break;
                }
            }

            // Try 1-opt drop: release a marginal edge into slack.
            if (!improved) {
                outerDrop:
                for (int aId : attackerIds) {
                    List<Integer> assignedDefenders = best.get(aId);
                    if (assignedDefenders == null || assignedDefenders.isEmpty()) {
                        continue;
                    }
                    for (int assignedIndex = 0; assignedIndex < assignedDefenders.size(); assignedIndex++) {
                        int removedDefenderId = assignedDefenders.get(assignedIndex);
                        Map<Integer, List<Integer>> candidate = dropOverlay(best, aId, assignedIndex);
                        double surrogateDelta = aggregates.dropDelta(aId, removedDefenderId, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                        if (exactDelta > 1e-9) {
                            applyDrop(best, aId, assignedIndex);
                            aggregates.applyDrop(aId, removedDefenderId);
                            defUsed.merge(removedDefenderId, -1, Integer::sum);
                            if (defUsed.getOrDefault(removedDefenderId, 0) <= 0) {
                                defUsed.remove(removedDefenderId);
                            }
                            improved = true;
                            break outerDrop;
                        }
                        if (System.currentTimeMillis() > deadline) {
                            break outerDrop;
                        }
                    }
                }
            }

            if (!improved) break; // Hill-climb converged
        }
        return best;
    }

    private double exactBundleDelta(
            Map<Integer, List<Integer>> currentAssignment,
            Map<Integer, List<Integer>> candidateAssignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            int attackerTeamId
    ) {
        return PlannerConflictExecutor.scoreAssignmentDelta(
            tuning,
            overrides,
            objective,
            currentAssignment,
            candidateAssignment,
            attackers,
            defenders,
            attackerTeamId
        );
    }

    // ============================================================
    // Slot capacity helpers
    // ============================================================

    private int[] computeAttackerCaps(CompiledScenario compiledScenario) {
        int[] caps = new int[compiledScenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < compiledScenario.attackerCount(); attackerIndex++) {
            int freeOff = compiledScenario.attackerFreeOffSlots(attackerIndex);
            caps[attackerIndex] = Math.max(0, freeOff);
        }
        return caps;
    }

    private int[] computeDefenderCaps(CompiledScenario compiledScenario) {
        int[] caps = new int[compiledScenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < compiledScenario.defenderCount(); defenderIndex++) {
            int freeDef = compiledScenario.defenderFreeDefSlots(defenderIndex);
            caps[defenderIndex] = Math.max(0, freeDef);
        }
        return caps;
    }

    /**
     * Returns a map of nationId → strength rank (0 = strongest).
     * Strength is approximated by a weighted unit score (air × 3 + ground × 1 + naval × 2).
     */
    private int[] computeStrengthRanks(CompiledScenario compiledScenario) {
        List<Integer> attackerIndexes = new ArrayList<>(compiledScenario.attackerCount());
        for (int attackerIndex = 0; attackerIndex < compiledScenario.attackerCount(); attackerIndex++) {
            attackerIndexes.add(attackerIndex);
        }
        attackerIndexes.sort(Comparator.comparingDouble((Integer index) -> attackerStrength(compiledScenario.attacker(index))).reversed());
        int[] ranks = new int[compiledScenario.attackerCount()];
        for (int rank = 0; rank < attackerIndexes.size(); rank++) {
            ranks[attackerIndexes.get(rank)] = rank;
        }
        return ranks;
    }

    private double attackerStrength(DBNationSnapshot snap) {
        double ground = UnitEconomy.groundStrengthRaw(
                snap.unit(MilitaryUnit.SOLDIER),
                snap.unit(MilitaryUnit.TANK),
                false,
                false
        );
        return ground
                + snap.unit(MilitaryUnit.AIRCRAFT) * 3.0
                + snap.unit(MilitaryUnit.SHIP) * 2.0;
    }

    // ============================================================
    // Utilities
    // ============================================================

    private static int[] attackerNationIds(CompiledScenario compiledScenario) {
        int[] nationIds = new int[compiledScenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < compiledScenario.attackerCount(); attackerIndex++) {
            nationIds[attackerIndex] = compiledScenario.attackerNationId(attackerIndex);
        }
        return nationIds;
    }

    private static int[] defenderNationIds(CompiledScenario compiledScenario) {
        int[] nationIds = new int[compiledScenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < compiledScenario.defenderCount(); defenderIndex++) {
            nationIds[defenderIndex] = compiledScenario.defenderNationId(defenderIndex);
        }
        return nationIds;
    }

    private static Map<Integer, Integer> capsByNationId(CompiledScenario compiledScenario, int[] capsByIndex, boolean attacker) {
        Map<Integer, Integer> caps = new HashMap<>(capsByIndex.length);
        for (int index = 0; index < capsByIndex.length; index++) {
            if (capsByIndex[index] <= 0) {
                continue;
            }
            int nationId = attacker ? compiledScenario.attackerNationId(index) : compiledScenario.defenderNationId(index);
            caps.put(nationId, capsByIndex[index]);
        }
        return caps;
    }

    private static Map<Integer, List<Integer>> copyAssignment(Map<Integer, List<Integer>> src) {
        Map<Integer, List<Integer>> copy = new LinkedHashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    private static Map<Integer, List<Integer>> swapOverlay(
            Map<Integer, List<Integer>> base,
            int attackerOne,
            int attackerOneIndex,
            int newDefenderOne,
            int attackerTwo,
            int attackerTwoIndex,
            int newDefenderTwo
    ) {
        Map<Integer, List<Integer>> overrides = new LinkedHashMap<>(2);
        overrides.put(attackerOne, replacedList(base.get(attackerOne), attackerOneIndex, newDefenderOne));
        overrides.put(attackerTwo, replacedList(base.get(attackerTwo), attackerTwoIndex, newDefenderTwo));
        return new TrialAssignmentOverlay(base, overrides);
    }

    private static Map<Integer, List<Integer>> moveOverlay(
            Map<Integer, List<Integer>> base,
            int attackerId,
            int assignedIndex,
            int newDefenderId
    ) {
        return new TrialAssignmentOverlay(base, Map.of(attackerId, replacedList(base.get(attackerId), assignedIndex, newDefenderId)));
    }

    private static Map<Integer, List<Integer>> addOverlay(
            Map<Integer, List<Integer>> base,
            int attackerId,
            int defenderId
    ) {
        List<Integer> existing = base.get(attackerId);
        List<Integer> next = new ArrayList<>(existing == null ? List.<Integer>of() : existing);
        next.add(defenderId);
        return new TrialAssignmentOverlay(base, Map.of(attackerId, List.copyOf(next)));
    }

    private static Map<Integer, List<Integer>> dropOverlay(
            Map<Integer, List<Integer>> base,
            int attackerId,
            int assignedIndex
    ) {
        List<Integer> existing = base.get(attackerId);
        List<Integer> next = new ArrayList<>(existing);
        next.remove(assignedIndex);
        return new TrialAssignmentOverlay(base, Map.of(attackerId, List.copyOf(next)));
    }

    private static List<Integer> replacedList(List<Integer> base, int index, int newValue) {
        List<Integer> next = new ArrayList<>(base);
        next.set(index, newValue);
        return List.copyOf(next);
    }

    private static void applySwap(
            Map<Integer, List<Integer>> best,
            int attackerOne,
            int attackerOneIndex,
            int newDefenderOne,
            int attackerTwo,
            int attackerTwoIndex,
            int newDefenderTwo
    ) {
        best.get(attackerOne).set(attackerOneIndex, newDefenderOne);
        best.get(attackerTwo).set(attackerTwoIndex, newDefenderTwo);
    }

    private static void applyMove(
            Map<Integer, List<Integer>> best,
            int attackerId,
            int assignedIndex,
            int newDefenderId
    ) {
        best.get(attackerId).set(assignedIndex, newDefenderId);
    }

    private static void applyAdd(
            Map<Integer, List<Integer>> best,
            int attackerId,
            int defenderId
    ) {
        best.computeIfAbsent(attackerId, ignored -> new ArrayList<>()).add(defenderId);
    }

    private static void applyDrop(
            Map<Integer, List<Integer>> best,
            int attackerId,
            int assignedIndex
    ) {
        List<Integer> defenders = best.get(attackerId);
        defenders.remove(assignedIndex);
        if (defenders.isEmpty()) {
            best.remove(attackerId);
        }
    }

    private static boolean containsOtherAssignment(List<Integer> defenderIds, int defenderId, int excludedIndex) {
        for (int index = 0; index < defenderIds.size(); index++) {
            if (index != excludedIndex && defenderIds.get(index) == defenderId) {
                return true;
            }
        }
        return false;
    }

    private static List<DBNationSnapshot> combinedSnapshots(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
        List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        return combined;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }

    private void collectDiagnostics(
            List<DBNationSnapshot> attList,
            List<DBNationSnapshot> defList,
            List<PlannerDiagnostic> diagnostics
    ) {
        PlannerSimSupport.collectResetDiagnostics(attList, defList, diagnostics);
    }

    private record GeneratedCandidates(
        CandidateEdgeTable edgeTable,
        Map<Integer, List<Integer>> candidateDefendersByAttacker,
        Map<Long, Float> edgeScoresByPair
    ) {
        boolean containsPair(int attackerNationId, int defenderNationId) {
            return edgeScoresByPair.containsKey(pairKey(attackerNationId, defenderNationId));
        }

        float edgeScore(int attackerNationId, int defenderNationId) {
            return edgeScoresByPair.getOrDefault(pairKey(attackerNationId, defenderNationId), Float.NEGATIVE_INFINITY);
        }
    }

    private static final class TrialAssignmentOverlay extends AbstractMap<Integer, List<Integer>> {
        private final Map<Integer, List<Integer>> base;
        private final Map<Integer, List<Integer>> overrides;
        private final Set<Entry<Integer, List<Integer>>> entrySet;

        private TrialAssignmentOverlay(
                Map<Integer, List<Integer>> base,
                Map<Integer, List<Integer>> overrides
        ) {
            this.base = base;
            this.overrides = overrides;
            this.entrySet = new AbstractSet<>() {
                @Override
                public Iterator<Entry<Integer, List<Integer>>> iterator() {
                    Iterator<Entry<Integer, List<Integer>>> baseIterator = TrialAssignmentOverlay.this.base.entrySet().iterator();
                    Iterator<Entry<Integer, List<Integer>>> overrideIterator = extraOverrideEntries().iterator();
                    Set<Integer> seen = new HashSet<>(TrialAssignmentOverlay.this.base.size() + TrialAssignmentOverlay.this.overrides.size());
                    return new Iterator<>() {
                        private Entry<Integer, List<Integer>> next;

                        @Override
                        public boolean hasNext() {
                            if (next != null) {
                                return true;
                            }
                            while (baseIterator.hasNext()) {
                                Entry<Integer, List<Integer>> entry = baseIterator.next();
                                seen.add(entry.getKey());
                                if (TrialAssignmentOverlay.this.overrides.containsKey(entry.getKey())) {
                                    next = Map.entry(entry.getKey(), TrialAssignmentOverlay.this.overrides.get(entry.getKey()));
                                } else {
                                    next = entry;
                                }
                                return true;
                            }
                            while (overrideIterator.hasNext()) {
                                Entry<Integer, List<Integer>> entry = overrideIterator.next();
                                if (seen.add(entry.getKey())) {
                                    next = entry;
                                    return true;
                                }
                            }
                            return false;
                        }

                        @Override
                        public Entry<Integer, List<Integer>> next() {
                            if (!hasNext()) {
                                throw new java.util.NoSuchElementException();
                            }
                            Entry<Integer, List<Integer>> result = next;
                            next = null;
                            return result;
                        }
                    };
                }

                @Override
                public int size() {
                    Set<Integer> keys = new LinkedHashSet<>(TrialAssignmentOverlay.this.base.keySet());
                    keys.addAll(TrialAssignmentOverlay.this.overrides.keySet());
                    return keys.size();
                }
            };
        }

        @Override
        public List<Integer> get(Object key) {
            if (overrides.containsKey(key)) {
                return overrides.get(key);
            }
            return base.get(key);
        }

        @Override
        public Set<Entry<Integer, List<Integer>>> entrySet() {
            return entrySet;
        }

        private Set<Entry<Integer, List<Integer>>> extraOverrideEntries() {
            LinkedHashSet<Entry<Integer, List<Integer>>> extras = new LinkedHashSet<>();
            for (Entry<Integer, List<Integer>> entry : overrides.entrySet()) {
                if (!base.containsKey(entry.getKey())) {
                    extras.add(Map.entry(entry.getKey(), entry.getValue()));
                }
            }
            return extras;
        }
    }

    private static final class RefinementAggregates {
        private static final double DEFENDER_PRESSURE_WEIGHT = 0.06;
        private static final double ATTACKER_COMMITMENT_WEIGHT = 0.04;
        private static final double SURROGATE_EVAL_FLOOR = -0.05;

        private final Map<Integer, Integer> attackerIndexByNationId;
        private final Map<Integer, Integer> defenderIndexByNationId;
        private final int[] attackerAssignedCount;
        private final int[] defenderAssignedCount;
        private final double[] attackerCommitmentLoad;
        private final double[] defenderPressure;
        private final Map<Integer, Integer> attackerCaps;
        private final Map<Integer, Integer> defenderCaps;

        private RefinementAggregates(
            Map<Integer, Integer> attackerIndexByNationId,
            Map<Integer, Integer> defenderIndexByNationId,
            int[] attackerAssignedCount,
            int[] defenderAssignedCount,
            double[] attackerCommitmentLoad,
            double[] defenderPressure,
            Map<Integer, Integer> attackerCaps,
            Map<Integer, Integer> defenderCaps
        ) {
            this.attackerIndexByNationId = attackerIndexByNationId;
            this.defenderIndexByNationId = defenderIndexByNationId;
            this.attackerAssignedCount = attackerAssignedCount;
            this.defenderAssignedCount = defenderAssignedCount;
            this.attackerCommitmentLoad = attackerCommitmentLoad;
            this.defenderPressure = defenderPressure;
            this.attackerCaps = attackerCaps;
            this.defenderCaps = defenderCaps;
        }

        static RefinementAggregates fromAssignment(
            Map<Integer, List<Integer>> assignment,
            Map<Integer, Integer> attackerCaps,
            Map<Integer, Integer> defenderCaps,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
        ) {
            Map<Integer, Integer> attackerIndexByNationId = new HashMap<>(attackers.size() * 2);
            for (int i = 0; i < attackers.size(); i++) {
                attackerIndexByNationId.put(attackers.get(i).nationId(), i);
            }
            Map<Integer, Integer> defenderIndexByNationId = new HashMap<>(defenders.size() * 2);
            for (int i = 0; i < defenders.size(); i++) {
                defenderIndexByNationId.put(defenders.get(i).nationId(), i);
            }

            int[] attackerAssignedCount = new int[attackers.size()];
            int[] defenderAssignedCount = new int[defenders.size()];
            double[] attackerCommitmentLoad = new double[attackers.size()];
            double[] defenderPressure = new double[defenders.size()];

            for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
                int attackerNationId = entry.getKey();
                Integer attackerIndex = attackerIndexByNationId.get(attackerNationId);
                if (attackerIndex == null) {
                    continue;
                }
                for (int defenderNationId : entry.getValue()) {
                    Integer defenderIndex = defenderIndexByNationId.get(defenderNationId);
                    if (defenderIndex == null) {
                        continue;
                    }
                    attackerAssignedCount[attackerIndex]++;
                    defenderAssignedCount[defenderIndex]++;
                }
            }

            for (Map.Entry<Integer, Integer> entry : attackerIndexByNationId.entrySet()) {
                int nationId = entry.getKey();
                int index = entry.getValue();
                int cap = Math.max(1, attackerCaps.getOrDefault(nationId, 1));
                attackerCommitmentLoad[index] = (double) attackerAssignedCount[index] / cap;
            }
            for (Map.Entry<Integer, Integer> entry : defenderIndexByNationId.entrySet()) {
                int nationId = entry.getKey();
                int index = entry.getValue();
                int cap = Math.max(1, defenderCaps.getOrDefault(nationId, 1));
                defenderPressure[index] = (double) defenderAssignedCount[index] / cap;
            }

            return new RefinementAggregates(
                attackerIndexByNationId,
                defenderIndexByNationId,
                attackerAssignedCount,
                defenderAssignedCount,
                attackerCommitmentLoad,
                defenderPressure,
                attackerCaps,
                defenderCaps
            );
        }

        boolean isPromising(double surrogateDelta) {
            return surrogateDelta > SURROGATE_EVAL_FLOOR;
        }

        double addDelta(int attackerNationId, int defenderNationId, GeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(attackerNationId, defenderNationId);
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return edgeScore
                + attackerPenaltyDelta(attackerNationId, +1)
                + defenderPenaltyDelta(defenderNationId, +1);
        }

        double dropDelta(int attackerNationId, int defenderNationId, GeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(attackerNationId, defenderNationId);
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return -edgeScore
                + attackerPenaltyDelta(attackerNationId, -1)
                + defenderPenaltyDelta(defenderNationId, -1);
        }

        double moveDelta(int attackerNationId, int oldDefenderNationId, int newDefenderNationId, GeneratedCandidates candidates) {
            float dropScore = candidates.edgeScore(attackerNationId, oldDefenderNationId);
            float addScore = candidates.edgeScore(attackerNationId, newDefenderNationId);
            if (!Float.isFinite(dropScore) || !Float.isFinite(addScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (addScore - dropScore)
                + defenderPenaltyDelta(oldDefenderNationId, -1)
                + defenderPenaltyDelta(newDefenderNationId, +1);
        }

        double swapDelta(
            int attackerOne,
            int oldDefenderOne,
            int newDefenderOne,
            int attackerTwo,
            int oldDefenderTwo,
            int newDefenderTwo,
            GeneratedCandidates candidates
        ) {
            float oldOne = candidates.edgeScore(attackerOne, oldDefenderOne);
            float oldTwo = candidates.edgeScore(attackerTwo, oldDefenderTwo);
            float nextOne = candidates.edgeScore(attackerOne, newDefenderOne);
            float nextTwo = candidates.edgeScore(attackerTwo, newDefenderTwo);
            if (!Float.isFinite(oldOne) || !Float.isFinite(oldTwo) || !Float.isFinite(nextOne) || !Float.isFinite(nextTwo)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (nextOne + nextTwo) - (oldOne + oldTwo);
        }

        void applyAdd(int attackerNationId, int defenderNationId) {
            applyAttacker(attackerNationId, +1);
            applyDefender(defenderNationId, +1);
        }

        void applyDrop(int attackerNationId, int defenderNationId) {
            applyAttacker(attackerNationId, -1);
            applyDefender(defenderNationId, -1);
        }

        void applyMove(int oldDefenderNationId, int newDefenderNationId) {
            applyDefender(oldDefenderNationId, -1);
            applyDefender(newDefenderNationId, +1);
        }

        void applySwap() {
            // Swap preserves attacker loads and defender counts when defenders differ.
        }

        private double attackerPenaltyDelta(int attackerNationId, int delta) {
            Integer index = attackerIndexByNationId.get(attackerNationId);
            if (index == null) {
                return 0.0;
            }
            int cap = Math.max(1, attackerCaps.getOrDefault(attackerNationId, 1));
            double before = attackerCommitmentLoad[index];
            double after = (double) (attackerAssignedCount[index] + delta) / cap;
            return -ATTACKER_COMMITMENT_WEIGHT * ((after * after) - (before * before));
        }

        private double defenderPenaltyDelta(int defenderNationId, int delta) {
            Integer index = defenderIndexByNationId.get(defenderNationId);
            if (index == null) {
                return 0.0;
            }
            int cap = Math.max(1, defenderCaps.getOrDefault(defenderNationId, 1));
            double before = defenderPressure[index];
            double after = (double) (defenderAssignedCount[index] + delta) / cap;
            return -DEFENDER_PRESSURE_WEIGHT * ((after * after) - (before * before));
        }

        private void applyAttacker(int attackerNationId, int delta) {
            Integer index = attackerIndexByNationId.get(attackerNationId);
            if (index == null) {
                return;
            }
            attackerAssignedCount[index] += delta;
            int cap = Math.max(1, attackerCaps.getOrDefault(attackerNationId, 1));
            attackerCommitmentLoad[index] = (double) attackerAssignedCount[index] / cap;
        }

        private void applyDefender(int defenderNationId, int delta) {
            Integer index = defenderIndexByNationId.get(defenderNationId);
            if (index == null) {
                return;
            }
            defenderAssignedCount[index] += delta;
            int cap = Math.max(1, defenderCaps.getOrDefault(defenderNationId, 1));
            defenderPressure[index] = (double) defenderAssignedCount[index] / cap;
        }
    }
}

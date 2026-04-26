package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        return assign(attackers, defenders, currentTurn, List.of());
    }

    public BlitzAssignment assign(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int currentTurn,
            Collection<BlitzFixedEdge> fixedEdges) {
        Objects.requireNonNull(attackers, "attackers");
        Objects.requireNonNull(defenders, "defenders");
        Objects.requireNonNull(fixedEdges, "fixedEdges");

        List<DBNationSnapshot> attList = List.copyOf(attackers);
        List<DBNationSnapshot> defList = List.copyOf(defenders);
        List<BlitzFixedEdge> fixedEdgeList = List.copyOf(fixedEdges);

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
            defenderNationIds(compiledScenario),
            fixedEdgeList
        );

        Map<Integer, Integer> attCapsByNationId = capsByNationId(compiledScenario, attCaps, true);
        Map<Integer, Integer> defCapsByNationId = capsByNationId(compiledScenario, defCaps, false);
        boolean excludeReciprocalPairs = hasOverlappingNationIds(attList, defList);

        if (excludeReciprocalPairs) {
            assignment = normalizeReciprocalPairs(assignment, candidates, fixedEdgeList);
        }

        // ---- Step 3: local search refinement (budget-capped) ----------------------
        assignment = localSearch(
            assignment,
            candidates,
            attCapsByNationId,
            defCapsByNationId,
            attList,
            defList,
            fixedEdgeList,
            excludeReciprocalPairs
        );
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

        for (int edge = 0; edge < edges.edgeCount(); edge++) {
            float attackerActivityWeight = compiledScenario.attackerActivityWeight(edges.attackerIndex(edge));
            edges.scaleScalarScore(edge, attackerActivityWeight);
        }

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
            List<DBNationSnapshot> defList,
            List<BlitzFixedEdge> fixedEdges,
            boolean excludeReciprocalPairs) {

        long budgetMs = tuning.localSearchBudgetMs();
        int maxIter = tuning.localSearchMaxIterations();
        if (budgetMs <= 0 || maxIter <= 0) return assignment;

        long deadline = System.currentTimeMillis() + budgetMs;

        PlannerAssignmentSession best = PlannerAssignmentSession.create(assignment, attList, defList, attCaps, defCaps, fixedEdges);
        int[][] candidateDefenderSlotsByAttacker = candidateDefenderSlotsByAttacker(candidates, best);
        int attackerTeamId = attList.isEmpty() ? 1 : attList.get(0).teamId();
        RefinementAggregates aggregates = RefinementAggregates.fromAssignment(best);

        int iter = 0;
        while (iter < maxIter && System.currentTimeMillis() < deadline) {
            iter++;

            // Try 2-opt swaps
            boolean improved = false;
            outer:
            for (int attackerOneSlot = 0; attackerOneSlot < best.attackerCount(); attackerOneSlot++) {
                if (!best.hasAssignments(attackerOneSlot)) {
                    continue;
                }
                for (int attackerTwoSlot = attackerOneSlot + 1; attackerTwoSlot < best.attackerCount(); attackerTwoSlot++) {
                    if (!best.hasAssignments(attackerTwoSlot)) {
                        continue;
                    }

                    for (int defenderOneIndex = 0; defenderOneIndex < best.assignedCount(attackerOneSlot); defenderOneIndex++) {
                        if (best.isLocked(attackerOneSlot, defenderOneIndex)) {
                            continue;
                        }
                        for (int defenderTwoIndex = 0; defenderTwoIndex < best.assignedCount(attackerTwoSlot); defenderTwoIndex++) {
                            if (best.isLocked(attackerTwoSlot, defenderTwoIndex)) {
                                continue;
                            }
                            int defenderOneSlot = best.defenderSlotAt(attackerOneSlot, defenderOneIndex);
                            int defenderTwoSlot = best.defenderSlotAt(attackerTwoSlot, defenderTwoIndex);
                            if (defenderOneSlot == defenderTwoSlot) continue;

                            int attackerOneId = best.attackerNationIdAt(attackerOneSlot);
                            int attackerTwoId = best.attackerNationIdAt(attackerTwoSlot);
                            int defenderOneId = best.defenderNationIdAt(defenderOneSlot);
                            int defenderTwoId = best.defenderNationIdAt(defenderTwoSlot);

                            // Check if (a1->d2) and (a2->d1) are valid candidates
                            boolean a1d2 = candidates.containsPair(attackerOneId, defenderTwoId);
                            boolean a2d1 = candidates.containsPair(attackerTwoId, defenderOneId);
                            if (!a1d2 || !a2d1) continue;
                            if (excludeReciprocalPairs
                                    && (wouldCreateReciprocalPair(best, attackerOneId, defenderTwoId, attackerTwoSlot, defenderTwoIndex)
                                    || wouldCreateReciprocalPair(best, attackerTwoId, defenderOneId, attackerOneSlot, defenderOneIndex))) {
                                continue;
                            }
                            if (best.containsDefenderSlotExcept(attackerOneSlot, defenderTwoSlot, defenderOneIndex)
                                    || best.containsDefenderSlotExcept(attackerTwoSlot, defenderOneSlot, defenderTwoIndex)) {
                                continue;
                            }

                            // Perform swap and score
                            PlannerAssignmentChange candidate = best.swapChange(
                                    attackerOneSlot,
                                    defenderOneIndex,
                                    defenderTwoSlot,
                                    attackerTwoSlot,
                                    defenderTwoIndex,
                                    defenderOneSlot
                            );
                            double surrogateDelta = aggregates.swapDelta(
                                    best,
                                    attackerOneSlot,
                                    defenderOneSlot,
                                    defenderTwoSlot,
                                    attackerTwoSlot,
                                    defenderTwoSlot,
                                    defenderOneSlot,
                                    candidates
                            );
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                            if (exactDelta > 1e-9) {
                                best.applySwap(
                                        attackerOneSlot,
                                        defenderOneIndex,
                                        defenderTwoSlot,
                                        attackerTwoSlot,
                                        defenderTwoIndex,
                                        defenderOneSlot
                                );
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
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    if (!best.hasAssignments(attackerSlot)) {
                        continue;
                    }
                    int[] candidateDefenderSlots = candidateDefenderSlotsByAttacker[attackerSlot];
                    for (int assignedIndex = 0; assignedIndex < best.assignedCount(attackerSlot); assignedIndex++) {
                        if (best.isLocked(attackerSlot, assignedIndex)) {
                            continue;
                        }
                        int previousDefenderSlot = best.defenderSlotAt(attackerSlot, assignedIndex);
                        for (int nextDefenderSlot : candidateDefenderSlots) {
                            if (nextDefenderSlot == previousDefenderSlot) {
                                continue;
                            }
                            if (best.containsDefenderSlotExcept(attackerSlot, nextDefenderSlot, assignedIndex)) {
                                continue;
                            }
                            if (excludeReciprocalPairs && wouldCreateReciprocalPair(
                                    best,
                                    best.attackerNationIdAt(attackerSlot),
                                    best.defenderNationIdAt(nextDefenderSlot),
                                    -1,
                                    -1)) {
                                continue;
                            }
                            int nextUsed = best.defenderAssignedCount(nextDefenderSlot);
                            int nextCap = best.defenderCap(nextDefenderSlot);
                            if (nextUsed >= nextCap) {
                                continue;
                            }

                            PlannerAssignmentChange candidate = best.moveChange(attackerSlot, assignedIndex, nextDefenderSlot);
                            double surrogateDelta = aggregates.moveDelta(best, attackerSlot, previousDefenderSlot, nextDefenderSlot, candidates);
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                            if (exactDelta > 1e-9) {
                                best.applyMove(attackerSlot, assignedIndex, nextDefenderSlot);
                                aggregates.applyMove(previousDefenderSlot, nextDefenderSlot);
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
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    int cap = best.attackerCap(attackerSlot);
                    int used = best.assignedCount(attackerSlot);
                    if (used >= cap) continue;

                    int[] candidateDefenderSlots = candidateDefenderSlotsByAttacker[attackerSlot];
                    for (int defenderSlot : candidateDefenderSlots) {
                        int dUsedCount = best.defenderAssignedCount(defenderSlot);
                        int dCap = best.defenderCap(defenderSlot);
                        if (dUsedCount >= dCap) continue;

                        // Check not already assigned
                        if (best.containsDefenderSlot(attackerSlot, defenderSlot)) continue;
                        if (excludeReciprocalPairs && wouldCreateReciprocalPair(
                                best,
                                best.attackerNationIdAt(attackerSlot),
                                best.defenderNationIdAt(defenderSlot),
                                -1,
                                -1)) {
                            continue;
                        }

                        PlannerAssignmentChange candidate = best.addChange(attackerSlot, defenderSlot);
                        double surrogateDelta = aggregates.addDelta(best, attackerSlot, defenderSlot, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                        if (exactDelta > 1e-9) {
                            best.applyAdd(attackerSlot, defenderSlot);
                            aggregates.applyAdd(attackerSlot, defenderSlot);
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
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    if (!best.hasAssignments(attackerSlot)) {
                        continue;
                    }
                    for (int assignedIndex = 0; assignedIndex < best.assignedCount(attackerSlot); assignedIndex++) {
                        if (best.isLocked(attackerSlot, assignedIndex)) {
                            continue;
                        }
                        int removedDefenderSlot = best.defenderSlotAt(attackerSlot, assignedIndex);
                        PlannerAssignmentChange candidate = best.dropChange(attackerSlot, assignedIndex);
                        double surrogateDelta = aggregates.dropDelta(best, attackerSlot, removedDefenderSlot, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(best, candidate, attList, defList, attackerTeamId);
                        if (exactDelta > 1e-9) {
                            best.applyDrop(attackerSlot, assignedIndex);
                            aggregates.applyDrop(attackerSlot, removedDefenderSlot);
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
        return best.toAssignmentMap();
    }

    private double exactBundleDelta(
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            int attackerTeamId
    ) {
        return PlannerConflictExecutor.scoreAssignmentDelta(
            tuning,
            overrides,
            objective,
            currentAssignment,
            candidateChange,
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

    private static List<DBNationSnapshot> combinedSnapshots(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
        List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        return combined;
    }

    private static boolean hasOverlappingNationIds(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
        if (attackers.isEmpty() || defenders.isEmpty()) {
            return false;
        }
        Map<Integer, Boolean> attackerNationIds = new HashMap<>(Math.max(16, attackers.size() * 2));
        for (DBNationSnapshot attacker : attackers) {
            attackerNationIds.put(attacker.nationId(), Boolean.TRUE);
        }
        for (DBNationSnapshot defender : defenders) {
            if (attackerNationIds.containsKey(defender.nationId())) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, List<Integer>> normalizeReciprocalPairs(
            Map<Integer, List<Integer>> assignment,
            GeneratedCandidates candidates,
            List<BlitzFixedEdge> fixedEdges
    ) {
        if (assignment.isEmpty()) {
            return assignment;
        }
        Map<Integer, List<Integer>> normalized = copyAssignment(assignment);
        java.util.Set<Long> fixedPairKeys = fixedPairKeys(fixedEdges);
        java.util.Set<Long> seenPairs = new java.util.HashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerNationId = entry.getKey();
            for (int defenderNationId : entry.getValue()) {
                long unorderedKey = unorderedPairKey(attackerNationId, defenderNationId);
                if (!seenPairs.add(unorderedKey)) {
                    continue;
                }
                if (!containsAssignmentPair(normalized, defenderNationId, attackerNationId)) {
                    continue;
                }

                boolean keepForward = chooseReciprocalDirection(attackerNationId, defenderNationId, candidates, fixedPairKeys);
                if (keepForward) {
                    removeAssignmentPair(normalized, defenderNationId, attackerNationId);
                } else {
                    removeAssignmentPair(normalized, attackerNationId, defenderNationId);
                }
            }
        }
        return normalized;
    }

    private static boolean chooseReciprocalDirection(
            int attackerNationId,
            int defenderNationId,
            GeneratedCandidates candidates,
            java.util.Set<Long> fixedPairKeys
    ) {
        boolean forwardFixed = fixedPairKeys.contains(pairKey(attackerNationId, defenderNationId));
        boolean reverseFixed = fixedPairKeys.contains(pairKey(defenderNationId, attackerNationId));
        if (forwardFixed != reverseFixed) {
            return forwardFixed;
        }
        float forwardScore = candidates.edgeScore(attackerNationId, defenderNationId);
        float reverseScore = candidates.edgeScore(defenderNationId, attackerNationId);
        if (forwardScore != reverseScore) {
            return forwardScore > reverseScore;
        }
        return attackerNationId <= defenderNationId;
    }

    private static Map<Integer, List<Integer>> copyAssignment(Map<Integer, List<Integer>> assignment) {
        Map<Integer, List<Integer>> copy = new LinkedHashMap<>(assignment.size());
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static boolean containsAssignmentPair(Map<Integer, List<Integer>> assignment, int attackerNationId, int defenderNationId) {
        List<Integer> defenders = assignment.get(attackerNationId);
        return defenders != null && defenders.contains(defenderNationId);
    }

    private static void removeAssignmentPair(Map<Integer, List<Integer>> assignment, int attackerNationId, int defenderNationId) {
        List<Integer> defenders = assignment.get(attackerNationId);
        if (defenders == null) {
            return;
        }
        defenders.removeIf(defenderId -> defenderId == defenderNationId);
        if (defenders.isEmpty()) {
            assignment.remove(attackerNationId);
        }
    }

    private static boolean wouldCreateReciprocalPair(
            PlannerAssignmentSession assignment,
            int attackerNationId,
            int defenderNationId,
            int excludedAttackerSlot,
            int excludedAssignmentIndex
    ) {
        int reverseAttackerSlot = assignment.attackerSlot(defenderNationId);
        int reverseDefenderSlot = assignment.defenderSlot(attackerNationId);
        if (reverseAttackerSlot < 0 || reverseDefenderSlot < 0) {
            return false;
        }
        int excludedIndex = reverseAttackerSlot == excludedAttackerSlot ? excludedAssignmentIndex : -1;
        return assignment.containsDefenderSlotExcept(reverseAttackerSlot, reverseDefenderSlot, excludedIndex);
    }

    private static java.util.Set<Long> fixedPairKeys(List<BlitzFixedEdge> fixedEdges) {
        java.util.Set<Long> keys = new java.util.HashSet<>(Math.max(16, fixedEdges.size() * 2));
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            keys.add(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
        }
        return keys;
    }

    private static long unorderedPairKey(int nationIdOne, int nationIdTwo) {
        return pairKey(Math.min(nationIdOne, nationIdTwo), Math.max(nationIdOne, nationIdTwo));
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }

    private static int[][] candidateDefenderSlotsByAttacker(GeneratedCandidates candidates, PlannerAssignmentSession assignment) {
        int[][] candidateDefenderSlots = new int[assignment.attackerCount()][];
        for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
            List<Integer> defenderIds = candidates.candidateDefendersByAttacker().getOrDefault(
                    assignment.attackerNationIdAt(attackerSlot),
                    List.of()
            );
            int[] defenderSlots = new int[defenderIds.size()];
            int size = 0;
            for (int defenderId : defenderIds) {
                int defenderSlot = assignment.defenderSlot(defenderId);
                if (defenderSlot >= 0) {
                    defenderSlots[size++] = defenderSlot;
                }
            }
            candidateDefenderSlots[attackerSlot] = size == defenderSlots.length
                    ? defenderSlots
                    : Arrays.copyOf(defenderSlots, size);
        }
        return candidateDefenderSlots;
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

    private static final class RefinementAggregates {
        private static final double DEFENDER_PRESSURE_WEIGHT = 0.06;
        private static final double ATTACKER_COMMITMENT_WEIGHT = 0.04;
        private static final double SURROGATE_EVAL_FLOOR = -0.05;

        private final int[] attackerAssignedCount;
        private final int[] defenderAssignedCount;
        private final double[] attackerCommitmentLoad;
        private final double[] defenderPressure;
        private final int[] attackerCaps;
        private final int[] defenderCaps;

        private RefinementAggregates(
            int[] attackerAssignedCount,
            int[] defenderAssignedCount,
            double[] attackerCommitmentLoad,
            double[] defenderPressure,
            int[] attackerCaps,
            int[] defenderCaps
        ) {
            this.attackerAssignedCount = attackerAssignedCount;
            this.defenderAssignedCount = defenderAssignedCount;
            this.attackerCommitmentLoad = attackerCommitmentLoad;
            this.defenderPressure = defenderPressure;
            this.attackerCaps = attackerCaps;
            this.defenderCaps = defenderCaps;
        }

        static RefinementAggregates fromAssignment(
            PlannerAssignmentSession assignment
        ) {
            int[] attackerAssignedCount = new int[assignment.attackerCount()];
            int[] defenderAssignedCount = new int[assignment.defenderCount()];
            double[] attackerCommitmentLoad = new double[assignment.attackerCount()];
            double[] defenderPressure = new double[assignment.defenderCount()];
            int[] attackerCaps = new int[assignment.attackerCount()];
            int[] defenderCaps = new int[assignment.defenderCount()];

            for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
                attackerAssignedCount[attackerSlot] = assignment.assignedCount(attackerSlot);
                attackerCaps[attackerSlot] = Math.max(1, assignment.attackerCap(attackerSlot));
                attackerCommitmentLoad[attackerSlot] = (double) attackerAssignedCount[attackerSlot] / attackerCaps[attackerSlot];
            }
            for (int defenderSlot = 0; defenderSlot < assignment.defenderCount(); defenderSlot++) {
                defenderAssignedCount[defenderSlot] = assignment.defenderAssignedCount(defenderSlot);
                defenderCaps[defenderSlot] = Math.max(1, assignment.defenderCap(defenderSlot));
                defenderPressure[defenderSlot] = (double) defenderAssignedCount[defenderSlot] / defenderCaps[defenderSlot];
            }

            return new RefinementAggregates(
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

        double addDelta(PlannerAssignmentSession assignment, int attackerSlot, int defenderSlot, GeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(
                    assignment.attackerNationIdAt(attackerSlot),
                    assignment.defenderNationIdAt(defenderSlot)
            );
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return edgeScore
                + attackerPenaltyDelta(attackerSlot, +1)
                + defenderPenaltyDelta(defenderSlot, +1);
        }

        double dropDelta(PlannerAssignmentSession assignment, int attackerSlot, int defenderSlot, GeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(
                    assignment.attackerNationIdAt(attackerSlot),
                    assignment.defenderNationIdAt(defenderSlot)
            );
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return -edgeScore
                + attackerPenaltyDelta(attackerSlot, -1)
                + defenderPenaltyDelta(defenderSlot, -1);
        }

        double moveDelta(PlannerAssignmentSession assignment, int attackerSlot, int oldDefenderSlot, int newDefenderSlot, GeneratedCandidates candidates) {
            int attackerNationId = assignment.attackerNationIdAt(attackerSlot);
            float dropScore = candidates.edgeScore(attackerNationId, assignment.defenderNationIdAt(oldDefenderSlot));
            float addScore = candidates.edgeScore(attackerNationId, assignment.defenderNationIdAt(newDefenderSlot));
            if (!Float.isFinite(dropScore) || !Float.isFinite(addScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (addScore - dropScore)
                + defenderPenaltyDelta(oldDefenderSlot, -1)
                + defenderPenaltyDelta(newDefenderSlot, +1);
        }

        double swapDelta(
            PlannerAssignmentSession assignment,
            int attackerOneSlot,
            int oldDefenderOneSlot,
            int newDefenderOneSlot,
            int attackerTwoSlot,
            int oldDefenderTwoSlot,
            int newDefenderTwoSlot,
            GeneratedCandidates candidates
        ) {
            int attackerOneNationId = assignment.attackerNationIdAt(attackerOneSlot);
            int attackerTwoNationId = assignment.attackerNationIdAt(attackerTwoSlot);
            float oldOne = candidates.edgeScore(attackerOneNationId, assignment.defenderNationIdAt(oldDefenderOneSlot));
            float oldTwo = candidates.edgeScore(attackerTwoNationId, assignment.defenderNationIdAt(oldDefenderTwoSlot));
            float nextOne = candidates.edgeScore(attackerOneNationId, assignment.defenderNationIdAt(newDefenderOneSlot));
            float nextTwo = candidates.edgeScore(attackerTwoNationId, assignment.defenderNationIdAt(newDefenderTwoSlot));
            if (!Float.isFinite(oldOne) || !Float.isFinite(oldTwo) || !Float.isFinite(nextOne) || !Float.isFinite(nextTwo)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (nextOne + nextTwo) - (oldOne + oldTwo);
        }

        void applyAdd(int attackerSlot, int defenderSlot) {
            applyAttacker(attackerSlot, +1);
            applyDefender(defenderSlot, +1);
        }

        void applyDrop(int attackerSlot, int defenderSlot) {
            applyAttacker(attackerSlot, -1);
            applyDefender(defenderSlot, -1);
        }

        void applyMove(int oldDefenderSlot, int newDefenderSlot) {
            applyDefender(oldDefenderSlot, -1);
            applyDefender(newDefenderSlot, +1);
        }

        void applySwap() {
            // Swap preserves attacker loads and defender counts when defenders differ.
        }

        private double attackerPenaltyDelta(int attackerSlot, int delta) {
            double before = attackerCommitmentLoad[attackerSlot];
            double after = (double) (attackerAssignedCount[attackerSlot] + delta) / attackerCaps[attackerSlot];
            return -ATTACKER_COMMITMENT_WEIGHT * ((after * after) - (before * before));
        }

        private double defenderPenaltyDelta(int defenderSlot, int delta) {
            double before = defenderPressure[defenderSlot];
            double after = (double) (defenderAssignedCount[defenderSlot] + delta) / defenderCaps[defenderSlot];
            return -DEFENDER_PRESSURE_WEIGHT * ((after * after) - (before * before));
        }

        private void applyAttacker(int attackerSlot, int delta) {
            attackerAssignedCount[attackerSlot] += delta;
            attackerCommitmentLoad[attackerSlot] = (double) attackerAssignedCount[attackerSlot] / attackerCaps[attackerSlot];
        }

        private void applyDefender(int defenderSlot, int delta) {
            defenderAssignedCount[defenderSlot] += delta;
            defenderPressure[defenderSlot] = (double) defenderAssignedCount[defenderSlot] / defenderCaps[defenderSlot];
        }
    }
}

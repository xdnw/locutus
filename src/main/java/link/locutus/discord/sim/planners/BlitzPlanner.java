package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
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
 *       effort-tiering with top {@link SimTuning#candidatesPerAttacker()} retention per attacker,
 *       plus a bounded defender-coverage rescue for viable edges pruned by attacker-only top-K.</li>
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
    private final StrategicObjective objective;
    private final SnapshotActivityProvider snapshotActivityProvider;

    public BlitzPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, StrategicObjective objective) {
        this(tuning, treatyProvider, overrides, objective, SnapshotActivityProvider.BASELINE);
    }

    public BlitzPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, StrategicObjective objective, SnapshotActivityProvider activityProvider) {
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
        return assign(attackers, defenders, currentTurn, fixedEdges, 1);
    }

    public BlitzAssignment assign(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int currentTurn,
            Collection<BlitzFixedEdge> fixedEdges,
            int horizonTurns) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.BLITZ_ASSIGN)) {
            AssignmentInputs inputs = normalizeAssignmentInputs(attackers, defenders, currentTurn, fixedEdges, horizonTurns);
            PreparedAssignment prepared = prepareAssignment(inputs);
            if (!prepared.hasCandidates()) {
                return new BlitzAssignment(Map.of(), prepared.diagnostics(), 0.0);
            }

            PlannedAssignment planned = planPreparedAssignment(prepared);
            PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "assignmentPairs", assignmentPairCount(planned.assignment()));
            Map<Long, Integer> assignmentWarTypeOrdinalsByPair = assignmentWarTypeOrdinals(
                    prepared.candidates(),
                    prepared.inputs().fixedEdges()
            );
            return new BlitzAssignment(
                planned.assignment(),
                prepared.diagnostics(),
                planned.objectiveSummary().mean(),
                planned.objectiveSummary(),
                assignmentWarTypeOrdinalsByPair,
                prepared.candidates().initialAttackTypeOrdinalsByPair()
            );
        }
    }

    private AssignmentInputs normalizeAssignmentInputs(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int currentTurn,
            Collection<BlitzFixedEdge> fixedEdges,
            int horizonTurns
    ) {
        Objects.requireNonNull(attackers, "attackers");
        Objects.requireNonNull(defenders, "defenders");
        Objects.requireNonNull(fixedEdges, "fixedEdges");

        List<DBNationSnapshot> attList = List.copyOf(attackers);
        List<DBNationSnapshot> defList = List.copyOf(defenders);
        List<BlitzFixedEdge> fixedEdgeList = List.copyOf(fixedEdges);
        int planningHorizonTurns = Math.max(1, horizonTurns);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "attackers", attList.size());
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "defenders", defList.size());
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "fixedEdges", fixedEdgeList.size());
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "horizonTurns", planningHorizonTurns);
        return new AssignmentInputs(attList, defList, fixedEdgeList, currentTurn, planningHorizonTurns);
    }

    private PreparedAssignment prepareAssignment(AssignmentInputs inputs) {
        Map<Integer, Float> activityWeights = PlannerSimSupport.compileActivityWeights(
            snapshotActivityProvider.withWartimeUplift(tuning.wartimeActivityUplift()),
            inputs.currentTurn(),
            overrides,
            combinedSnapshots(inputs.attackers(), inputs.defenders())
        );

        CompiledScenario compiledScenario = SCENARIO_COMPILER.compile(
            inputs.attackers(),
            inputs.defenders(),
            overrides,
            treatyProvider,
            activityWeights
        );

        int[] attCaps = computeAttackerCaps(compiledScenario);
        int[] defCaps = computeDefenderCaps(compiledScenario);
        int[] attStrengthRank = computeStrengthRanks(compiledScenario);
        int[] attackerNationIds = attackerNationIds(compiledScenario);
        int[] defenderNationIds = defenderNationIds(compiledScenario);
        BlitzGeneratedCandidates candidates = generateCandidates(compiledScenario, attCaps, defCaps);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "candidateEdges", candidates.edgeTable().edgeCount());

        List<PlannerDiagnostic> diagnostics = new ArrayList<>();
        collectDiagnostics(inputs.attackers(), inputs.defenders(), diagnostics);

        return new PreparedAssignment(
            inputs,
            compiledScenario,
            attCaps,
            defCaps,
            attStrengthRank,
            attackerNationIds,
            defenderNationIds,
            capsByNationId(compiledScenario, attCaps, true),
            capsByNationId(compiledScenario, defCaps, false),
            activityWeights,
            candidates,
            diagnostics,
            hasOverlappingNationIds(inputs.attackers(), inputs.defenders()),
            LongHorizonAssignmentOptimizer.shouldOptimize(inputs.planningHorizonTurns())
        );
    }

    private PlannedAssignment planPreparedAssignment(PreparedAssignment prepared) {
        if (prepared.useLongHorizonOptimization()) {
            return planLongHorizonAssignment(prepared);
        }
        Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
            prepared.candidates().edgeTable(),
            prepared.compiledScenario().attackerCount(),
            prepared.compiledScenario().defenderCount(),
            prepared.attCaps(),
            prepared.defCaps(),
            prepared.attStrengthRank(),
            prepared.attackerNationIds(),
            prepared.defenderNationIds(),
            prepared.inputs().fixedEdges()
        );
        if (prepared.excludeReciprocalPairs()) {
            assignment = normalizeReciprocalPairs(
                assignment,
                prepared.candidates(),
                prepared.inputs().fixedEdges(),
                prepared.attCapsByNationId(),
                prepared.defCapsByNationId(),
                prepared.activityWeightsByNationId(),
                overrides
            );
        }
        Map<Long, Integer> assignmentWarTypeOrdinalsByPair = assignmentWarTypeOrdinals(
                prepared.candidates(),
                prepared.inputs().fixedEdges()
        );
        assignment = BlitzAssignmentRefiner.refine(
            tuning,
            overrides,
            objective,
            assignment,
            prepared.candidates(),
            assignmentWarTypeOrdinalsByPair,
            prepared.attCapsByNationId(),
            prepared.defCapsByNationId(),
            prepared.inputs().attackers(),
            prepared.inputs().defenders(),
            prepared.inputs().fixedEdges(),
            prepared.excludeReciprocalPairs()
        );
        if (prepared.excludeReciprocalPairs()) {
            assignment = normalizeReciprocalPairs(
                assignment,
                prepared.candidates(),
                prepared.inputs().fixedEdges(),
                prepared.attCapsByNationId(),
                prepared.defCapsByNationId(),
                prepared.activityWeightsByNationId(),
                overrides
            );
        }
        return new PlannedAssignment(
                assignment,
                summarizeExactAssignment(prepared, assignment, assignmentWarTypeOrdinalsByPair)
        );
    }

    private PlannedAssignment planLongHorizonAssignment(PreparedAssignment prepared) {
        LongHorizonAssignmentOptimizer.Result optimized = LongHorizonAssignmentOptimizer.solveDetailed(
            prepared.candidates().edgeTable(),
            prepared.compiledScenario(),
            prepared.attCaps(),
            prepared.defCaps(),
            prepared.attStrengthRank(),
            prepared.attackerNationIds(),
            prepared.defenderNationIds(),
            prepared.inputs().fixedEdges(),
            prepared.inputs().planningHorizonTurns(),
            new LongHorizonAssignmentOptimizer.ProjectionScoringContext(objective)
        );
        Map<Integer, List<Integer>> assignment = optimized.assignment();
        boolean normalizedReciprocals = false;
        if (prepared.excludeReciprocalPairs()) {
            assignment = normalizeReciprocalPairs(
                assignment,
                prepared.candidates(),
                prepared.inputs().fixedEdges(),
                prepared.attCapsByNationId(),
                prepared.defCapsByNationId(),
                prepared.activityWeightsByNationId(),
                overrides
            );
            normalizedReciprocals = true;
        }
        ObjectiveValueSummary objectiveSummary = !normalizedReciprocals
                ? optimized.projectedObjectiveSummary()
                : null;
        if (objectiveSummary == null) {
            objectiveSummary = LongHorizonAssignmentOptimizer.projectedObjectiveSummary(
                prepared.candidates().edgeTable(),
                prepared.compiledScenario(),
                prepared.attCaps(),
                prepared.defCaps(),
                prepared.inputs().planningHorizonTurns(),
                assignment,
                objective,
                prepared.attackerNationIds(),
                prepared.defenderNationIds()
            );
        }
        return new PlannedAssignment(assignment, objectiveSummary);
    }

    private ObjectiveValueSummary summarizeExactAssignment(
            PreparedAssignment prepared,
            Map<Integer, List<Integer>> assignment,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        return PlannerSimSupport.summarizeObjectiveValues(tuning, sampleIndex -> {
            SimTuning scoringTuning = tuning.stateResolutionMode() == link.locutus.discord.sim.combat.ResolutionMode.STOCHASTIC
                ? PlannerSimSupport.sampleTuning(tuning, sampleIndex)
                : tuning;
            return PlannerSimSupport.scoreAssignment(
                scoringTuning,
                overrides,
                objective,
                assignment,
                prepared.inputs().attackers(),
                prepared.inputs().defenders(),
                warTypeOrdinalsByPair
            );
        });
    }

    // ============================================================
    // Candidate generation
    // ============================================================

    private BlitzGeneratedCandidates generateCandidates(
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
        Map<Long, Integer> initialWarTypeOrdinalsByPair = new HashMap<>(Math.max(16, edges.edgeCount() * 2));
        Map<Long, Integer> initialAttackTypeOrdinalsByPair = new HashMap<>(Math.max(16, edges.edgeCount() * 2));

        for (int edge = 0; edge < edges.edgeCount(); edge++) {
            int attackerIndex = edges.attackerIndex(edge);
            int defenderIndex = edges.defenderIndex(edge);
            int attackerNationId = compiledScenario.attackerNationId(attackerIndex);
            int defenderNationId = compiledScenario.defenderNationId(defenderIndex);

            long candidatePairKey = pairKey(attackerNationId, defenderNationId);

            candidateDefendersByAttacker
                .computeIfAbsent(attackerNationId, ignored -> new ArrayList<>())
                .add(defenderNationId);
            edgeScoresByPair.put(candidatePairKey, edges.scalarScore(edge));
            initialWarTypeOrdinalsByPair.put(candidatePairKey, validWarTypeOrdinal(edges.preferredWarTypeId(edge)));
            initialAttackTypeOrdinalsByPair.put(candidatePairKey, (int) edges.bestAttackTypeId(edge));
        }

        return new BlitzGeneratedCandidates(edges, candidateDefendersByAttacker, edgeScoresByPair, initialWarTypeOrdinalsByPair, initialAttackTypeOrdinalsByPair);
    }

    private static int validWarTypeOrdinal(byte warTypeOrdinal) {
        return warTypeOrdinal >= 0 && warTypeOrdinal < link.locutus.discord.apiv1.enums.WarType.values.length
                ? warTypeOrdinal
                : link.locutus.discord.apiv1.enums.WarType.ORD.ordinal();
    }

    private static Map<Long, Integer> assignmentWarTypeOrdinals(
            BlitzGeneratedCandidates candidates,
            List<BlitzFixedEdge> fixedEdges
    ) {
        if (fixedEdges.isEmpty()) {
            return candidates.initialWarTypeOrdinalsByPair();
        }
        Map<Long, Integer> merged = new HashMap<>(candidates.initialWarTypeOrdinalsByPair());
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            merged.put(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()), fixedEdge.warTypeOrdinal());
        }
        return Map.copyOf(merged);
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
            BlitzGeneratedCandidates candidates,
            List<BlitzFixedEdge> fixedEdges,
            Map<Integer, Integer> attackerCapsByNationId,
            Map<Integer, Integer> defenderCapsByNationId,
                Map<Integer, Float> activityWeightsByNationId,
                OverrideSet overrides
    ) {
        if (assignment.isEmpty()) {
            return assignment;
        }
        Map<Integer, List<Integer>> normalized = copyAssignment(assignment);
        java.util.Set<Long> fixedPairKeys = fixedPairKeys(fixedEdges);
        java.util.Set<Long> seenPairs = new java.util.HashSet<>();
        Map<Integer, Integer> attackerAssignedCounts = attackerAssignedCounts(normalized);
        Map<Integer, Integer> defenderAssignedCounts = defenderAssignedCounts(normalized);
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerNationId = entry.getKey();
            for (int defenderNationId : entry.getValue()) {
                long unorderedKey = unorderedPairKey(attackerNationId, defenderNationId);
                if (!seenPairs.add(unorderedKey)) {
                    continue;
                }
                boolean forwardAssigned = containsAssignmentPair(normalized, attackerNationId, defenderNationId);
                boolean reverseAssigned = containsAssignmentPair(normalized, defenderNationId, attackerNationId);
                if (!forwardAssigned && !reverseAssigned) {
                    continue;
                }

                boolean keepForward = chooseReciprocalDirection(
                    attackerNationId,
                    defenderNationId,
                    candidates,
                    fixedPairKeys,
                    activityWeightsByNationId,
                    overrides
                );
                if (forwardAssigned && reverseAssigned) {
                    if (keepForward) {
                        removeAssignmentPair(normalized, defenderNationId, attackerNationId);
                        decrementAssignmentCounts(attackerAssignedCounts, defenderNationId);
                        decrementAssignmentCounts(defenderAssignedCounts, attackerNationId);
                    } else {
                        removeAssignmentPair(normalized, attackerNationId, defenderNationId);
                        decrementAssignmentCounts(attackerAssignedCounts, attackerNationId);
                        decrementAssignmentCounts(defenderAssignedCounts, defenderNationId);
                    }
                    continue;
                }
                if (keepForward == forwardAssigned) {
                    continue;
                }
                if (keepForward) {
                    if (!canFlipReciprocalDirection(
                        attackerNationId,
                        defenderNationId,
                        attackerAssignedCounts,
                        defenderAssignedCounts,
                        attackerCapsByNationId,
                        defenderCapsByNationId
                    )) {
                        continue;
                    }
                    removeAssignmentPair(normalized, defenderNationId, attackerNationId);
                    decrementAssignmentCounts(attackerAssignedCounts, defenderNationId);
                    decrementAssignmentCounts(defenderAssignedCounts, attackerNationId);
                    addAssignmentPair(normalized, attackerNationId, defenderNationId);
                    incrementAssignmentCounts(attackerAssignedCounts, attackerNationId);
                    incrementAssignmentCounts(defenderAssignedCounts, defenderNationId);
                } else {
                    if (!canFlipReciprocalDirection(
                        defenderNationId,
                        attackerNationId,
                        attackerAssignedCounts,
                        defenderAssignedCounts,
                        attackerCapsByNationId,
                        defenderCapsByNationId
                    )) {
                        continue;
                    }
                    removeAssignmentPair(normalized, attackerNationId, defenderNationId);
                    decrementAssignmentCounts(attackerAssignedCounts, attackerNationId);
                    decrementAssignmentCounts(defenderAssignedCounts, defenderNationId);
                    addAssignmentPair(normalized, defenderNationId, attackerNationId);
                    incrementAssignmentCounts(attackerAssignedCounts, defenderNationId);
                    incrementAssignmentCounts(defenderAssignedCounts, attackerNationId);
                }
            }
        }
        return normalized;
    }

    private static Map<Integer, Integer> attackerAssignedCounts(Map<Integer, List<Integer>> assignment) {
        Map<Integer, Integer> counts = new HashMap<>(Math.max(16, assignment.size() * 2));
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private static Map<Integer, Integer> defenderAssignedCounts(Map<Integer, List<Integer>> assignment) {
        Map<Integer, Integer> counts = new HashMap<>(Math.max(16, assignment.size() * 2));
        for (List<Integer> defenders : assignment.values()) {
            for (int defenderNationId : defenders) {
                incrementAssignmentCounts(counts, defenderNationId);
            }
        }
        return counts;
    }

    private static boolean canFlipReciprocalDirection(
            int attackerNationId,
            int defenderNationId,
            Map<Integer, Integer> attackerAssignedCounts,
            Map<Integer, Integer> defenderAssignedCounts,
            Map<Integer, Integer> attackerCapsByNationId,
            Map<Integer, Integer> defenderCapsByNationId
    ) {
        int attackerCount = attackerAssignedCounts.getOrDefault(attackerNationId, 0);
        int defenderCount = defenderAssignedCounts.getOrDefault(defenderNationId, 0);
        int attackerCap = attackerCapsByNationId.getOrDefault(attackerNationId, 0);
        int defenderCap = defenderCapsByNationId.getOrDefault(defenderNationId, 0);
        return attackerCount < attackerCap && defenderCount < defenderCap;
    }

    private static void addAssignmentPair(Map<Integer, List<Integer>> assignment, int attackerNationId, int defenderNationId) {
        assignment.computeIfAbsent(attackerNationId, ignored -> new ArrayList<>()).add(defenderNationId);
    }

    private static void incrementAssignmentCounts(Map<Integer, Integer> counts, int nationId) {
        counts.merge(nationId, 1, Integer::sum);
    }

    private static void decrementAssignmentCounts(Map<Integer, Integer> counts, int nationId) {
        counts.compute(nationId, (ignored, current) -> {
            if (current == null || current <= 1) {
                return null;
            }
            return current - 1;
        });
    }

    private static boolean chooseReciprocalDirection(
            int attackerNationId,
            int defenderNationId,
            BlitzGeneratedCandidates candidates,
            java.util.Set<Long> fixedPairKeys,
            Map<Integer, Float> activityWeightsByNationId,
            OverrideSet overrides
    ) {
        boolean forwardFixed = fixedPairKeys.contains(pairKey(attackerNationId, defenderNationId));
        boolean reverseFixed = fixedPairKeys.contains(pairKey(defenderNationId, attackerNationId));
        if (forwardFixed != reverseFixed) {
            return forwardFixed;
        }
        int forwardOverrideRank = activeOverrideRank(overrides.activeOverride(attackerNationId));
        int reverseOverrideRank = activeOverrideRank(overrides.activeOverride(defenderNationId));
        if (forwardOverrideRank != reverseOverrideRank) {
            return forwardOverrideRank > reverseOverrideRank;
        }
        float forwardActivity = activityWeightsByNationId.getOrDefault(attackerNationId, 1.0f);
        float reverseActivity = activityWeightsByNationId.getOrDefault(defenderNationId, 1.0f);
        if (forwardActivity != reverseActivity) {
            return forwardActivity > reverseActivity;
        }
        float forwardScore = candidates.edgeScore(attackerNationId, defenderNationId);
        float reverseScore = candidates.edgeScore(defenderNationId, attackerNationId);
        if (forwardScore != reverseScore) {
            return forwardScore > reverseScore;
        }
        return attackerNationId <= defenderNationId;
    }

    private static int activeOverrideRank(OverrideSet.ActiveOverride activeOverride) {
        return switch (activeOverride) {
            case FALSE -> 0;
            case AUTO -> 1;
            case TRUE -> 2;
        };
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

    private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
        int pairCount = 0;
        for (List<Integer> defenders : assignment.values()) {
            pairCount += defenders.size();
        }
        return pairCount;
    }

    private void collectDiagnostics(
            List<DBNationSnapshot> attList,
            List<DBNationSnapshot> defList,
            List<PlannerDiagnostic> diagnostics
    ) {
        PlannerSimSupport.collectResetDiagnostics(attList, defList, diagnostics);
    }

    private record AssignmentInputs(
        List<DBNationSnapshot> attackers,
        List<DBNationSnapshot> defenders,
        List<BlitzFixedEdge> fixedEdges,
        int currentTurn,
        int planningHorizonTurns
    ) {
    }

    private record PreparedAssignment(
        AssignmentInputs inputs,
        CompiledScenario compiledScenario,
        int[] attCaps,
        int[] defCaps,
        int[] attStrengthRank,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, Integer> attCapsByNationId,
        Map<Integer, Integer> defCapsByNationId,
        Map<Integer, Float> activityWeightsByNationId,
        BlitzGeneratedCandidates candidates,
        List<PlannerDiagnostic> diagnostics,
        boolean excludeReciprocalPairs,
        boolean useLongHorizonOptimization
    ) {
        boolean hasCandidates() {
            return candidates.edgeTable().edgeCount() > 0;
        }
    }

    private record PlannedAssignment(
        Map<Integer, List<Integer>> assignment,
        ObjectiveValueSummary objectiveSummary
    ) {
    }
}

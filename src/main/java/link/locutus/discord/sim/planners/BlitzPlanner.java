package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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

    public StrategicObjective objective() {
        return objective;
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
            Collection<DBNationSnapshot> defenders,
            SidePolicy actingPolicy,
            SidePolicy opposingPolicy,
            int currentTurn,
            Collection<BlitzFixedEdge> fixedEdges,
            int horizonTurns) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.BLITZ_ASSIGN)) {
            Objects.requireNonNull(actingPolicy, "actingPolicy");
            Objects.requireNonNull(opposingPolicy, "opposingPolicy");
            return assignForPolicy(attackers, defenders, actingPolicy, opposingPolicy, currentTurn, fixedEdges, horizonTurns);
        }
    }

    public BlitzAssignmentPair assignSymmetric(
            Collection<DBNationSnapshot> sideA,
            Collection<DBNationSnapshot> sideB,
            SidePolicy sideAPolicy,
            SidePolicy sideBPolicy,
            int currentTurn,
            Collection<BlitzFixedEdge> sideAFixedEdges,
            Collection<BlitzFixedEdge> sideBFixedEdges,
            int horizonTurns
        ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.BLITZ_ASSIGN)) {
            Objects.requireNonNull(sideAPolicy, "sideAPolicy");
            Objects.requireNonNull(sideBPolicy, "sideBPolicy");

            BlitzAssignment sideAAssignment = assignForPolicy(sideA, sideB, sideAPolicy, sideBPolicy, currentTurn, sideAFixedEdges, horizonTurns);
            BlitzAssignment sideBAssignment = assignForPolicy(sideB, sideA, sideBPolicy, sideAPolicy, currentTurn, sideBFixedEdges, horizonTurns);
            Map<String, List<PlannerDiagnostic>> diagnosticsByPass = new LinkedHashMap<>();
            diagnosticsByPass.put(BlitzAssignmentPair.SIDE_A_PASS, sideAAssignment.diagnostics());
            diagnosticsByPass.put(BlitzAssignmentPair.SIDE_B_PASS, sideBAssignment.diagnostics());
            return new BlitzAssignmentPair(sideAAssignment, sideBAssignment, diagnosticsByPass);
        }
    }

    private BlitzAssignment assignForPolicy(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            SidePolicy sidePolicy,
            SidePolicy opposingPolicy,
            int currentTurn,
            Collection<BlitzFixedEdge> fixedEdges,
            int horizonTurns
        ) {
        AssignmentInputs inputs = normalizeAssignmentInputs(attackers, defenders, currentTurn, fixedEdges, horizonTurns);
            if (!sidePolicy.allowInitialDeclarations()) {
                List<PlannerDiagnostic> diagnostics = new ObjectArrayList<>();
                collectDiagnostics(inputs.attackers(), inputs.defenders(), diagnostics);
                return new BlitzAssignment(Map.of(), diagnostics, 0.0);
            }
        PreparedAssignment prepared = prepareAssignment(inputs, sidePolicy, opposingPolicy);
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

    private PreparedAssignment prepareAssignment(AssignmentInputs inputs, SidePolicy actingPolicy, SidePolicy defendingPolicy) {
        SimTuning effectiveTuning = tuningFor(actingPolicy);
        StrategicObjective effectiveObjective = actingPolicy.objective();
        Map<Integer, Float> activityWeights = PlannerSimSupport.compileActivityWeights(
            snapshotActivityProvider.withWartimeUplift(effectiveTuning.wartimeActivityUplift()),
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
        BlitzGeneratedCandidates candidates = generateCandidates(compiledScenario, attCaps, defCaps, effectiveTuning, effectiveObjective);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.BLITZ_ASSIGN, "candidateEdges", candidates.edgeTable().edgeCount());

        List<PlannerDiagnostic> diagnostics = new ObjectArrayList<>();
        collectDiagnostics(inputs.attackers(), inputs.defenders(), diagnostics);

        return new PreparedAssignment(
            inputs,
            actingPolicy,
            defendingPolicy,
            effectiveTuning,
            effectiveObjective,
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
            prepared.effectiveTuning(),
            overrides,
            prepared.actingPolicy(),
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
        LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext =
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.fromSidePolicies(
                        prepared.effectiveObjective(),
                        prepared.actingPolicy(),
                        prepared.defendingPolicy()
                );
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
            projectionContext
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
                projectionContext,
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
        return PlannerSimSupport.summarizeObjectiveValues(prepared.effectiveTuning(), sampleIndex -> {
            SimTuning scoringTuning = prepared.effectiveTuning().stateResolutionMode() == link.locutus.discord.sim.combat.ResolutionMode.STOCHASTIC
                ? PlannerSimSupport.sampleTuning(prepared.effectiveTuning(), sampleIndex)
                : prepared.effectiveTuning();
            return PlannerSimSupport.scoreAssignment(
                scoringTuning,
                overrides,
                prepared.effectiveObjective(),
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
            int[] defCaps,
            SimTuning effectiveTuning,
            StrategicObjective effectiveObjective) {
        CandidateEdgeTable edges = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
            compiledScenario,
            effectiveTuning,
            overrides,
            effectiveObjective,
            attCaps,
            defCaps,
            edges
        );

        for (int edge = 0; edge < edges.edgeCount(); edge++) {
            float attackerActivityWeight = compiledScenario.attackerActivityWeight(edges.attackerIndex(edge));
            edges.scaleScalarScore(edge, attackerActivityWeight);
        }

        Map<Integer, List<Integer>> candidateDefendersByAttacker = new Int2ObjectOpenHashMap<>();
        Map<Long, Float> edgeScoresByPair = new Long2FloatOpenHashMap(Math.max(16, edges.edgeCount() * 2));
        Map<Long, Integer> initialWarTypeOrdinalsByPair = new Long2IntOpenHashMap(Math.max(16, edges.edgeCount() * 2));
        Map<Long, Integer> initialAttackTypeOrdinalsByPair = new Long2IntOpenHashMap(Math.max(16, edges.edgeCount() * 2));

        for (int edge = 0; edge < edges.edgeCount(); edge++) {
            int attackerIndex = edges.attackerIndex(edge);
            int defenderIndex = edges.defenderIndex(edge);
            int attackerNationId = compiledScenario.attackerNationId(attackerIndex);
            int defenderNationId = compiledScenario.defenderNationId(defenderIndex);

            long candidatePairKey = pairKey(attackerNationId, defenderNationId);

            candidateDefendersByAttacker
                .computeIfAbsent(attackerNationId, ignored -> new IntArrayList())
                .add(defenderNationId);
            edgeScoresByPair.put(candidatePairKey, edges.scalarScore(edge));
            initialWarTypeOrdinalsByPair.put(candidatePairKey, validWarTypeOrdinal(edges.preferredWarTypeId(edge)));
            initialAttackTypeOrdinalsByPair.put(candidatePairKey, (int) edges.bestAttackTypeId(edge));
        }

        for (Map.Entry<Integer, List<Integer>> entry : candidateDefendersByAttacker.entrySet()) {
            int attackerNationId = entry.getKey();
            entry.getValue().sort(Comparator
                    .comparingDouble((Integer defenderNationId) -> edgeScoresByPair.getOrDefault(
                            pairKey(attackerNationId, defenderNationId),
                            Float.NEGATIVE_INFINITY
                    ))
                    .reversed()
                    .thenComparingInt(Integer::intValue));
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
        Long2IntOpenHashMap merged = new Long2IntOpenHashMap(candidates.initialWarTypeOrdinalsByPair());
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            merged.put(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()), fixedEdge.warTypeOrdinal());
        }
        return Long2IntMaps.unmodifiable(merged);
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
        IntArrayList attackerIndexes = new IntArrayList(compiledScenario.attackerCount());
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
        Map<Integer, Integer> caps = new Int2IntOpenHashMap(capsByIndex.length);
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
        List<DBNationSnapshot> combined = new ObjectArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        return combined;
    }

    private static boolean hasOverlappingNationIds(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
        if (attackers.isEmpty() || defenders.isEmpty()) {
            return false;
        }
        IntOpenHashSet attackerNationIds = new IntOpenHashSet(Math.max(16, attackers.size() * 2));
        for (DBNationSnapshot attacker : attackers) {
            attackerNationIds.add(attacker.nationId());
        }
        for (DBNationSnapshot defender : defenders) {
            if (attackerNationIds.contains(defender.nationId())) {
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
        LongOpenHashSet fixedPairKeys = fixedPairKeys(fixedEdges);
        LongOpenHashSet seenPairs = new LongOpenHashSet();
        Int2IntOpenHashMap attackerAssignedCounts = attackerAssignedCounts(normalized);
        Int2IntOpenHashMap defenderAssignedCounts = defenderAssignedCounts(normalized);
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

    private static Int2IntOpenHashMap attackerAssignedCounts(Map<Integer, List<Integer>> assignment) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap(Math.max(16, assignment.size() * 2));
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerNationId = entry.getKey();
            counts.put(attackerNationId, entry.getValue().size());
        }
        return counts;
    }

    private static Int2IntOpenHashMap defenderAssignedCounts(Map<Integer, List<Integer>> assignment) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap(Math.max(16, assignment.size() * 2));
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
            Int2IntOpenHashMap attackerAssignedCounts,
            Int2IntOpenHashMap defenderAssignedCounts,
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
        assignment.computeIfAbsent(attackerNationId, ignored -> new IntArrayList()).add(defenderNationId);
    }

    private static void incrementAssignmentCounts(Int2IntOpenHashMap counts, int nationId) {
        counts.put(nationId, counts.get(nationId) + 1);
    }

    private static void decrementAssignmentCounts(Int2IntOpenHashMap counts, int nationId) {
        int current = counts.get(nationId);
        if (current <= 1) {
            counts.remove(nationId);
            return;
        }
        counts.put(nationId, current - 1);
    }

    private static boolean chooseReciprocalDirection(
            int attackerNationId,
            int defenderNationId,
            BlitzGeneratedCandidates candidates,
            LongOpenHashSet fixedPairKeys,
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
        Map<Integer, List<Integer>> copy = new Int2ObjectLinkedOpenHashMap<>(assignment.size());
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            copy.put(entry.getKey(), new IntArrayList(entry.getValue()));
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

    private static LongOpenHashSet fixedPairKeys(List<BlitzFixedEdge> fixedEdges) {
        LongOpenHashSet keys = new LongOpenHashSet(Math.max(16, fixedEdges.size() * 2));
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

    private SimTuning tuningFor(SidePolicy sidePolicy) {
        SidePlannerSettings plannerSettings = sidePolicy.planner();
        return new SimTuning(
                tuning.intraTurnPasses(),
                plannerSettings.turn1DeclarePolicy(),
                plannerSettings.wartimeActivityUplift(),
                plannerSettings.activityActThreshold(),
                tuning.policyCooldownTurns(),
                plannerSettings.localSearchBudgetMs(),
                plannerSettings.localSearchMaxIterations(),
                plannerSettings.candidatesPerAttacker(),
                tuning.beigeTurnsOnDefeat(),
                tuning.stateResolutionMode(),
                tuning.stochasticSeed(),
                tuning.stochasticSampleCount()
        );
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
        SidePolicy actingPolicy,
        SidePolicy defendingPolicy,
        SimTuning effectiveTuning,
        StrategicObjective effectiveObjective,
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

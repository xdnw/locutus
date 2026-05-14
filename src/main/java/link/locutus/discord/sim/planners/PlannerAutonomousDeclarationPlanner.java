package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMaps;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PlannerAutonomousDeclarationPlanner {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();

    private PlannerAutonomousDeclarationPlanner() {
    }

    static Plan plan(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            SidePolicy declarerPolicy,
            SidePolicy targetPolicy,
            int remainingTurns
    ) {
        return planInternal(
            declarerSnapshots,
            targetSnapshots,
            tuning,
            declarerPolicy,
            targetPolicy,
            remainingTurns,
            null,
            false
        );
        }

        static Plan planScorerOnly(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            SidePolicy declarerPolicy,
            SidePolicy targetPolicy,
            int remainingTurns
        ) {
        return planInternal(
            declarerSnapshots,
            targetSnapshots,
            tuning,
            declarerPolicy,
            targetPolicy,
            remainingTurns,
            null,
            true
        );
        }

        static Plan planWithProjectionContext(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            SidePolicy declarerPolicy,
            SidePolicy targetPolicy,
            int remainingTurns
        ) {
        return planInternal(
            declarerSnapshots,
            targetSnapshots,
            tuning,
            declarerPolicy,
            targetPolicy,
            remainingTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext.fromSidePolicies(
                declarerPolicy.objective(),
                declarerPolicy,
                targetPolicy
            ),
            false
        );
        }

        static Plan planScorerOnly(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            SidePlannerSettings declarerPlannerSettings,
            int remainingTurns
        ) {
            return planScorerOnly(
                scenario,
                edges,
                attackerCaps(scenario),
                defenderCaps(scenario),
                attackerNationIds(scenario),
                defenderNationIds(scenario),
                declarerPlannerSettings,
                remainingTurns
            );
            }

            static Plan planScorerOnly(
                CompiledScenario scenario,
                CandidateEdgeTable edges,
                int[] attackerCaps,
                int[] defenderCaps,
                int[] attackerNationIds,
                int[] defenderNationIds,
                SidePlannerSettings declarerPlannerSettings,
                int remainingTurns
            ) {
        if (scenario.attackerCount() == 0 || scenario.defenderCount() == 0 || edges.edgeCount() == 0) {
            return Plan.empty();
        }
            Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                    attackerStrengthRanks(scenario),
                attackerNationIds,
                defenderNationIds
            );
            return planFromAssignment(assignment, edges, attackerNationIds, defenderNationIds);
        }

        private static Plan planInternal(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            SidePolicy declarerPolicy,
            SidePolicy targetPolicy,
            int remainingTurns,
                LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext,
                boolean scorerOnly
        ) {
        if (declarerSnapshots.isEmpty() || targetSnapshots.isEmpty()) {
            return Plan.empty();
        }
        if (declarerPolicy == null) {
            throw new IllegalArgumentException("declarerPolicy must not be null");
        }
        if (targetPolicy == null) {
            throw new IllegalArgumentException("targetPolicy must not be null");
        }
        SimTuning effectiveTuning = tuningForPlannerSettings(tuning, declarerPolicy.planner());

        CompiledScenario scenario = SCENARIO_COMPILER.compileWithoutRelevantDefenderIndexes(
                declarerSnapshots,
                targetSnapshots,
                OverrideSet.EMPTY,
                sameTeamTreaty(declarerSnapshots, targetSnapshots),
                Map.of()
        );
        int[] attackerCaps = attackerCaps(scenario);
        int[] defenderCaps = defenderCaps(scenario);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
            effectiveTuning,
                OverrideSet.EMPTY,
                declarerPolicy.objective(),
                declarerPolicy.opening(),
                attackerCaps,
                defenderCaps,
                edges
        );
        if (edges.edgeCount() == 0) {
            return Plan.empty();
        }
        edges = applyLaterDeclarationPolicy(
                edges,
                scenario,
                declarerPolicy.projection().laterDeclarationScoringPolicy(),
                declarerPolicy.planner().laterDeclarationScoreThreshold()
        );
        if (edges.edgeCount() == 0) {
            return Plan.empty();
        }
        if (scorerOnly) {
            return planScorerOnly(
                    scenario,
                    edges,
                    attackerCaps,
                    defenderCaps,
                    attackerNationIds(scenario),
                    defenderNationIds(scenario),
                    declarerPolicy.planner(),
                    remainingTurns
            );
        }
        Map<Integer, List<Integer>> assignment = projectionContext == null
            ? LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks(scenario),
                attackerNationIds(scenario),
                defenderNationIds(scenario),
                List.of(),
                Math.max(1, remainingTurns)
            )
            : LongHorizonAssignmentOptimizer.solveDetailed(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks(scenario),
                attackerNationIds(scenario),
                defenderNationIds(scenario),
                List.of(),
                Math.max(1, remainingTurns),
                projectionContext
            ).assignment();
        return planFromAssignment(assignment, edges, scenario);
    }

    private static CandidateEdgeTable applyLaterDeclarationPolicy(
            CandidateEdgeTable rawEdges,
            CompiledScenario scenario,
            LaterDeclarationScoringPolicy scoringPolicy,
            double scoreThreshold
    ) {
        if (scoringPolicy == HeuristicLaterDeclarationScoringPolicy.INSTANCE) {
            return rawEdges;
        }
        CandidateEdgeTable rescoredEdges = new CandidateEdgeTable(rawEdges.edgeCount());
        rescoredEdges.configureComponentRetention(retainedComponentPolicy(rawEdges));
        double[] targetBestActionability = targetBestActionability(rawEdges, scenario);
        double[] targetSupportActionability = targetSupportActionability(rawEdges, scenario);
        for (int edgeIndex = 0; edgeIndex < rawEdges.edgeCount(); edgeIndex++) {
            int attackerIndex = rawEdges.attackerIndex(edgeIndex);
            int defenderIndex = rawEdges.defenderIndex(edgeIndex);
            DBNationSnapshot declarer = scenario.attacker(attackerIndex);
            DBNationSnapshot target = scenario.defender(defenderIndex);
            double openingScore = Math.max(0d, rawEdges.scalarScore(edgeIndex));
            double score = scoringPolicy.score(new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
                    openingScore,
                    rawEdges.retainsImmediateHarm() ? rawEdges.immediateHarm(edgeIndex) : openingScore,
                    rawEdges.retainsSelfExposure() ? rawEdges.selfExposure(edgeIndex) : 0d,
                    rawEdges.retainsResourceSwing() ? rawEdges.resourceSwing(edgeIndex) : 0d,
                    rawEdges.retainsControlLeverage() ? rawEdges.controlLeverage(edgeIndex) : 0d,
                    rawEdges.retainsFutureWarLeverage() ? rawEdges.futureWarLeverage(edgeIndex) : 0d,
                    OpeningMetricSummary.defenderControlPressure(target),
                    counterStrength(declarer),
                    counterStrength(target),
                    0d,
                    Math.max(1, scenario.attackerFreeOffSlots(attackerIndex)),
                    Math.max(1, scenario.defenderFreeDefSlots(defenderIndex)),
                    targetBestActionability[defenderIndex],
                        Math.max(0d, targetSupportActionability[defenderIndex] - edgeActionability(rawEdges, scenario, edgeIndex)),
                    1d
            ));
            if (score <= scoreThreshold) {
                continue;
            }
            rescoredEdges.add(
                    attackerIndex,
                    defenderIndex,
                    rawEdges.preferredWarTypeId(edgeIndex),
                    rawEdges.bestAttackTypeId(edgeIndex),
                    (float) score,
                    rawEdges.counterRisk(edgeIndex),
                    rawEdges.retainsImmediateHarm() ? rawEdges.immediateHarm(edgeIndex) : 0f,
                    rawEdges.retainsSelfExposure() ? rawEdges.selfExposure(edgeIndex) : 0f,
                    rawEdges.retainsResourceSwing() ? rawEdges.resourceSwing(edgeIndex) : 0f,
                    rawEdges.retainsControlLeverage() ? rawEdges.controlLeverage(edgeIndex) : 0f,
                    rawEdges.retainsFutureWarLeverage() ? rawEdges.futureWarLeverage(edgeIndex) : 0f
            );
        }
        return rescoredEdges;
    }

    private static double[] targetBestActionability(CandidateEdgeTable rawEdges, CompiledScenario scenario) {
        double[] best = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < rawEdges.edgeCount(); edgeIndex++) {
            int attackerIndex = rawEdges.attackerIndex(edgeIndex);
            int defenderIndex = rawEdges.defenderIndex(edgeIndex);
            DBNationSnapshot declarer = scenario.attacker(attackerIndex);
            DBNationSnapshot target = scenario.defender(defenderIndex);
            double conventionalActionability = LaterDeclarationFit.actionability(
                    counterStrength(declarer),
                    counterStrength(target)
            );
            double specialistActionability = rawEdges.retainsResourceSwing()
                    ? LaterDeclarationFit.specialistSlotActionability(
                            rawEdges.resourceSwing(edgeIndex),
                            OpeningMetricSummary.defenderControlPressure(target)
                    )
                    : 0d;
            best[defenderIndex] = Math.max(best[defenderIndex], Math.max(conventionalActionability, specialistActionability));
        }
        return best;
    }

    private static double[] targetSupportActionability(CandidateEdgeTable rawEdges, CompiledScenario scenario) {
        double[] best = new double[scenario.defenderCount()];
        double[] second = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < rawEdges.edgeCount(); edgeIndex++) {
            int defenderIndex = rawEdges.defenderIndex(edgeIndex);
            double actionability = edgeActionability(rawEdges, scenario, edgeIndex);
            if (actionability > best[defenderIndex]) {
                second[defenderIndex] = best[defenderIndex];
                best[defenderIndex] = actionability;
            } else if (actionability > second[defenderIndex]) {
                second[defenderIndex] = actionability;
            }
        }
        double[] support = new double[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < support.length; defenderIndex++) {
            support[defenderIndex] = best[defenderIndex] + second[defenderIndex];
        }
        return support;
    }

    private static double edgeActionability(CandidateEdgeTable rawEdges, CompiledScenario scenario, int edgeIndex) {
        int attackerIndex = rawEdges.attackerIndex(edgeIndex);
        int defenderIndex = rawEdges.defenderIndex(edgeIndex);
        DBNationSnapshot declarer = scenario.attacker(attackerIndex);
        DBNationSnapshot target = scenario.defender(defenderIndex);
        double conventionalActionability = LaterDeclarationFit.actionability(
                counterStrength(declarer),
                counterStrength(target)
        );
        double specialistActionability = rawEdges.retainsResourceSwing()
                ? LaterDeclarationFit.specialistSlotActionability(
                rawEdges.resourceSwing(edgeIndex),
                OpeningMetricSummary.defenderControlPressure(target)
            )
                : 0d;
        return Math.max(conventionalActionability, specialistActionability);
    }

    private static CandidateEdgeComponentPolicy retainedComponentPolicy(CandidateEdgeTable edges) {
        return new CandidateEdgeComponentPolicy(
                edges.retainsImmediateHarm(),
                edges.retainsSelfExposure(),
                edges.retainsResourceSwing(),
                edges.retainsControlLeverage(),
                edges.retainsFutureWarLeverage()
        );
    }

    record Plan(
            Map<Integer, List<Integer>> assignment,
            Map<Long, Integer> warTypeOrdinalsByPair,
            Map<Long, Float> scalarScoresByPair
    ) {
        static Plan empty() {
            return new Plan(Map.of(), Map.of(), Map.of());
        }

        int warTypeOrdinal(int declarerNationId, int targetNationId) {
            return warTypeOrdinalsByPair.getOrDefault(
                    PlannerLocalConflict.pairKey(declarerNationId, targetNationId),
                    WarType.ORD.ordinal()
            );
        }

        float scalarScore(int declarerNationId, int targetNationId) {
            return scalarScoresByPair.getOrDefault(
                    PlannerLocalConflict.pairKey(declarerNationId, targetNationId),
                    0f
            );
        }
    }

    private static TreatyProvider sameTeamTreaty(List<DBNationSnapshot> declarers, List<DBNationSnapshot> targets) {
        Int2IntOpenHashMap teamByNationId = new Int2IntOpenHashMap(Math.max(16, (declarers.size() + targets.size()) * 2));
        teamByNationId.defaultReturnValue(Integer.MIN_VALUE);
        for (DBNationSnapshot declarer : declarers) {
            teamByNationId.put(declarer.nationId(), declarer.teamId());
        }
        for (DBNationSnapshot target : targets) {
            teamByNationId.put(target.nationId(), target.teamId());
        }
        return (declarerId, targetId) -> {
            int declarerTeam = teamByNationId.get(declarerId);
            int targetTeam = teamByNationId.get(targetId);
            return declarerTeam != Integer.MIN_VALUE && declarerTeam == targetTeam;
        };
    }

    private static int[] attackerCaps(CompiledScenario scenario) {
        int[] caps = new int[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < caps.length; attackerIndex++) {
            caps[attackerIndex] = Math.max(0, scenario.attackerFreeOffSlots(attackerIndex));
        }
        return caps;
    }

    private static int[] defenderCaps(CompiledScenario scenario) {
        int[] caps = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < caps.length; defenderIndex++) {
            caps[defenderIndex] = Math.max(0, scenario.defenderFreeDefSlots(defenderIndex));
        }
        return caps;
    }

    private static int[] attackerNationIds(CompiledScenario scenario) {
        int[] nationIds = new int[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < nationIds.length; attackerIndex++) {
            nationIds[attackerIndex] = scenario.attackerNationId(attackerIndex);
        }
        return nationIds;
    }

    private static int[] defenderNationIds(CompiledScenario scenario) {
        int[] nationIds = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < nationIds.length; defenderIndex++) {
            nationIds[defenderIndex] = scenario.defenderNationId(defenderIndex);
        }
        return nationIds;
    }

    private static int[] attackerStrengthRanks(CompiledScenario scenario) {
        int attackerCount = scenario.attackerCount();
        int[] indexes = new int[attackerCount];
        double[] strengths = new double[attackerCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            indexes[attackerIndex] = attackerIndex;
            strengths[attackerIndex] = counterStrength(scenario.attacker(attackerIndex));
        }
        for (int index = 1; index < attackerCount; index++) {
            int attackerIndex = indexes[index];
            double strength = strengths[index];
            int cursor = index;
            while (cursor > 0 && compareAttackerStrength(attackerIndex, strength, indexes[cursor - 1], strengths[cursor - 1]) < 0) {
                indexes[cursor] = indexes[cursor - 1];
                strengths[cursor] = strengths[cursor - 1];
                cursor--;
            }
            indexes[cursor] = attackerIndex;
            strengths[cursor] = strength;
        }
        int[] ranks = new int[scenario.attackerCount()];
        for (int rank = 0; rank < indexes.length; rank++) {
            ranks[indexes[rank]] = rank;
        }
        return ranks;
    }

    private static int compareAttackerStrength(
            int leftAttackerIndex,
            double leftStrength,
            int rightAttackerIndex,
            double rightStrength
    ) {
        int strengthOrder = Double.compare(rightStrength, leftStrength);
        if (strengthOrder != 0) {
            return strengthOrder;
        }
        return Integer.compare(leftAttackerIndex, rightAttackerIndex);
    }

    private static double counterStrength(DBNationSnapshot snapshot) {
        return OpeningMetricSummary.groundStrength(
                snapshot.unit(MilitaryUnit.SOLDIER),
                snapshot.unit(MilitaryUnit.TANK),
                false
        ) + (3d * snapshot.unit(MilitaryUnit.AIRCRAFT)) + (2d * snapshot.unit(MilitaryUnit.SHIP));
    }

    static SimTuning tuningForPlannerSettings(SimTuning tuning, SidePlannerSettings plannerSettings) {
        SimTuning baseTuning = tuning == null ? SimTuning.defaults() : tuning;
        if (plannerSettings == null) {
            return baseTuning;
        }
        return new SimTuning(
                baseTuning.intraTurnPasses(),
                plannerSettings.turn1DeclarePolicy(),
                plannerSettings.wartimeActivityUplift(),
                plannerSettings.activityActThreshold(),
                baseTuning.policyCooldownTurns(),
                plannerSettings.localSearchBudgetMs(),
                plannerSettings.localSearchMaxIterations(),
                plannerSettings.candidatesPerAttacker(),
                baseTuning.beigeTurnsOnDefeat(),
                baseTuning.stateResolutionMode(),
                baseTuning.stochasticSeed(),
                baseTuning.stochasticSampleCount()
        );
    }

    private static Plan planFromAssignment(
            Map<Integer, List<Integer>> assignment,
            CandidateEdgeTable edges,
            CompiledScenario scenario
    ) {
        return planFromAssignment(
            assignment,
            edges,
            attackerNationIds(scenario),
            defenderNationIds(scenario)
        );
        }

        private static Plan planFromAssignment(
            Map<Integer, List<Integer>> assignment,
            CandidateEdgeTable edges,
            int[] attackerNationIds,
            int[] defenderNationIds
        ) {
        if (assignment.isEmpty() || edges.edgeCount() == 0) {
            return Plan.empty();
        }
        int assignmentPairCount = assignmentPairCount(assignment);
        LongOpenHashSet assignedPairs = new LongOpenHashSet(Math.max(16, assignmentPairCount * 2));
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerNationId = entry.getKey();
            for (int defenderNationId : entry.getValue()) {
                assignedPairs.add(PlannerLocalConflict.pairKey(attackerNationId, defenderNationId));
            }
        }
        Long2IntOpenHashMap ordinalsByPair = new Long2IntOpenHashMap(Math.max(16, assignmentPairCount * 2));
        Long2FloatOpenHashMap scoresByPair = new Long2FloatOpenHashMap(Math.max(16, assignmentPairCount * 2));
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerNationId = attackerNationIds[edges.attackerIndex(edgeIndex)];
            int defenderNationId = defenderNationIds[edges.defenderIndex(edgeIndex)];
            long pairKey = PlannerLocalConflict.pairKey(attackerNationId, defenderNationId);
            if (!assignedPairs.contains(pairKey)) {
                continue;
            }
            ordinalsByPair.put(
                    pairKey,
                    validWarTypeOrdinal(edges.preferredWarTypeId(edgeIndex))
            );
            scoresByPair.put(pairKey, edges.scalarScore(edgeIndex));
        }
        return new Plan(
                assignment,
                Long2IntMaps.unmodifiable(ordinalsByPair),
                Long2FloatMaps.unmodifiable(scoresByPair)
        );
    }

    private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
        int count = 0;
        for (List<Integer> targets : assignment.values()) {
            count += targets.size();
        }
        return count;
    }

    private static int validWarTypeOrdinal(byte warTypeOrdinal) {
        return warTypeOrdinal >= 0 && warTypeOrdinal < WarType.values.length
                ? warTypeOrdinal
                : WarType.ORD.ordinal();
    }
}

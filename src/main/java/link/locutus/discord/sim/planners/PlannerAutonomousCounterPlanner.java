package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PlannerAutonomousCounterPlanner {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();

    private PlannerAutonomousCounterPlanner() {
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
            null
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
            )
        );
        }

        static Plan planScorerOnly(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            SidePlannerSettings declarerPlannerSettings,
            int remainingTurns
        ) {
        if (scenario.attackerCount() == 0 || scenario.defenderCount() == 0 || edges.edgeCount() == 0) {
            return Plan.empty();
        }
            LongHorizonAssignmentOptimizer.Candidate candidate = LongHorizonAssignmentOptimizer.solveWithAttackerCaps(
                    edges,
                    scenario,
                    attackerCaps(scenario),
                    defenderCaps(scenario),
                    attackerStrengthRanks(scenario),
                    attackerNationIds(scenario),
                    defenderNationIds(scenario),
                    List.of(),
                    Math.max(1, remainingTurns),
                    false,
                    declarerPlannerSettings
            );
            return new Plan(candidate.assignment(), assignmentWarTypeOrdinals(candidate.assignment(), edges, scenario));
        }

        private static Plan planInternal(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            SidePolicy declarerPolicy,
            SidePolicy targetPolicy,
            int remainingTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext
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

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
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
        return new Plan(assignment, assignmentWarTypeOrdinals(assignment, edges, scenario));
    }

    record Plan(
            Map<Integer, List<Integer>> assignment,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        static Plan empty() {
            return new Plan(Map.of(), Map.of());
        }

        int warTypeOrdinal(int declarerNationId, int targetNationId) {
            return warTypeOrdinalsByPair.getOrDefault(
                    PlannerLocalConflict.pairKey(declarerNationId, targetNationId),
                    WarType.ORD.ordinal()
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
        List<Integer> indexes = new ArrayList<>(scenario.attackerCount());
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            indexes.add(attackerIndex);
        }
        indexes.sort((left, right) -> Double.compare(counterStrength(scenario.attacker(right)), counterStrength(scenario.attacker(left))));
        int[] ranks = new int[scenario.attackerCount()];
        for (int rank = 0; rank < indexes.size(); rank++) {
            ranks[indexes.get(rank)] = rank;
        }
        return ranks;
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

    private static Map<Long, Integer> assignmentWarTypeOrdinals(
            Map<Integer, List<Integer>> assignment,
            CandidateEdgeTable edges,
            CompiledScenario scenario
    ) {
        if (assignment.isEmpty() || edges.edgeCount() == 0) {
            return Map.of();
        }
        Long2IntOpenHashMap ordinalsByPair = new Long2IntOpenHashMap(Math.max(16, assignmentPairCount(assignment) * 2));
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerNationId = scenario.attackerNationId(edges.attackerIndex(edgeIndex));
            int defenderNationId = scenario.defenderNationId(edges.defenderIndex(edgeIndex));
            if (!assignment.getOrDefault(attackerNationId, List.of()).contains(defenderNationId)) {
                continue;
            }
            ordinalsByPair.put(
                    PlannerLocalConflict.pairKey(attackerNationId, defenderNationId),
                    validWarTypeOrdinal(edges.preferredWarTypeId(edgeIndex))
            );
        }
        return Long2IntMaps.unmodifiable(ordinalsByPair);
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
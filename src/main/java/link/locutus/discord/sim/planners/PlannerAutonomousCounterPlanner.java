package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PlannerAutonomousCounterPlanner {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();
    private static final StrategicObjective DEFAULT_OBJECTIVE = new DamageObjective();

    private PlannerAutonomousCounterPlanner() {
    }

    static Map<Integer, List<Integer>> plan(
            List<DBNationSnapshot> declarerSnapshots,
            List<DBNationSnapshot> targetSnapshots,
            SimTuning tuning,
            StrategicObjective counterObjective,
            int remainingTurns
    ) {
        if (declarerSnapshots.isEmpty() || targetSnapshots.isEmpty()) {
            return Map.of();
        }

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
                tuning,
                OverrideSet.EMPTY,
                counterObjective == null ? DEFAULT_OBJECTIVE : counterObjective,
                attackerCaps,
                defenderCaps,
                edges
        );
        if (edges.edgeCount() == 0) {
            return Map.of();
        }
        return LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks(scenario),
                attackerNationIds(scenario),
                defenderNationIds(scenario),
                List.of(),
                Math.max(1, remainingTurns)
        );
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
}
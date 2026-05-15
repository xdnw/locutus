package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzAssignmentPair;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.SidePolicy;
import link.locutus.discord.sim.planners.compile.CompiledActiveWar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlitzPlannerActiveWarEntryPointTest {
    @Test
    void explicitEmptyActiveWarListMatchesOneWayAssign() {
        List<DBNationSnapshot> attackers = buildNations(1, 6, 1000, 500, 500);
        List<DBNationSnapshot> defenders = buildNations(101, 6, 9999, 300, 300);
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());

        BlitzAssignment baseline = planner.assign(
                attackers,
                defenders,
                SidePolicy.heuristicActing("acting", planner.objective()),
                SidePolicy.heuristicPassive("defending", planner.objective()),
                0,
                List.of(),
                72
        );
        BlitzAssignment explicit = planner.assign(
                attackers,
                defenders,
                SidePolicy.heuristicActing("acting", planner.objective()),
                SidePolicy.heuristicPassive("defending", planner.objective()),
                0,
                List.of(),
                List.<CompiledActiveWar>of(),
                72
        );

        assertEquals(baseline.assignment(), explicit.assignment());
        assertEquals(baseline.objectiveSummary(), explicit.objectiveSummary());
        assertEquals(baseline.initialWarTypeOrdinalsByPair(), explicit.initialWarTypeOrdinalsByPair());
    }

    @Test
    void explicitEmptyActiveWarListsMatchSymmetricAssign() {
        List<DBNationSnapshot> sideA = buildNations(1, 6, 1000, 500, 500);
        List<DBNationSnapshot> sideB = buildNations(101, 6, 9999, 300, 300);
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());

        BlitzAssignmentPair baseline = planner.assignSymmetric(
                sideA,
                sideB,
                SidePolicy.heuristicActing("sideA", planner.objective()),
                SidePolicy.heuristicPassive("sideB", planner.objective()),
                0,
                List.of(),
                List.of(),
                72
        );
        BlitzAssignmentPair explicit = planner.assignSymmetric(
                sideA,
                sideB,
                SidePolicy.heuristicActing("sideA", planner.objective()),
                SidePolicy.heuristicPassive("sideB", planner.objective()),
                0,
                List.of(),
                List.of(),
                List.<CompiledActiveWar>of(),
                List.<CompiledActiveWar>of(),
                72
        );

        assertEquals(baseline.sideAAssignment().assignment(), explicit.sideAAssignment().assignment());
        assertEquals(baseline.sideAAssignment().objectiveSummary(), explicit.sideAAssignment().objectiveSummary());
        assertEquals(baseline.sideBAssignment().assignment(), explicit.sideBAssignment().assignment());
        assertEquals(baseline.sideBAssignment().objectiveSummary(), explicit.sideBAssignment().objectiveSummary());
    }

    private static List<DBNationSnapshot> buildNations(int idStart, int count, int teamId, int aircraft, int soldiers) {
        List<DBNationSnapshot> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(DBNationSnapshot.synthetic(idStart + index)
                    .teamId(teamId)
                    .allianceId(teamId)
                    .cities(10)
                    .cityInfra(uniformInfra(10, 1000.0d))
                    .maxOff(5)
                    .currentOffensiveWars(0)
                    .currentDefensiveWars(0)
                    .unit(MilitaryUnit.AIRCRAFT, aircraft)
                    .unit(MilitaryUnit.SOLDIER, soldiers)
                    .warPolicy(WarPolicy.ATTRITION)
                    .build());
        }
        return result;
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        for (int index = 0; index < cities; index++) {
            values[index] = infra;
        }
        return values;
    }
}
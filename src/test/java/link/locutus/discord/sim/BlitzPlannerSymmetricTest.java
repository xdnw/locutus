package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzAssignmentPair;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.SidePolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzPlannerSymmetricTest {
    @Test
    void assignSymmetricMatchesExplicitOneWayAssignWhenOtherSideUsesPassiveLegacy() {
        List<DBNationSnapshot> sideA = buildNations(1, 8, 1000, 1000.0, 500, 500);
        List<DBNationSnapshot> sideB = buildNations(101, 8, 9999, 1000.0, 300, 300);
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());

        BlitzAssignment oneWay = planner.assign(
                sideA,
                sideB,
                SidePolicy.legacy("sideA", planner.objective()),
                SidePolicy.legacyPassive("sideB", planner.objective()),
                0,
                List.of(),
                72
        );
        BlitzAssignmentPair symmetric = planner.assignSymmetric(
                sideA,
                sideB,
                SidePolicy.legacy("sideA", new DamageObjective()),
                SidePolicy.legacyPassive("sideB", new DamageObjective()),
                0,
                List.of(),
                List.of(),
                72
        );

            assertEquals(oneWay.assignment(), symmetric.sideAAssignment().assignment());
            assertEquals(oneWay.objectiveScore(), symmetric.sideAAssignment().objectiveScore());
            assertEquals(oneWay.initialWarTypeOrdinalsByPair(), symmetric.sideAAssignment().initialWarTypeOrdinalsByPair());
        assertTrue(symmetric.sideBAssignment().assignment().isEmpty());
    }

    private List<DBNationSnapshot> buildNations(int idStart, int count, int teamId, double score,
                                                int aircraft, int soldiers) {
        List<DBNationSnapshot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = idStart + i;
            result.add(DBNationSnapshot.synthetic(id)
                    .teamId(teamId)
                    .allianceId(teamId)
                    .score(score)
                    .cities(10)
                    .nonInfraScoreBase(score)
                    .cityInfra(uniformInfra(10, 1000.0))
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

    private double[] uniformInfra(int cities, double infra) {
        double[] arr = new double[cities];
        for (int i = 0; i < cities; i++) {
            arr[i] = infra;
        }
        return arr;
    }
}

package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzFixedEdge;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzPlannerFixedEdgeTest {

    private static final int ATTACKER_TEAM = 1000;
    private static final int DEFENDER_TEAM = 2000;
    private static final double SCORE = 1000.0;

    @Test
    void fixedEdgeConsumesCapacityWithoutDuplicatePlannerAssignment() {
        DBNationSnapshot attacker = nation(1, ATTACKER_TEAM, 1, 350, 500);
        DBNationSnapshot fixedDefender = nation(101, DEFENDER_TEAM, 3, 200, 250);
        DBNationSnapshot alternateDefender = nation(102, DEFENDER_TEAM, 3, 200, 250);

        BlitzAssignment assignment = new BlitzPlanner(SimTuning.defaults()).assign(
                List.of(attacker),
                List.of(fixedDefender, alternateDefender),
                0,
                List.of(new BlitzFixedEdge(attacker.nationId(), fixedDefender.nationId()))
        );

        assertEquals(List.of(fixedDefender.nationId()), assignment.targetsFor(attacker.nationId()));
        assertEquals(1, assignment.pairCount());
    }

    @Test
    void fixedEdgeDirectionSurvivesReciprocalPairNormalization() {
        DBNationSnapshot attackerSide = nation(1, ATTACKER_TEAM, 5, 500, 500);
        DBNationSnapshot defenderSide = nation(101, DEFENDER_TEAM, 5, 500, 500);
        List<DBNationSnapshot> combined = List.of(attackerSide, defenderSide);
        TreatyProvider oppositeSideOnly = (attackerId, defenderId) -> attackerId == defenderId
                || ((attackerId < 100) == (defenderId < 100));

        BlitzAssignment assignment = new BlitzPlanner(
                SimTuning.defaults(),
                oppositeSideOnly,
                OverrideSet.EMPTY,
                new DamageObjective()
        ).assign(
                combined,
                combined,
                0,
                List.of(new BlitzFixedEdge(defenderSide.nationId(), attackerSide.nationId()))
        );

        assertTrue(assignment.targetsFor(defenderSide.nationId()).contains(attackerSide.nationId()));
        assertFalse(assignment.targetsFor(attackerSide.nationId()).contains(defenderSide.nationId()));
        assertEquals(1, assignment.pairCount());
    }

    private static DBNationSnapshot nation(int nationId, int allianceId, int maxOff, int aircraft, int soldiers) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(allianceId)
                .allianceId(allianceId)
                .score(SCORE)
                .cities(10)
                .nonInfraScoreBase(SCORE)
                .cityInfra(uniformInfra(10, 1000.0))
                .maxOff(maxOff)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] arr = new double[cities];
        for (int i = 0; i < cities; i++) {
            arr[i] = infra;
        }
        return arr;
    }
}
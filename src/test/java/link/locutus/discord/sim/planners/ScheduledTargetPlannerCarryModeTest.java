package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import link.locutus.discord.util.PW;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduledTargetPlannerCarryModeTest {
    @Test
    void scheduledPlannerProducesExpectedSingleBucketPlan() {
        DBNationSnapshot attacker = combatant(201, 1, 1_000.0, 15_000, 500, 1_000, 8);
        DBNationSnapshot defender = combatant(202, 2, 950.0, 10_000, 200, 500, 4);

        List<ScheduledAttacker> attackers = List.of(new ScheduledAttacker(attacker, List.of(new AvailabilityWindow(0, 1))));
        List<DBNationSnapshot> defenders = List.of(defender);

        ScheduledTargetPlan plan = new ScheduledTargetPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE
        ).assign(attackers, defenders, 1);

        assertEquals(1, plan.bucketSizeTurns());
        assertEquals(2, plan.buckets().size());
        assertEquals(List.of(201), plan.buckets().get(0).eligibleAttackerIds());
        assertEquals(1, plan.buckets().get(0).assignment().pairCount());
        assertEquals(0, plan.buckets().get(1).assignment().pairCount());
    }

        private static void assertScheduledPlansMatch(ScheduledTargetPlan expected, ScheduledTargetPlan actual) {
                assertEquals(expected.bucketSizeTurns(), actual.bucketSizeTurns());
                assertEquals(expected.buckets().size(), actual.buckets().size());
                for (int index = 0; index < expected.buckets().size(); index++) {
                        ScheduledBucketAssignment expectedBucket = expected.buckets().get(index);
                        ScheduledBucketAssignment actualBucket = actual.buckets().get(index);
                        assertEquals(expectedBucket.startTurn(), actualBucket.startTurn(), "bucket start mismatch at index " + index);
                        assertEquals(expectedBucket.endTurnInclusive(), actualBucket.endTurnInclusive(), "bucket end mismatch at index " + index);
                        assertEquals(expectedBucket.availableAttackerIds(), actualBucket.availableAttackerIds(), "available attackers mismatch at bucket " + index);
                        assertEquals(expectedBucket.eligibleAttackerIds(), actualBucket.eligibleAttackerIds(), "eligible attackers mismatch at bucket " + index);
                        assertEquals(expectedBucket.assignment().assignment(), actualBucket.assignment().assignment(), "assignment mismatch at bucket " + index);
                        assertEquals(expectedBucket.assignment().pairCount(), actualBucket.assignment().pairCount(), "pair count mismatch at bucket " + index);
                        assertEquals(expectedBucket.assignment().objectiveScore(), actualBucket.assignment().objectiveScore(), 1e-9, "objective mismatch at bucket " + index);
                }
                assertEquals(expected.timingComparisons(), actual.timingComparisons());
                assertEquals(expected.diagnostics(), actual.diagnostics());
        }

        private static DBNationSnapshot combatant(
                int nationId,
                int teamId,
                double targetScore,
                int soldiers,
                int tanks,
                int aircraft,
                int ships
        ) {
                int cities = 3;
                double staticScore = PW.computeStaticScoreComponent(cities, 0, 0);
                double unitScore = MilitaryUnit.SOLDIER.getScore(soldiers)
                        + MilitaryUnit.TANK.getScore(tanks)
                        + MilitaryUnit.AIRCRAFT.getScore(aircraft)
                        + MilitaryUnit.SHIP.getScore(ships);
                double infraPerCity = Math.max(0d, ((targetScore - staticScore - unitScore) * 40.0d) / cities);
                return DBNationSnapshot.synthetic(nationId)
                        .teamId(teamId)
                        .allianceId(teamId == 1 ? 10 : 20)
                        .maxOff(3)
                        .cities(cities)
                        .cityInfra(new double[]{infraPerCity, infraPerCity, infraPerCity})
                        .warPolicy(WarPolicy.ATTRITION)
                        .unit(MilitaryUnit.SOLDIER, soldiers)
                        .unit(MilitaryUnit.TANK, tanks)
                        .unit(MilitaryUnit.AIRCRAFT, aircraft)
                        .unit(MilitaryUnit.SHIP, ships)
                        .build();
        }
}


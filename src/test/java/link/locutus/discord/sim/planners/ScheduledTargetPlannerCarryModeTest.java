package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduledTargetPlannerCarryModeTest {
    @Test
    void scheduledPlannerProducesExpectedSingleBucketPlan() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(201)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 15_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .unit(MilitaryUnit.SHIP, 8)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(202)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 200)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

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
}

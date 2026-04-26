package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.AvailabilityWindow;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.ScheduledTargetPlan;
import link.locutus.discord.sim.planners.ScheduledAttacker;
import link.locutus.discord.sim.planners.ScheduledTargetPlanner;
import link.locutus.discord.sim.planners.ScheduledTimingComparison;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledTargetPlannerTest {
        @Test
        void availabilityWindowOverlapsBucketRange() {
                AvailabilityWindow window = new AvailabilityWindow(6, 11);

                assertFalse(window.overlapsBucket(0, 6));
                assertTrue(window.overlapsBucket(6, 12));
                assertTrue(window.overlapsBucket(10, 16));
                assertFalse(window.overlapsBucket(12, 18));
        }

        @Test
        void scheduledAttackerReportsBucketAvailability() {
                ScheduledAttacker attacker = new ScheduledAttacker(
                                DBNationSnapshot.synthetic(1)
                                                .teamId(1)
                                                .allianceId(10)
                                                .score(1_000)
                                                .nonInfraScoreBase(700)
                                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                                .warPolicy(WarPolicy.ATTRITION)
                                                .build(),
                                List.of(new AvailabilityWindow(0, 5), new AvailabilityWindow(12, 17))
                );

                assertTrue(attacker.isAvailable(0, 6));
                assertFalse(attacker.isAvailable(6, 12));
                assertTrue(attacker.isAvailable(12, 18));
        }

        @Test
        void carriesPlannedWarStateIntoNextBucket() {
                DBNationSnapshot attackerSnapshot = DBNationSnapshot.synthetic(1)
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
                DBNationSnapshot defenderSnapshot = DBNationSnapshot.synthetic(2)
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

                ScheduledTargetPlan plan = new ScheduledTargetPlanner(SimTuning.defaults()).assign(
                                List.of(new ScheduledAttacker(attackerSnapshot, List.of(new AvailabilityWindow(0, 1)))),
                                List.of(defenderSnapshot),
                                1
                );

                assertEquals(2, plan.buckets().size());
                assertEquals(1, plan.buckets().get(0).assignment().pairCount());
                assertEquals(0, plan.buckets().get(1).assignment().pairCount());
                assertEquals(1, plan.timingComparisons().size());
                ScheduledTimingComparison comparison = plan.timingComparisons().get(0);
                assertEquals(1, comparison.attackerId());
                assertEquals(0, comparison.currentBucketStartTurn());
                assertEquals(1, comparison.waitBucketStartTurn());
        }

        @Test
        void timingComparisonsUseEligibleAttackersNotWindowAvailability() {
                DBNationSnapshot eligibleAttacker = DBNationSnapshot.synthetic(2)
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
                DBNationSnapshot unavailableAttacker = DBNationSnapshot.synthetic(1)
                                .teamId(1)
                                .allianceId(10)
                                .score(1_000)
                                .currentOffensiveWars(5)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .warPolicy(WarPolicy.ATTRITION)
                                .unit(MilitaryUnit.SOLDIER, 15_000)
                                .unit(MilitaryUnit.TANK, 500)
                                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                                .unit(MilitaryUnit.SHIP, 8)
                                .build();
                DBNationSnapshot defenderSnapshot = DBNationSnapshot.synthetic(3)
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

                ScheduledTargetPlan plan = new ScheduledTargetPlanner(SimTuning.defaults())
                                .assign(
                                                List.of(
                                                                new ScheduledAttacker(unavailableAttacker, List.of(new AvailabilityWindow(0, 1))),
                                                                new ScheduledAttacker(eligibleAttacker, List.of(new AvailabilityWindow(0, 1)))
                                                ),
                                                List.of(defenderSnapshot),
                                                1
                                );

                assertEquals(2, plan.buckets().size());
                assertEquals(List.of(1, 2), plan.buckets().get(0).availableAttackerIds());
                assertEquals(List.of(2), plan.buckets().get(0).eligibleAttackerIds());
                assertEquals(1, plan.timingComparisons().size());
                assertEquals(2, plan.timingComparisons().get(0).attackerId());
        }
}
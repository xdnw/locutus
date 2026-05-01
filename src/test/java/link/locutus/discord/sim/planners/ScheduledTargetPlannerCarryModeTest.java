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
    void directConflictSnapshotsMatchProjectionStateAcrossBuckets() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(101)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .resetHourUtc((byte) 10)
                .policyCooldownTurnsRemaining(3)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .unit(MilitaryUnit.TANK, 50)
                .unit(MilitaryUnit.AIRCRAFT, 200)
                .unitBoughtToday(MilitaryUnit.SOLDIER, 300)
                .pendingBuyNextTurn(MilitaryUnit.SOLDIER, 250)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(102)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .unit(MilitaryUnit.TANK, 40)
                .unit(MilitaryUnit.AIRCRAFT, 100)
                .build();

        OverrideSet overrides = OverrideSet.builder()
                .units(attacker.nationId(), Map.of(
                        MilitaryUnit.SOLDIER, 12_000,
                        MilitaryUnit.TANK, 400,
                        MilitaryUnit.AIRCRAFT, 800
                ))
                .build();
        SimTuning tuning = SimTuning.defaults();
        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                overrides,
                List.of(attacker, defender),
                List.of(),
                0,
                tuning,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );
        PlannerProjectionState state = PlannerProjectionState.seed(overrides, List.of(attacker, defender), List.of(), 0);

        assertSnapshotListsMatch(
                state.snapshotsFor(List.of(attacker.nationId(), defender.nationId())),
                conflict.snapshotsFor(List.of(attacker.nationId(), defender.nationId()))
        );

        Map<Integer, List<Integer>> firstBucket = Map.of(attacker.nationId(), List.of(defender.nationId()));
        conflict.applyAssignmentHorizon(firstBucket, 1);
        state = state.advance(tuning, firstBucket, 1, PlannerTransitionSemantics.ALL_OPTIONAL);

        assertSnapshotListsMatch(
                state.snapshotsFor(List.of(attacker.nationId(), defender.nationId())),
                conflict.snapshotsFor(List.of(attacker.nationId(), defender.nationId()))
        );

        conflict.applyAssignmentHorizon(Map.of(), 4);
        state = state.advance(tuning, Map.of(), 4, PlannerTransitionSemantics.ALL_OPTIONAL);

        assertSnapshotListsMatch(
                state.snapshotsFor(List.of(attacker.nationId(), defender.nationId())),
                conflict.snapshotsFor(List.of(attacker.nationId(), defender.nationId()))
        );
    }

    @Test
    void scheduledPlannerCarryModesProduceEquivalentPlans() {
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

        ScheduledTargetPlan directPlan = new ScheduledTargetPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE,
                ScheduledTargetPlanner.CarryStateMode.DIRECT_CONFLICT
        ).assign(attackers, defenders, 1);
        ScheduledTargetPlan projectionPlan = new ScheduledTargetPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE,
                ScheduledTargetPlanner.CarryStateMode.PROJECTION_STATE
        ).assign(attackers, defenders, 1);

        assertEquals(projectionPlan.bucketSizeTurns(), directPlan.bucketSizeTurns());
        assertEquals(projectionPlan.buckets().size(), directPlan.buckets().size());
        for (int index = 0; index < projectionPlan.buckets().size(); index++) {
            ScheduledBucketAssignment expected = projectionPlan.buckets().get(index);
            ScheduledBucketAssignment actual = directPlan.buckets().get(index);
            assertEquals(expected.startTurn(), actual.startTurn());
            assertEquals(expected.endTurnInclusive(), actual.endTurnInclusive());
            assertEquals(expected.availableAttackerIds(), actual.availableAttackerIds());
            assertEquals(expected.eligibleAttackerIds(), actual.eligibleAttackerIds());
            assertEquals(expected.assignment().assignment(), actual.assignment().assignment());
            assertEquals(expected.assignment().pairCount(), actual.assignment().pairCount());
            assertEquals(expected.assignment().objectiveScore(), actual.assignment().objectiveScore(), 1e-9);
        }
        assertEquals(projectionPlan.timingComparisons(), directPlan.timingComparisons());
        assertEquals(projectionPlan.diagnostics(), directPlan.diagnostics());
    }

        @Test
        void scheduledPlannerCarryModesStayEquivalentAcrossMultiBucketSyntheticFixture() {
                int population = 100;
                int horizonTurns = 720;
                int bucketSizeTurns = 144;
                SimTuning tuning = deterministicComparisonTuning();

                List<DBNationSnapshot> attackers = new ArrayList<>(population);
                List<DBNationSnapshot> defenders = new ArrayList<>(population);
                for (int index = 0; index < population; index++) {
                        attackers.add(benchmarkNation(1_000 + index, 1, index, 3));
                        defenders.add(benchmarkNation(200_000 + index, 2, population - index, 1));
                }

                List<ScheduledAttacker> scheduledAttackers = attackers.stream()
                                .map(attacker -> new ScheduledAttacker(attacker, List.of(new AvailabilityWindow(0, horizonTurns - 1))))
                                .toList();

                ScheduledTargetPlan directPlan = new ScheduledTargetPlanner(
                        tuning,
                                TreatyProvider.NONE,
                                OverrideSet.EMPTY,
                                new DamageObjective(),
                                SnapshotActivityProvider.BASELINE,
                                PlannerTransitionSemantics.NONE,
                                ScheduledTargetPlanner.CarryStateMode.DIRECT_CONFLICT
                ).assign(scheduledAttackers, defenders, bucketSizeTurns);
                ScheduledTargetPlan projectionPlan = new ScheduledTargetPlanner(
                        tuning,
                                TreatyProvider.NONE,
                                OverrideSet.EMPTY,
                                new DamageObjective(),
                                SnapshotActivityProvider.BASELINE,
                                PlannerTransitionSemantics.NONE,
                                ScheduledTargetPlanner.CarryStateMode.PROJECTION_STATE
                ).assign(scheduledAttackers, defenders, bucketSizeTurns);

                assertScheduledPlansMatch(projectionPlan, directPlan);
        }

        @Test
        void directConflictSnapshotsMatchProjectionStateOnBenchmarkSyntheticFixture() {
                int population = 100;
                SimTuning tuning = deterministicComparisonTuning();
                List<DBNationSnapshot> attackers = new ArrayList<>(population);
                List<DBNationSnapshot> defenders = new ArrayList<>(population);
                for (int index = 0; index < population; index++) {
                        attackers.add(benchmarkNation(1_000 + index, 1, index, 3));
                        defenders.add(benchmarkNation(200_000 + index, 2, population - index, 1));
                }

                List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
                combined.addAll(attackers);
                combined.addAll(defenders);
                combined.sort(java.util.Comparator.comparingInt(DBNationSnapshot::nationId));
                List<Integer> nationIds = combined.stream().map(DBNationSnapshot::nationId).toList();

                PlannerProjectionState projectionState = PlannerProjectionState.seed(OverrideSet.EMPTY, combined, List.of(), 0);
                PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                                OverrideSet.EMPTY,
                                combined,
                                List.of(),
                                0,
                                tuning,
                                PlannerTransitionSemantics.NONE
                );

                assertSnapshotListsMatch(
                                projectionState.snapshotsFor(nationIds),
                                conflict.snapshotsFor(nationIds)
                );
        }

            @Test
            void blitzPlannerAssignmentsMatchForEquivalentBenchmarkSnapshots() {
                int population = 100;
                SimTuning tuning = deterministicComparisonTuning();
                List<DBNationSnapshot> attackers = new ArrayList<>(population);
                List<DBNationSnapshot> defenders = new ArrayList<>(population);
                for (int index = 0; index < population; index++) {
                    attackers.add(benchmarkNation(1_000 + index, 1, index, 3));
                    defenders.add(benchmarkNation(200_000 + index, 2, population - index, 1));
                }

                PlannerProjectionState projectionState = PlannerProjectionState.seed(OverrideSet.EMPTY, combined(attackers, defenders), List.of(), 0);
                PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                        OverrideSet.EMPTY,
                        combined(attackers, defenders),
                        List.of(),
                        0,
                        tuning,
                        PlannerTransitionSemantics.NONE
                );

                List<Integer> attackerIds = attackers.stream().map(DBNationSnapshot::nationId).toList();
                List<Integer> defenderIds = defenders.stream().map(DBNationSnapshot::nationId).toList();
                List<DBNationSnapshot> projectionAttackers = projectionState.snapshotsFor(attackerIds);
                List<DBNationSnapshot> projectionDefenders = projectionState.snapshotsFor(defenderIds);
                List<DBNationSnapshot> directAttackers = conflict.snapshotsFor(attackerIds);
                List<DBNationSnapshot> directDefenders = conflict.snapshotsFor(defenderIds);

                assertSnapshotListsMatch(projectionAttackers, directAttackers);
                assertSnapshotListsMatch(projectionDefenders, directDefenders);

                BlitzAssignment projectionAssignment = new BlitzPlanner(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective())
                        .assign(projectionAttackers, projectionDefenders, 0);
                BlitzAssignment directAssignment = new BlitzPlanner(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective())
                        .assign(directAttackers, directDefenders, 0);

                assertEquals(projectionAssignment.assignment(), directAssignment.assignment());
                assertEquals(projectionAssignment.pairCount(), directAssignment.pairCount());
                assertEquals(projectionAssignment.objectiveScore(), directAssignment.objectiveScore(), 1e-9);
            }

        @Test
        void openingCandidateTablesMatchForEquivalentBenchmarkSnapshots() {
                int population = 100;
                SimTuning tuning = deterministicComparisonTuning();
                List<DBNationSnapshot> attackers = new ArrayList<>(population);
                List<DBNationSnapshot> defenders = new ArrayList<>(population);
                for (int index = 0; index < population; index++) {
                        attackers.add(benchmarkNation(1_000 + index, 1, index, 3));
                        defenders.add(benchmarkNation(200_000 + index, 2, population - index, 1));
                }

                PlannerProjectionState projectionState = PlannerProjectionState.seed(OverrideSet.EMPTY, combined(attackers, defenders), List.of(), 0);
                PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                                OverrideSet.EMPTY,
                                combined(attackers, defenders),
                                List.of(),
                                0,
                                tuning,
                                PlannerTransitionSemantics.NONE
                );

                List<Integer> attackerIds = attackers.stream().map(DBNationSnapshot::nationId).toList();
                List<Integer> defenderIds = defenders.stream().map(DBNationSnapshot::nationId).toList();
                List<DBNationSnapshot> projectionAttackers = projectionState.snapshotsFor(attackerIds);
                List<DBNationSnapshot> projectionDefenders = projectionState.snapshotsFor(defenderIds);
                List<DBNationSnapshot> directAttackers = conflict.snapshotsFor(attackerIds);
                List<DBNationSnapshot> directDefenders = conflict.snapshotsFor(defenderIds);

                CompiledScenario projectionScenario = new ScenarioCompiler().compile(
                                projectionAttackers,
                                projectionDefenders,
                                OverrideSet.EMPTY,
                                TreatyProvider.NONE,
                                Map.of()
                );
                CompiledScenario directScenario = new ScenarioCompiler().compile(
                                directAttackers,
                                directDefenders,
                                OverrideSet.EMPTY,
                                TreatyProvider.NONE,
                                Map.of()
                );

                int[] projectionAttCaps = new int[projectionScenario.attackerCount()];
                int[] projectionDefCaps = new int[projectionScenario.defenderCount()];
                int[] directAttCaps = new int[directScenario.attackerCount()];
                int[] directDefCaps = new int[directScenario.defenderCount()];
                java.util.Arrays.fill(projectionAttCaps, 3);
                java.util.Arrays.fill(projectionDefCaps, 3);
                java.util.Arrays.fill(directAttCaps, 3);
                java.util.Arrays.fill(directDefCaps, 3);

                CandidateEdgeTable projectionEdges = new CandidateEdgeTable();
                CandidateEdgeTable directEdges = new CandidateEdgeTable();
                OpeningEvaluator.evaluate(projectionScenario, tuning, OverrideSet.EMPTY, new DamageObjective(), projectionAttCaps, projectionDefCaps, projectionEdges);
                OpeningEvaluator.evaluate(directScenario, tuning, OverrideSet.EMPTY, new DamageObjective(), directAttCaps, directDefCaps, directEdges);

                assertCandidateTablesMatch(projectionEdges, directEdges);
        }

    private static void assertSnapshotListsMatch(List<DBNationSnapshot> expected, List<DBNationSnapshot> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertSnapshotMatches(expected.get(index), actual.get(index));
        }
    }

    private static void assertSnapshotMatches(DBNationSnapshot expected, DBNationSnapshot actual) {
        assertEquals(expected.nationId(), actual.nationId());
        assertEquals(expected.score(), actual.score(), 1e-9);
        assertEquals(expected.currentOffensiveWars(), actual.currentOffensiveWars());
        assertEquals(expected.currentDefensiveWars(), actual.currentDefensiveWars());
        assertEquals(expected.activeOpponentNationIds(), actual.activeOpponentNationIds());
                assertEquals(expected.maxOff(), actual.maxOff());
                assertEquals(expected.warPolicy(), actual.warPolicy());
                assertEquals(expected.nonInfraScoreBase(), actual.nonInfraScoreBase(), 1e-9);
                assertEquals(expected.resetHourUtc(), actual.resetHourUtc());
                assertEquals(expected.resetHourUtcFallback(), actual.resetHourUtcFallback());
        assertEquals(expected.policyCooldownTurnsRemaining(), actual.policyCooldownTurnsRemaining());
                assertEquals(expected.beigeTurns(), actual.beigeTurns());
                assertEquals(expected.vmTurns(), actual.vmTurns());
                assertEquals(expected.projectBits(), actual.projectBits());
                assertEquals(expected.researchBits(), actual.researchBits());
                assertEquals(expected.blitzkriegActive(), actual.blitzkriegActive());
                assertEquals(expected.looterModifier(true), actual.looterModifier(true), 1e-9);
                assertEquals(expected.looterModifier(false), actual.looterModifier(false), 1e-9);
                assertEquals(expected.lootModifier(), actual.lootModifier(), 1e-9);
        assertEquals(expected.pendingBuysNextTurn(MilitaryUnit.SOLDIER), actual.pendingBuysNextTurn(MilitaryUnit.SOLDIER));
        assertEquals(expected.unitsBoughtToday(MilitaryUnit.SOLDIER), actual.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(expected.unit(MilitaryUnit.SOLDIER), actual.unit(MilitaryUnit.SOLDIER));
        assertEquals(expected.unit(MilitaryUnit.TANK), actual.unit(MilitaryUnit.TANK));
        assertEquals(expected.unit(MilitaryUnit.AIRCRAFT), actual.unit(MilitaryUnit.AIRCRAFT));
                assertEquals(expected.unit(MilitaryUnit.SHIP), actual.unit(MilitaryUnit.SHIP));
        assertArrayEquals(expected.cityInfraRaw(), actual.cityInfraRaw(), 1e-9);
                assertArrayEquals(expected.resources(), actual.resources(), 1e-9);
                assertArrayEquals(expected.citySpecialistProfiles(), actual.citySpecialistProfiles());
                for (AttackType type : AttackType.values) {
                        assertEquals(expected.infraAttackModifier(type), actual.infraAttackModifier(type), 1e-9);
                        assertEquals(expected.infraDefendModifier(type), actual.infraDefendModifier(type), 1e-9);
                }
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

        private static void assertCandidateTablesMatch(CandidateEdgeTable expected, CandidateEdgeTable actual) {
                assertEquals(expected.edgeCount(), actual.edgeCount());
                for (int index = 0; index < expected.edgeCount(); index++) {
                        assertEquals(expected.attackerIndex(index), actual.attackerIndex(index), "attacker index mismatch at edge " + index);
                        assertEquals(expected.defenderIndex(index), actual.defenderIndex(index), "defender index mismatch at edge " + index);
                        assertEquals(expected.preferredWarTypeId(index), actual.preferredWarTypeId(index), "war type mismatch at edge " + index);
                        assertEquals(expected.bestAttackTypeId(index), actual.bestAttackTypeId(index), "attack type mismatch at edge " + index);
                        assertEquals(expected.scalarScore(index), actual.scalarScore(index), 1e-6f, "scalar score mismatch at edge " + index);
                        assertEquals(expected.counterRisk(index), actual.counterRisk(index), 1e-6f, "counter risk mismatch at edge " + index);
                }
        }

        private static DBNationSnapshot benchmarkNation(int nationId, int teamId, int offset, int maxOff) {
                int cities = 18 + (offset % 8);
                int aircraft = 1_500 + offset * 3;
                return DBNationSnapshot.synthetic(nationId)
                                .teamId(teamId)
                                .allianceId(teamId)
                                .score(1_000.0d + offset)
                                .cities(cities)
                                .nonInfraScoreBase(500.0d + cities * 50.0d)
                                .cityInfra(uniformInfra(cities, 1_800.0d + (offset % 5) * 100.0d))
                                .maxOff(maxOff)
                                .unit(MilitaryUnit.SOLDIER, 250_000 + offset * 100)
                                .unit(MilitaryUnit.TANK, 20_000 + offset * 20)
                                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                                .unit(MilitaryUnit.SHIP, 200 + offset)
                                .warPolicy(WarPolicy.ATTRITION)
                                .build();
        }

        private static double[] uniformInfra(int cities, double infra) {
                double[] values = new double[cities];
                for (int index = 0; index < values.length; index++) {
                        values[index] = infra;
                }
                return values;
        }

        private static List<DBNationSnapshot> combined(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
                List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
                combined.addAll(attackers);
                combined.addAll(defenders);
                combined.sort(java.util.Comparator.comparingInt(DBNationSnapshot::nationId));
                return combined;
        }

        private static SimTuning deterministicComparisonTuning() {
                return new SimTuning(
                                SimTuning.DEFAULT_INTRA_TURN_PASSES,
                                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                                SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                                60_000L,
                                1,
                                SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                                SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                                SimTuning.DEFAULT_STATE_RESOLUTION_MODE,
                                SimTuning.DEFAULT_STOCHASTIC_SEED,
                                SimTuning.DEFAULT_STOCHASTIC_SAMPLE_COUNT
                );
        }
}

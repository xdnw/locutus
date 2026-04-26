package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.planners.AdHocPlan;
import link.locutus.discord.sim.planners.AdHocSimulationOptions;
import link.locutus.discord.sim.planners.AdHocTargetPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.PlannerCoordinationPolicy;
import link.locutus.discord.sim.planners.PlannerExactValidatorScripts;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.SnapshotActivityProvider;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.sim.combat.ResolutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdHocTargetPlannerTest {
    private static final class CountingDamageObjective extends DamageObjective {
        private final AtomicInteger openingCalls = new AtomicInteger();
        private final AtomicInteger terminalCalls = new AtomicInteger();
        private final Set<Integer> terminalDefenderIds = new LinkedHashSet<>();

        @Override
        public double scoreOpening(OpeningMetricVector metrics, int teamId) {
            openingCalls.incrementAndGet();
            return super.scoreOpening(metrics, teamId);
        }

        @Override
        public double scoreTerminal(TeamScoreView view, int teamId) {
            terminalCalls.incrementAndGet();
            view.forEachNation((nationId, nationTeamId, score) -> {
                if (nationTeamId != teamId) {
                    terminalDefenderIds.add(nationId);
                }
            });
            return super.scoreTerminal(view, teamId);
        }
    }

    @Test
    void ranksTargetsByShortHorizonOutcome() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
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
        DBNationSnapshot weakDefender = DBNationSnapshot.synthetic(2)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 150)
                .unit(MilitaryUnit.AIRCRAFT, 400)
                .unit(MilitaryUnit.SHIP, 4)
                .build();
        DBNationSnapshot strongDefender = DBNationSnapshot.synthetic(3)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 700)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 10)
                .build();

        AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                .rankTargets(attacker, List.of(strongDefender, weakDefender), 3, 2);

        assertEquals(2, plan.recommendations().size());
        assertTrue(plan.recommendations().get(0).objectiveScore() >= plan.recommendations().get(1).objectiveScore());
        assertEquals(
                Set.of(weakDefender.nationId(), strongDefender.nationId()),
                plan.recommendations().stream().map(recommendation -> recommendation.defenderId()).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void excludesOutOfRangeOrFullDefenders() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .build();
        DBNationSnapshot outOfRange = DBNationSnapshot.synthetic(2)
                .teamId(2)
                .allianceId(20)
                .score(10_000)
                .nonInfraScoreBase(10_000)
                .cityInfra(new double[]{2_000, 2_000, 2_000})
                .build();
        DBNationSnapshot fullDef = DBNationSnapshot.synthetic(3)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .currentDefensiveWars(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .build();

        AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                .rankTargets(attacker, List.of(outOfRange, fullDef), 2, 5);

        assertTrue(plan.recommendations().isEmpty());
    }

        @Test
        void defaultRankingUsesDeterministicOpeningScoresInStochasticMode() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
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
        DBNationSnapshot defender = DBNationSnapshot.synthetic(2)
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

        SimTuning tuning = SimTuning.defaults()
                .withStateResolutionMode(ResolutionMode.STOCHASTIC)
                .withStochasticSeed(123L)
                .withStochasticSampleCount(4);

        AdHocPlan plan = new AdHocTargetPlanner(tuning)
                .rankTargets(attacker, List.of(defender), 3, 1);

        assertEquals(1, plan.recommendations().size());
        var summary = plan.recommendations().get(0).scoreSummary();
        assertEquals(1, summary.sampleCount());
        assertEquals(summary.mean(), summary.p10());
        assertEquals(summary.mean(), summary.p50());
        assertEquals(summary.mean(), summary.p90());
        assertEquals(summary.mean(), plan.recommendations().get(0).objectiveScore());
    }

    @Test
    void runtimePreviewOnlyRescoresRetainedShortlist() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(51)
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
        DBNationSnapshot strongDefender = DBNationSnapshot.synthetic(52)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 150)
                .unit(MilitaryUnit.AIRCRAFT, 400)
                .unit(MilitaryUnit.SHIP, 4)
                .build();
        DBNationSnapshot weakerDefender = DBNationSnapshot.synthetic(53)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 14_000)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .unit(MilitaryUnit.SHIP, 7)
                .build();

        CountingDamageObjective objective = new CountingDamageObjective();
        AdHocSimulationOptions options = new AdHocSimulationOptions(
                PlannerCoordinationPolicy.resetWindowSpecialistHold(),
                ScenarioActionPolicy.ALLOW_ALL
        );

        AdHocPlan plan = new AdHocTargetPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                objective
        ).rankTargets(attacker, List.of(weakerDefender, strongDefender), 3, 1, options);

        assertEquals(1, plan.recommendations().size());
        assertTrue(plan.metadata().runtimePreviewApplied());
        assertTrue(objective.openingCalls.get() > 0);
        assertTrue(objective.terminalCalls.get() > 0);
        assertEquals(Set.of(plan.recommendations().get(0).defenderId()), objective.terminalDefenderIds);
    }

    @Test
    void runtimePreviewRetainsStochasticScoreBandsWhenEnabled() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(31)
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
        DBNationSnapshot defender = DBNationSnapshot.synthetic(32)
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

        SimTuning tuning = SimTuning.defaults()
                .withStateResolutionMode(ResolutionMode.STOCHASTIC)
                .withStochasticSeed(123L)
                .withStochasticSampleCount(4);
        AdHocSimulationOptions options = new AdHocSimulationOptions(
                PlannerCoordinationPolicy.resetWindowSpecialistHold(),
                ScenarioActionPolicy.ALLOW_ALL
        );

        AdHocPlan plan = new AdHocTargetPlanner(tuning)
                .rankTargets(attacker, List.of(defender), 3, 1, options);

        assertEquals(1, plan.recommendations().size());
        assertTrue(plan.metadata().runtimePreviewApplied());
        var summary = plan.recommendations().get(0).scoreSummary();
        assertEquals(4, summary.sampleCount());
        assertTrue(summary.p10() <= summary.p50());
        assertTrue(summary.p50() <= summary.p90());
        assertEquals(summary.mean(), plan.recommendations().get(0).objectiveScore());
    }

    @Test
    void planningTurnChangesActivityWeightedAdHocScore() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(11)
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
        DBNationSnapshot defender = DBNationSnapshot.synthetic(12)
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

        SnapshotActivityProvider turnSensitiveActivity = (snapshot, turn) -> snapshot.nationId() == attacker.nationId()
                ? (turn == 0 ? 0.0 : 1.0)
                : 1.0;
        AdHocTargetPlanner planner = new AdHocTargetPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                turnSensitiveActivity
        );

        AdHocPlan turnZero = planner.rankTargets(attacker, List.of(defender), 3, 1, 0, AdHocSimulationOptions.DEFAULT);
        AdHocPlan turnOne = planner.rankTargets(attacker, List.of(defender), 3, 1, 1, AdHocSimulationOptions.DEFAULT);

        assertEquals(1, turnZero.recommendations().size());
        assertEquals(1, turnOne.recommendations().size());
        assertTrue(turnOne.recommendations().get(0).objectiveScore() > turnZero.recommendations().get(0).objectiveScore());
    }

        @Test
        void excludesExistingPairConflicts() {
                DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
                                .teamId(1)
                                .allianceId(10)
                                .score(1_000)
                                .maxOff(3)
                                .activeOpponentNationId(2)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .build();
                DBNationSnapshot defender = DBNationSnapshot.synthetic(2)
                                .teamId(2)
                                .allianceId(20)
                                .score(1_000)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .build();

                AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                                .rankTargets(attacker, List.of(defender), 2, 5);

                assertTrue(plan.recommendations().isEmpty());
        }

        @Test
        void coordinationPolicyAppliesInRuntimePreview() {
                DBNationSnapshot attacker = DBNationSnapshot.synthetic(11)
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
                DBNationSnapshot defender = DBNationSnapshot.synthetic(12)
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

                AdHocSimulationOptions options = new AdHocSimulationOptions(
                                PlannerCoordinationPolicy.resetWindowSpecialistHold(),
                                ScenarioActionPolicy.ALLOW_ALL
                );

                AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                                .rankTargets(attacker, List.of(defender), 2, 1, options);

                assertEquals(1, plan.recommendations().size());
                assertFalse(plan.metadata().exactValidationDefault());
                assertTrue(plan.metadata().runtimePreviewApplied());
    }

        @Test
        void mapReserveCoordinationPolicyIsAccepted() {
                DBNationSnapshot attacker = DBNationSnapshot.synthetic(21)
                                .teamId(1)
                                .allianceId(10)
                                .score(1_000)
                                .maxOff(3)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .warPolicy(WarPolicy.ATTRITION)
                                .build();
                DBNationSnapshot defender = DBNationSnapshot.synthetic(22)
                                .teamId(2)
                                .allianceId(20)
                                .score(1_000)
                                .maxOff(3)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .warPolicy(WarPolicy.ATTRITION)
                                .build();

                AdHocSimulationOptions options = new AdHocSimulationOptions(
                                PlannerCoordinationPolicy.mapReserve(3),
                                ScenarioActionPolicy.ALLOW_ALL
                );

                AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                                .rankTargets(attacker, List.of(defender), 2, 1, options);

                assertTrue(plan.recommendations().isEmpty());
                assertFalse(plan.metadata().exactValidationDefault());
                assertFalse(plan.metadata().runtimePreviewApplied());
    }

    @Test
    void scenarioPolicyDenyDeclaresDropsRuntimePreviewCandidates() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(31)
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
        DBNationSnapshot defender = DBNationSnapshot.synthetic(32)
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

        ScenarioActionPolicy.NationActionPolicy denyDeclares = new ScenarioActionPolicy.NationActionPolicy(
                false,
                true,
                true,
                true,
                EnumSet.allOf(AttackType.class)
        );

        AdHocSimulationOptions options = new AdHocSimulationOptions(
                PlannerExactValidatorScripts.DEFAULT,
                ScenarioActionPolicy.fixed(denyDeclares)
        );

        AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                .rankTargets(attacker, List.of(defender), 2, 1, options);

        assertTrue(plan.recommendations().isEmpty());
    }

    @Test
    void semanticallyAllowAllPolicyDoesNotForceRuntimePreview() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(41)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(42)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .build();

        AdHocSimulationOptions options = new AdHocSimulationOptions(
                PlannerExactValidatorScripts.DEFAULT,
                ScenarioActionPolicy.fixed(ScenarioActionPolicy.NationActionPolicy.allowAll())
        );

        AdHocPlan plan = new AdHocTargetPlanner(SimTuning.defaults())
                .rankTargets(attacker, List.of(defender), 2, 1, options);

                assertTrue(plan.metadata().exactValidationDefault());
        assertFalse(plan.metadata().runtimePreviewApplied());
    }
}
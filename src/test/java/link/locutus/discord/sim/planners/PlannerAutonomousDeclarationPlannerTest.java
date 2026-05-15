package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerAutonomousDeclarationPlannerTest {

    @Test
    void autonomousPlannerUsesSideOpeningAdmissionPolicy() {
        DBNationSnapshot declarer = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 520)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 300)
                .build();
        DBNationSnapshot target = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .unit(MilitaryUnit.SOLDIER, 8_500)
                .unit(MilitaryUnit.TANK, 320)
                .build();

        StrategicObjective objective = BlitzObjective.DAMAGE.objective();
        SidePolicy permissivePolicy = SidePolicy.heuristicActing("acting", objective);
        SidePolicy targetPolicy = SidePolicy.heuristicPassive("target", objective);
        SideOpeningSettings restrictiveOpening = new SideOpeningSettings(
                Arrays.copyOf(permissivePolicy.opening().warTypeWeights(), permissivePolicy.opening().warTypeWeights().length),
                Arrays.copyOf(permissivePolicy.opening().attackTypeWeights(), permissivePolicy.opening().attackTypeWeights().length),
                new CandidateEdgeAdmissionPolicy(1.0d, false, false)
        );
        SidePolicy restrictivePolicy = new SidePolicy(
                "restrictive",
                objective,
                permissivePolicy.planner(),
                restrictiveOpening,
                permissivePolicy.projection(),
                permissivePolicy.turnActor(),
                permissivePolicy.allowInitialDeclarations()
        );

        PlannerAutonomousDeclarationPlanner.Plan permissivePlan = PlannerAutonomousDeclarationPlanner.plan(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                permissivePolicy,
                targetPolicy,
                24
        );
        PlannerAutonomousDeclarationPlanner.Plan restrictivePlan = PlannerAutonomousDeclarationPlanner.plan(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                restrictivePolicy,
                targetPolicy,
                24
        );

        assertFalse(permissivePlan.assignment().isEmpty(),
                "default acting-side opening settings should admit the comparable target");
        assertTrue(restrictivePlan.assignment().isEmpty(),
                "a restrictive acting-side opening admission policy should suppress the same autonomous declaration plan");
    }

    @Test
    void noDeclarationsPolicySuppressesAutonomousDeclarations() {
        DBNationSnapshot declarer = nation(101, 1)
                .maxOff(1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .build();
        DBNationSnapshot target = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 250)
                .build();

        StrategicObjective objective = BlitzObjective.NET_DAMAGE.objective();
        PlannerAutonomousDeclarationPlanner.Plan legacyPlan = PlannerAutonomousDeclarationPlanner.planWithProjectionContext(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                SidePolicy.heuristicActing("acting", objective),
                SidePolicy.heuristicPassive("target", objective),
                24
        );
        PlannerAutonomousDeclarationPlanner.Plan noDeclarationsPlan = PlannerAutonomousDeclarationPlanner.planWithProjectionContext(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                SidePolicy.noDeclarations("acting", objective),
                SidePolicy.heuristicPassive("target", objective),
                24
        );

        assertFalse(legacyPlan.assignment().isEmpty(),
                "legacy projection should still prove the fixture is declaration-eligible");
        assertTrue(noDeclarationsPlan.assignment().isEmpty(),
                "the named no-declarations policy must not retain heuristic later declarations");
    }

    @Test
    void autonomousPlannerCarriesSelectedWarTypeForAssignedPair() {
        DBNationSnapshot declarer = nation(101, 1)
                .maxOff(2)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .build();
        DBNationSnapshot target = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 250)
                .build();

        StrategicObjective objective = BlitzObjective.NET_DAMAGE.objective();
        PlannerAutonomousDeclarationPlanner.Plan plan = PlannerAutonomousDeclarationPlanner.plan(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                SidePolicy.heuristicActing("acting", objective),
                SidePolicy.heuristicPassive("target", objective),
                24
        );

        assertEquals(List.of(target.nationId()), plan.assignment().get(declarer.nationId()));
        assertEquals(WarType.ATT.ordinal(), plan.warTypeOrdinal(declarer.nationId(), target.nationId()),
                "autonomous planner should preserve the opening evaluator's preferred war type for the selected pair");
    }

    @Test
        void scorerOnlyFallbackCanMonopolizeComparableTargets() {
        DBNationSnapshot slotRichDeclarer = nation(101, 1)
                .maxOff(2)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_100)
                .build();
        DBNationSnapshot peerDeclarer = nation(102, 1)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .build();
        DBNationSnapshot targetOne = nation(201, 2)
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();
        DBNationSnapshot targetTwo = nation(202, 2)
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();

        StrategicObjective objective = BlitzObjective.NET_DAMAGE.objective();
        SidePolicy actingPolicy = SidePolicy.heuristicActing("acting", objective);
        SidePolicy fallbackLikePolicy = new SidePolicy(
                "fallbackLike",
                objective,
                SidePlannerSettings.defaults(),
                actingPolicy.opening(),
                actingPolicy.projection(),
                actingPolicy.turnActor(),
                actingPolicy.allowInitialDeclarations()
        );
        SidePolicy passiveTarget = SidePolicy.heuristicPassive("target", objective);

        PlannerAutonomousDeclarationPlanner.Plan fallbackLikePlan = PlannerAutonomousDeclarationPlanner.plan(
                List.of(slotRichDeclarer, peerDeclarer),
                List.of(targetOne, targetTwo),
                SimTuning.defaults(),
                fallbackLikePolicy,
                passiveTarget,
                72
        );
        assertEquals(2, fallbackLikePlan.assignment().getOrDefault(slotRichDeclarer.nationId(), List.of()).size(),
                "The fallback-like no-idle-pressure planner should still allow the stronger slot-rich declarer to monopolize both comparable targets in this fixture");
    }

    @Test
    void autonomousPlannerUsesExplicitLaterDeclarationPolicy() {
        DBNationSnapshot declarer = nation(101, 1)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_100)
                .build();
        DBNationSnapshot target = nation(201, 2)
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();

        StrategicObjective objective = BlitzObjective.NET_DAMAGE.objective();
        SidePolicy legacyPolicy = SidePolicy.heuristicActing("heuristic", objective);
        SidePolicy policyRejected = new SidePolicy(
                "policyRejected",
                objective,
                legacyPolicy.planner().withLaterDeclarationScoreThreshold(0.0d),
                legacyPolicy.opening(),
                new SideProjectionPolicies(
                        HeuristicAttackChoicePolicy.INSTANCE,
                        context -> 0d
                ),
                legacyPolicy.turnActor(),
                legacyPolicy.allowInitialDeclarations()
        );
        SidePolicy targetPolicy = SidePolicy.heuristicPassive("target", objective);

        PlannerAutonomousDeclarationPlanner.Plan legacyPlan = PlannerAutonomousDeclarationPlanner.planWithProjectionContext(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                legacyPolicy,
                targetPolicy,
                24
        );
        PlannerAutonomousDeclarationPlanner.Plan policyRejectedPlan = PlannerAutonomousDeclarationPlanner.planWithProjectionContext(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                policyRejected,
                targetPolicy,
                24
        );

        assertFalse(legacyPlan.assignment().isEmpty(),
                "legacy autonomous later declarations should still select the viable pair");
        assertTrue(policyRejectedPlan.assignment().isEmpty(),
                "autonomous later-declaration scoring should honor the explicit projection policy for the same legal pair");
    }

    @Test
    void scorerOnlyPlannerViewMatchesCompiledScenarioPlan() {
        DBNationSnapshot declarerA = nation(101, 1)
                .maxOff(2)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_100)
                .build();
        DBNationSnapshot declarerB = nation(102, 1)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 1_900)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot targetA = nation(201, 2)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();
        DBNationSnapshot targetB = nation(202, 2)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 17_000)
                .unit(MilitaryUnit.TANK, 1_000)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();

        List<DBNationSnapshot> declarers = List.of(declarerA, declarerB);
        List<DBNationSnapshot> targets = List.of(targetA, targetB);
        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario compiledScenario = compiler.compileForOpeningEvaluation(
                declarers,
                targets,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                java.util.Map.of()
        );
        int[] attackerCaps = new int[compiledScenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < attackerCaps.length; attackerIndex++) {
            attackerCaps[attackerIndex] = compiledScenario.attackerFreeOffSlots(attackerIndex);
        }
        int[] defenderCaps = new int[compiledScenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < defenderCaps.length; defenderIndex++) {
            defenderCaps[defenderIndex] = compiledScenario.defenderFreeDefSlots(defenderIndex);
        }

        CandidateEdgeTable edges = new CandidateEdgeTable();
        StrategicObjective objective = BlitzObjective.NET_DAMAGE.objective();
        OpeningEvaluator.evaluate(
                compiledScenario,
                SimTuning.defaults(),
                OverrideSet.EMPTY,
                objective,
                SideOpeningSettings.defaults(objective),
                attackerCaps,
                defenderCaps,
                edges
        );

        CompiledScenario plannerView = CompiledScenario.scorerOnlyPlannerView(
                declarers,
                targets,
                attackerCaps,
                defenderCaps
        );

        PlannerAutonomousDeclarationPlanner.Plan compiledPlan = PlannerAutonomousDeclarationPlanner.planScorerOnly(
                compiledScenario,
                edges,
                SidePlannerSettings.defaults(),
                72
        );
        PlannerAutonomousDeclarationPlanner.Plan plannerViewPlan = PlannerAutonomousDeclarationPlanner.planScorerOnly(
                plannerView,
                edges,
                SidePlannerSettings.defaults(),
                72
        );

        assertEquals(compiledPlan.assignment(), plannerViewPlan.assignment());
        for (var entry : compiledPlan.assignment().entrySet()) {
            for (int targetNationId : entry.getValue()) {
                assertEquals(
                        compiledPlan.warTypeOrdinal(entry.getKey(), targetNationId),
                        plannerViewPlan.warTypeOrdinal(entry.getKey(), targetNationId)
                );
            }
        }
    }

    @Test
    void scorerOnlyLaterDeclarationsDoNotReviveNegativeEdgesWithMarginalPressure() {
        DBNationSnapshot declarer = nation(101, 1)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot target = nation(201, 2)
                .cities(45)
                .cityInfra(uniformInfra(45, 2_000.0))
                .unit(MilitaryUnit.SOLDIER, 18_000)
                .unit(MilitaryUnit.TANK, 1_800)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();
        CompiledScenario scenario = CompiledScenario.scorerOnlyPlannerView(
                List.of(declarer),
                List.of(target),
                new int[]{1},
                new int[]{1}
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, -1.0f, 0.0f);

        PlannerAutonomousDeclarationPlanner.Plan plan = PlannerAutonomousDeclarationPlanner.planScorerOnly(
                scenario,
                edges,
                SidePlannerSettings.defaults(),
                72
        );

        assertTrue(plan.assignment().isEmpty(),
                "Scorer-only later declarations should not turn a negative pair score positive with opening pressure marginals");
    }

    private static DBNationSnapshot.Builder nation(int nationId, int teamId) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .cities(10)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .warPolicy(WarPolicy.ATTRITION);
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        Arrays.fill(values, infra);
        return values;
    }
}

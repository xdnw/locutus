package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerAutonomousCounterPlannerTest {

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
        SidePolicy permissivePolicy = SidePolicy.legacy("acting", objective);
        SidePolicy targetPolicy = SidePolicy.legacyPassive("target", objective);
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

        PlannerAutonomousCounterPlanner.Plan permissivePlan = PlannerAutonomousCounterPlanner.plan(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                permissivePolicy,
                targetPolicy,
                24
        );
        PlannerAutonomousCounterPlanner.Plan restrictivePlan = PlannerAutonomousCounterPlanner.plan(
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
        PlannerAutonomousCounterPlanner.Plan plan = PlannerAutonomousCounterPlanner.plan(
                List.of(declarer),
                List.of(target),
                SimTuning.defaults(),
                SidePolicy.legacy("acting", objective),
                SidePolicy.legacyPassive("target", objective),
                24
        );

        assertEquals(List.of(target.nationId()), plan.assignment().get(declarer.nationId()));
        assertEquals(WarType.ATT.ordinal(), plan.warTypeOrdinal(declarer.nationId(), target.nationId()),
                "autonomous planner should preserve the opening evaluator's preferred war type for the selected pair");
    }

    @Test
        void autonomousRedeclarePlannerUsesActingProjectionContextForAssignmentShape() {
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

        StrategicObjective objective = BlitzObjective.CONTROL.objective();
        SidePolicy actingPolicy = SidePolicy.legacy("acting", objective);
        SidePolicy fallbackLikePolicy = new SidePolicy(
                "fallbackLike",
                objective,
                SidePlannerSettings.legacy(),
                actingPolicy.opening(),
                actingPolicy.projection(),
                actingPolicy.turnActor(),
                actingPolicy.allowInitialDeclarations()
        );
        SidePolicy passiveTarget = SidePolicy.legacyPassive("target", objective);

        PlannerAutonomousCounterPlanner.Plan fallbackLikePlan = PlannerAutonomousCounterPlanner.plan(
                List.of(slotRichDeclarer, peerDeclarer),
                List.of(targetOne, targetTwo),
                SimTuning.defaults(),
                fallbackLikePolicy,
                passiveTarget,
                72
        );
        PlannerAutonomousCounterPlanner.Plan actingPlan = PlannerAutonomousCounterPlanner.planWithProjectionContext(
                List.of(slotRichDeclarer, peerDeclarer),
                List.of(targetOne, targetTwo),
                SimTuning.defaults(),
                actingPolicy,
                passiveTarget,
                72
        );

        assertEquals(2, fallbackLikePlan.assignment().getOrDefault(slotRichDeclarer.nationId(), List.of()).size(),
                "The fallback-like no-idle-pressure planner should still allow the stronger slot-rich declarer to monopolize both comparable targets in this fixture");
        assertEquals(List.of(targetOne.nationId()), actingPlan.assignment().get(slotRichDeclarer.nationId()),
                "Acting-side projection settings should now shape autonomous assignment instead of silently falling back to legacy defaults");
        assertEquals(List.of(targetTwo.nationId()), actingPlan.assignment().get(peerDeclarer.nationId()),
                "Autonomous planning should spread comparable targets once the acting-side projection context is actually used");
    }

    private static DBNationSnapshot.Builder nation(int nationId, int teamId) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .score(1_000.0)
                .cities(10)
                .nonInfraScoreBase(1_000.0)
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

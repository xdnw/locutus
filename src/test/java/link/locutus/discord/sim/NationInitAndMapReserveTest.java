package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.ReleaseMapAction;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NationInitAndMapReserveTest {

    @Test
    void nationInitBuildsNationThroughWorldBoundary() {
        SimWorld world = new SimWorld();

        double[] resources = ResourceType.getBuffer();
        resources[ResourceType.MONEY.ordinal()] = 2_500d;
        NationInit init = new NationInit(
                42,
                7,
                WarPolicy.PIRATE,
                resources,
                123d,
                new double[]{1_700d, 1_300d},
                4,
                (byte) 6
        );
        resources[ResourceType.MONEY.ordinal()] = 0d;

        world.addNation(init);
        SimNation nation = world.requireNation(42);

        assertEquals(WarPolicy.PIRATE, nation.policy());
        assertEquals(2_500d, nation.resource(ResourceType.MONEY));
        assertEquals(2, nation.cities());
        assertEquals(4, nation.maxOffSlots());
        assertEquals(6, nation.resetHourUtc());
        assertEquals(7, nation.teamId());
    }

    @Test
    void offensiveSlotRuleCountsPirateProjects() {
        long pirateBits = (1L << Projects.PIRATE_ECONOMY.ordinal())
                | (1L << Projects.ADVANCED_PIRATE_ECONOMY.ordinal());

        assertEquals(5, WarSlotRules.offensiveSlotCap(0L));
        assertEquals(6, WarSlotRules.offensiveSlotCap(1L << Projects.PIRATE_ECONOMY.ordinal()));
        assertEquals(7, WarSlotRules.offensiveSlotCap(pirateBits));
    }

    @Test
    void nationCapacityRulesDeriveAndRespectExplicitOverrides() {
        long pirateBits = 1L << Projects.PIRATE_ECONOMY.ordinal();

        assertEquals(6, NationCapacityRules.maxOffSlots(NationCapacityRules.UNSPECIFIED_CAP_OVERRIDE, pirateBits));
        assertEquals(4, NationCapacityRules.maxOffSlots(4, pirateBits));
    }

    @Test
    void liveNationUsesDerivedDailyBuyCapsUntilExplicitlyOverridden() {
        SimNation nation = new SimNation(new NationInit(
                55,
                55,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
                0d,
                new double[]{1_000d, 1_000d},
                5,
                (byte) 0
        ));

        int derivedCap = MilitaryUnit.SOLDIER.getMaxPerDay(2);
        assertEquals(derivedCap, nation.dailyBuyCap(MilitaryUnit.SOLDIER));

        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 7);
        assertEquals(7, nation.dailyBuyCap(MilitaryUnit.SOLDIER));

        nation.clearDailyBuyCapOverride(MilitaryUnit.SOLDIER);
        assertEquals(derivedCap, nation.dailyBuyCap(MilitaryUnit.SOLDIER));
    }

    @Test
    void liveNationDerivesExactUnitCapsFromStoredCityProfilesUntilOverridden() {
        SpecialistCityProfile lowCapacityCity = new SpecialistCityProfile(
                2_000d,
                250,
                100,
                0,
                0d,
                0d,
                1,
                0,
                0,
                0
        );
        SimNation nation = new SimNation(new NationInit(
                56,
                56,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
                0d,
                new double[]{1_000d},
                5,
                (byte) 0,
                0L,
                new SpecialistCityProfile[]{lowCapacityCity}
        ));

        assertEquals(3_000, nation.unitCap(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.unitCap(MilitaryUnit.TANK));

        nation.setUnitCap(MilitaryUnit.SOLDIER, 7);
        assertEquals(7, nation.unitCap(MilitaryUnit.SOLDIER));

        nation.clearUnitCapOverride(MilitaryUnit.SOLDIER);
        assertEquals(3_000, nation.unitCap(MilitaryUnit.SOLDIER));
    }

    @Test
    void nationCapacityRulesMatchLegacyUnitCapSemanticsWithoutCityViews() {
        SpecialistCityProfile[] profiles = {
                new SpecialistCityProfile(2_500d, 300, 95, 0, 0d, 0d, 5, 2, 1, 0),
                new SpecialistCityProfile(2_100d, 180, 88, 3, 0d, 0d, 2, 1, 3, 1)
        };
        double[] cityInfra = {1_450d, 1_075d};
        int researchBits = 1 << (Research.GROUND_CAPACITY.ordinal() * 5);
        Predicate<Project> hasProject = project -> project == Projects.MISSILE_LAUNCH_PAD || project == Projects.INTELLIGENCE_AGENCY;
        List<link.locutus.discord.apiv1.enums.city.ICity> cities = new ArrayList<>(profiles.length);
        for (int i = 0; i < profiles.length; i++) {
            cities.add(profiles[i].cityView(cityInfra[i]));
        }

        for (MilitaryUnit unit : new MilitaryUnit[]{
                MilitaryUnit.SOLDIER,
                MilitaryUnit.TANK,
                MilitaryUnit.AIRCRAFT,
                MilitaryUnit.SHIP,
                MilitaryUnit.MISSILE,
                MilitaryUnit.NUKE,
                MilitaryUnit.SPIES
        }) {
            int expected = unit.getCap(() -> cities, hasProject, researchBits);
            int actual = NationCapacityRules.unitCap(
                    NationCapacityRules.UNSPECIFIED_CAP_OVERRIDE,
                    unit,
                    profiles,
                    cityInfra,
                    0,
                    profiles.length,
                    hasProject,
                    researchBits
            );
            assertEquals(expected, actual, () -> "unit cap mismatch for " + unit);
        }
    }

    @Test
    void reserveMapActionSubtractsSpendableMapsThenReleasesOnAttack() {
        SimWorld world = new SimWorld();
        world.addNation(NationInit.moneyOnly(1, WarPolicy.FORTRESS, 0d, 100d, new double[0], 2, (byte) 0));
        world.addNation(NationInit.moneyOnly(2, WarPolicy.TURTLE, 0d, 100d, new double[0], 2, (byte) 0));
        world.addWar(new SimWar(900, 1, 2, WarType.ORD));

        world.apply(new ReserveMapAction(900, 1, 2));
        SimWar war = world.requireWar(900);
        assertEquals(6, war.attackerMaps());
        assertEquals(2, war.attackerReservedMaps());
        assertEquals(4, war.attackerSpendableMaps());
        assertEquals(4, war.asWarStateView().attackerMaps());

        world.apply(new AttackAction(900, 1, AttackType.FORTIFY));
        assertEquals(0, war.attackerReservedMaps());
        assertEquals(6, war.attackerSpendableMaps());

        world.apply(new ReserveMapAction(900, 1, 3));
        world.stepTurnStart();

        assertEquals(7, war.attackerMaps(), "MAP regen should happen at turn start");
        assertEquals(3, war.attackerReservedMaps(), "Reservation should persist until explicitly released");
        assertEquals(4, war.attackerSpendableMaps());

        world.apply(new ReleaseMapAction(900, 1));
        assertEquals(0, war.attackerReservedMaps());
        assertEquals(7, war.attackerSpendableMaps());
    }

    @Test
    void reserveMapActionRejectsOverReserve() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(10, WarPolicy.FORTRESS));
        world.addNation(new SimNation(20, WarPolicy.TURTLE));
        world.addWar(new SimWar(901, 10, 20, WarType.ORD));

        assertThrows(IllegalStateException.class, () -> world.apply(new ReserveMapAction(901, 10, 7)));
    }

    @Test
    void reservePersistsAcrossTurnStartsUntilExplicitlyReleased() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(11, WarPolicy.FORTRESS));
        world.addNation(new SimNation(22, WarPolicy.TURTLE));
        world.addWar(new SimWar(902, 11, 22, WarType.ORD));

        world.apply(new ReserveMapAction(902, 22, 2));
        SimWar war = world.requireWar(902);
        assertEquals(2, war.defenderReservedMaps());
        assertEquals(4, war.defenderSpendableMaps());

        world.stepTurnStart();
        assertEquals(2, war.defenderReservedMaps(), "Reservation should persist after turn start");
        assertEquals(5, war.defenderSpendableMaps());

        world.stepTurnStart();
        assertEquals(2, war.defenderReservedMaps(), "Reservation should still persist without an explicit release");
        assertEquals(6, war.defenderSpendableMaps());

        world.apply(new ReleaseMapAction(902, 22));
        assertEquals(0, war.defenderReservedMaps());
        assertEquals(8, war.defenderSpendableMaps());
    }
}

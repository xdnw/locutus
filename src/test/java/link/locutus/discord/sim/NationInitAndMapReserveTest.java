package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.ReleaseMapAction;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

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

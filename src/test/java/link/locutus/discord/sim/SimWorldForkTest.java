package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.ReserveMapAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimWorldForkTest {

    @Test
    void forkCreatesIndependentNationState() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 2_000_000d, 100d, 3);
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, 2_000_000d, 100d, 3);
        world.addNation(attacker);
        world.addNation(defender);

        attacker.setUnitCount(MilitaryUnit.SOLDIER, 12_000);
        attacker.setUnitCount(MilitaryUnit.TANK, 900);
        attacker.setDailyBuyCap(MilitaryUnit.SOLDIER, 20_000);
        attacker.setUnitCap(MilitaryUnit.SOLDIER, 1_000_000);
        attacker.queueUnitBuy(MilitaryUnit.SOLDIER, 1_500);

        SimWorld fork = world.fork();
        SimNation forkAttacker = fork.requireNation(1);

        forkAttacker.removeUnits(MilitaryUnit.SOLDIER, 5_000);
        forkAttacker.addResource(ResourceType.MONEY, 500_000d);
        forkAttacker.queueUnitBuy(MilitaryUnit.SOLDIER, 2_000);

        assertEquals(12_000, attacker.units(MilitaryUnit.SOLDIER));
        assertEquals(1_500, attacker.pendingBuys(MilitaryUnit.SOLDIER));
        assertEquals(7_000, forkAttacker.units(MilitaryUnit.SOLDIER));
        assertEquals(3_500, forkAttacker.pendingBuys(MilitaryUnit.SOLDIER));
        assertNotEquals(attacker.resource(ResourceType.MONEY), forkAttacker.resource(ResourceType.MONEY));

        attacker.addUnits(MilitaryUnit.TANK, 100);
        assertEquals(1_000, attacker.units(MilitaryUnit.TANK));
        assertEquals(900, forkAttacker.units(MilitaryUnit.TANK));
    }

    @Test
    void forkCreatesIndependentWarAndReservationState() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(11, WarPolicy.FORTRESS));
        world.addNation(new SimNation(22, WarPolicy.TURTLE));
        world.addWar(new SimWar(901, 11, 22, WarType.ORD));
        world.apply(new ReserveMapAction(901, 11, 2));

        SimWorld fork = world.fork();

        SimWar originalWar = world.requireWar(901);
        SimWar forkWar = fork.requireWar(901);
        fork.resolveAttack(901, 11, AttackType.FORTIFY);

        assertEquals(2, originalWar.attackerReservedMaps(), "Original world reserve should remain untouched");
        assertEquals(0, forkWar.attackerReservedMaps(), "Fork reserve is consumed by attack");
        assertEquals(4, originalWar.attackerSpendableMaps());
        assertEquals(6, forkWar.attackerSpendableMaps());
    }

    @Test
    void forkCanAppendNationWithoutMutatingParentLayout() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(31, WarPolicy.FORTRESS));

        SimWorld fork = world.fork();
        fork.addNation(new SimNation(32, WarPolicy.TURTLE));
        fork.requireNation(32).setUnitCount(MilitaryUnit.SOLDIER, 9_000);

        assertEquals(31, world.requireNation(31).nationId());
        assertEquals(31, fork.requireNation(31).nationId());
        assertEquals(9_000, fork.requireNation(32).units(MilitaryUnit.SOLDIER));
        assertNull(world.findNation(32), "Parent world should not see nations appended to the fork");
    }
}

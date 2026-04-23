package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AcceptPeaceAction;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.OfferPeaceAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeigeMechanicsTest {

    @Test
    void beigeDefenderCannotBeDeclaredOn() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 1_000_000d);
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, 1_000_000d);
        defender.applyBeigeTurns(3);

        world.addNation(attacker);
        world.addNation(defender);

        assertFalse(world.canDeclareWar(1, 2));
        assertThrows(IllegalStateException.class, () -> world.declareWar(100, 1, 2, WarType.ORD));
    }

    @Test
    void beigeAttackerCanDeclareAndLeavesBeigeImmediately() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 1_000_000d);
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, 1_000_000d);
        attacker.applyBeigeTurns(4);

        world.addNation(attacker);
        world.addNation(defender);

        assertTrue(world.canDeclareWar(1, 2));
        world.declareWar(101, 1, 2, WarType.ORD);

        assertEquals(0, attacker.beigeTurns());
    }

    @Test
    void beigeNoWarNationsReceiveCoreMilitaryDailyBuyBonus() {
        SimWorld world = new SimWorld();
        double[] resources = ResourceType.getBuffer();
        resources[ResourceType.MONEY.ordinal()] = 1_000_000d;
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS, resources, 0d, 5);
        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 100);
        nation.setUnitCap(MilitaryUnit.SOLDIER, 1_000);
        nation.applyBeigeTurns(3);

        world.addNation(nation);

        assertEquals(115, nation.dailyBuyCap(MilitaryUnit.SOLDIER));
        world.apply(new BuyUnitsAction(1, Map.of(MilitaryUnit.SOLDIER, 115)));
        assertEquals(115, nation.unitsBoughtToday(MilitaryUnit.SOLDIER));
    }

    @Test
    void beigeDailyBuyBonusDoesNotApplyWhileNationHasActiveWars() {
        SimWorld world = new SimWorld();
        double[] resources = ResourceType.getBuffer();
        resources[ResourceType.MONEY.ordinal()] = 1_000_000d;
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS, resources, 0d, 5);
        SimNation opponent = new SimNation(2, WarPolicy.TURTLE, 1_000_000d);
        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 100);
        nation.setUnitCap(MilitaryUnit.SOLDIER, 1_000);

        world.addNation(nation);
        world.addNation(opponent);
        world.declareWar(102, 1, 2, WarType.ORD);
        nation.applyBeigeTurns(3);

        assertEquals(100, nation.dailyBuyCap(MilitaryUnit.SOLDIER));
        assertThrows(IllegalStateException.class, () -> world.apply(new BuyUnitsAction(1, Map.of(MilitaryUnit.SOLDIER, 101))));
    }

    @Test
    void recentOpponentLockoutBlocksRedeclareButReleasesSlotsImmediately() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 1_000_000d);
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, 1_000_000d);
        SimNation thirdNation = new SimNation(3, WarPolicy.PIRATE, 1_000_000d);

        world.addNation(attacker);
        world.addNation(defender);
        world.addNation(thirdNation);

        world.declareWar(200, 1, 2, WarType.ORD);
        assertEquals(1, world.activeWarsForNation(1).size());

        world.apply(new OfferPeaceAction(200, 1));
        world.apply(new AcceptPeaceAction(200, 2));

        assertTrue(world.activeWarsForNation(1).isEmpty());
        assertFalse(world.canDeclareWar(1, 2));
        assertTrue(world.canDeclareWar(1, 3));

        for (int turn = 0; turn < WarParticipationIndex.RECENT_OPPONENT_LOCKOUT_TURNS - 1; turn++) {
            world.stepTurnStart();
        }

        assertFalse(world.canDeclareWar(1, 2));
        world.stepTurnStart();
        assertTrue(world.canDeclareWar(1, 2));
    }
}
package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubleBuyResetTest {

    @Test
    void buyPlacementConsumesDailyCapAndMaterializesNextTurnAcrossResetBoundary() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(22));
        SimNation nation = new SimNation(5, WarPolicy.FORTRESS, 1_000_000_000d, 100d, 2, (byte) 0);
        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 5);
        nation.setUnitCap(MilitaryUnit.SOLDIER, 20);
        world.addNation(nation);

        double unitCost = MilitaryUnit.SOLDIER.getConvertedCost(0);
        double initialMoney = nation.resource(ResourceType.MONEY);

        world.apply(new BuyUnitsAction(5, Map.of(MilitaryUnit.SOLDIER, 5)));

        assertEquals(0, nation.units(MilitaryUnit.SOLDIER));
        assertEquals(5, nation.pendingBuys(MilitaryUnit.SOLDIER));
        assertEquals(5, nation.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(initialMoney - 5 * unitCost, nation.resource(ResourceType.MONEY), 1e-6);

        world.stepTurnStart();

        assertEquals(0, nation.dayPhaseTurn(), "Reset-hour boundary should wrap phase to zero");
        assertEquals(5, nation.units(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.pendingBuys(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.unitsBoughtToday(MilitaryUnit.SOLDIER), "Daily cap should reset on wrap");

        world.apply(new BuyUnitsAction(5, Map.of(MilitaryUnit.SOLDIER, 5)));

        assertEquals(5, nation.units(MilitaryUnit.SOLDIER));
        assertEquals(5, nation.pendingBuys(MilitaryUnit.SOLDIER));
        assertEquals(5, nation.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(initialMoney - 10 * unitCost, nation.resource(ResourceType.MONEY), 1e-6);

        world.stepTurnStart();

        assertEquals(10, nation.units(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.pendingBuys(MilitaryUnit.SOLDIER));
    }

    @Test
    void buyPlacementRejectsWhenDailyCapOrResourcesInsufficient() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation nation = new SimNation(6, WarPolicy.TURTLE, 0d, 100d, 2, (byte) 0);
        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 2);
        nation.setUnitCap(MilitaryUnit.SOLDIER, 10);
        world.addNation(nation);

        assertThrows(IllegalStateException.class, () -> world.apply(new BuyUnitsAction(6, Map.of(MilitaryUnit.SOLDIER, 1))));

        nation.addResource(ResourceType.MONEY, 1_000_000d);
        world.apply(new BuyUnitsAction(6, Map.of(MilitaryUnit.SOLDIER, 2)));

        assertThrows(IllegalStateException.class, () -> world.apply(new BuyUnitsAction(6, Map.of(MilitaryUnit.SOLDIER, 1))));
    }

    @Test
    void buyPlacementRejectsWhenNonMoneyResourceIsMissing() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation nation = new SimNation(8, WarPolicy.FORTRESS, 1_000_000_000d, 100d, 2, (byte) 0);
        nation.setDailyBuyCap(MilitaryUnit.SHIP, 5);
        nation.setUnitCap(MilitaryUnit.SHIP, 10);
        world.addNation(nation);

        assertThrows(IllegalStateException.class, () -> world.apply(new BuyUnitsAction(8, Map.of(MilitaryUnit.SHIP, 1))));

        nation.addResource(ResourceType.STEEL, 100d);
        world.apply(new BuyUnitsAction(8, Map.of(MilitaryUnit.SHIP, 1)));
        assertEquals(1, nation.pendingBuys(MilitaryUnit.SHIP));
    }

    @Test
    void buyPlacementIsAtomicAcrossAllRequestedUnits() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation nation = new SimNation(7, WarPolicy.FORTRESS, 1_000_000d, 100d, 2, (byte) 0);
        nation.setDailyBuyCap(MilitaryUnit.SOLDIER, 5);
        nation.setDailyBuyCap(MilitaryUnit.AIRCRAFT, 0);
        nation.setUnitCap(MilitaryUnit.SOLDIER, 10);
        nation.setUnitCap(MilitaryUnit.AIRCRAFT, 10);
        world.addNation(nation);

        assertThrows(
                IllegalStateException.class,
                () -> world.apply(new BuyUnitsAction(7, Map.of(MilitaryUnit.SOLDIER, 5, MilitaryUnit.AIRCRAFT, 1)))
        );

        assertEquals(0, nation.pendingBuys(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.pendingBuys(MilitaryUnit.AIRCRAFT));
        assertEquals(0, nation.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(0, nation.unitsBoughtToday(MilitaryUnit.AIRCRAFT));
    }

    @Test
    void buyPlacementRejectsUnsupportedUnits() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation nation = new SimNation(9, WarPolicy.FORTRESS, 1_000_000_000d, 100d, 2, (byte) 0);
        world.addNation(nation);

        assertThrows(IllegalArgumentException.class, () -> world.apply(new BuyUnitsAction(9, Map.of(MilitaryUnit.SPIES, 1))));
        assertThrows(IllegalArgumentException.class, () -> world.apply(new BuyUnitsAction(9, Map.of(MilitaryUnit.MONEY, 1))));
        assertThrows(IllegalArgumentException.class, () -> world.apply(new BuyUnitsAction(9, Map.of(MilitaryUnit.INFRASTRUCTURE, 1))));
    }
}

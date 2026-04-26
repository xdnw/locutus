package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.actions.WaitAction;
import link.locutus.discord.sim.strategy.AttritionInfraMode;
import link.locutus.discord.sim.strategy.AirControlBuild;
import link.locutus.discord.sim.strategy.BalancedCasualtyRotation;
import link.locutus.discord.sim.strategy.DoubleBuyWindow;
import link.locutus.discord.sim.strategy.HighScoreEarlyStrike;
import link.locutus.discord.sim.strategy.GroundUnderAir;
import link.locutus.discord.sim.strategy.MapReserveForCoordination;
import link.locutus.discord.sim.strategy.MapReserveForReaction;
import link.locutus.discord.sim.strategy.RaidLootMode;
import link.locutus.discord.sim.strategy.SaveAndStrike;
import link.locutus.discord.sim.strategy.RuleBasedActor;
import link.locutus.discord.sim.strategy.ShipShortageNaval;
import link.locutus.discord.sim.strategy.WeakSubsetFocus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyPrimitiveIntegrationTest {

    @Test
    void attritionInfraModeTargetsInfraHeavyWar() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 180d, 180d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 400d, 380d));

        world.declareWar(2001, 1, 2, WarType.ORD);
        world.applyControlFlagChanges(2001, 1, 0, 1, 0);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 12);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 2);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());
        actor.addPrimitive(new AttritionInfraMode());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2001, action.warId());
        assertEquals(AttackType.AIRSTRIKE_INFRA, action.attackType());
    }

    @Test
    void raidLootModeTargetsMoneyHeavyWar() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 160d, 160d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 160d, 160d));

        world.declareWar(2002, 1, 2, WarType.ORD);
        world.applyControlFlagChanges(2002, 1, 0, 1, 0);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 10);
        world.requireNation(2).addResource(ResourceType.MONEY, 8_000_000d);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());
        actor.addPrimitive(new RaidLootMode());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2002, action.warId());
        assertEquals(AttackType.AIRSTRIKE_MONEY, action.attackType());
    }

    @Test
    void raidLootModeTargetsConvertedLootWhenCashIsLow() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 160d, 160d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 160d, 160d));

        world.declareWar(2007, 1, 2, WarType.ORD);
        world.applyControlFlagChanges(2007, 1, 0, 1, 0);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 10);
        world.requireNation(2).addResource(ResourceType.ALUMINUM, 8_000_000d);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());
        actor.addPrimitive(new RaidLootMode());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2007, action.warId());
        assertEquals(AttackType.AIRSTRIKE_MONEY, action.attackType());
    }

    @Test
    void shipShortageNavalTargetsWeakFleet() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 140d, 140d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 140d, 140d));

        world.declareWar(2003, 1, 2, WarType.ORD);
        world.requireNation(1).setUnitCount(MilitaryUnit.SHIP, 14);
        world.requireNation(2).setUnitCount(MilitaryUnit.SHIP, 2);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new ShipShortageNaval());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2003, action.warId());
        assertEquals(AttackType.NAVAL, action.attackType());
    }

    @Test
    void balancedCasualtyRotationPrefersRarestDamageFamily() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 150d, 150d));
        world.addNation(nation(2, WarPolicy.TURTLE, 4_700d, 90d, 90d));
        world.addNation(nation(3, WarPolicy.TURTLE, 4_650d, 100d, 80d));
        world.addNation(nation(4, WarPolicy.TURTLE, 4_850d, 120d, 120d));

        world.declareWar(2010, 1, 2, WarType.ORD);
        world.declareWar(2011, 1, 3, WarType.ORD);
        world.declareWar(2012, 1, 4, WarType.ORD);

        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 6);
        world.requireNation(1).setUnitCount(MilitaryUnit.TANK, 2);
        world.requireNation(1).setUnitCount(MilitaryUnit.SHIP, 24);

        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 42);
        world.requireNation(2).setUnitCount(MilitaryUnit.TANK, 12);
        world.requireNation(3).setUnitCount(MilitaryUnit.SOLDIER, 38);
        world.requireNation(3).setUnitCount(MilitaryUnit.TANK, 14);
        world.requireNation(4).setUnitCount(MilitaryUnit.SHIP, 30);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new BalancedCasualtyRotation());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2, 3, 4), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertTrue(action.attackType().isNaval(), "selected action=" + action);
    }

    @Test
    void weakSubsetFocusTargetsWeakerEnemyWar() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 150d, 150d));
        world.addNation(nation(2, WarPolicy.TURTLE, 4_300d, 70d, 70d));
        world.addNation(nation(3, WarPolicy.TURTLE, 9_000d, 140d, 140d));

        world.declareWar(2013, 1, 2, WarType.ORD);
        world.declareWar(2014, 1, 3, WarType.ORD);

        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 50);
        world.requireNation(1).setUnitCount(MilitaryUnit.TANK, 20);

        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 24);
        world.requireNation(2).setUnitCount(MilitaryUnit.TANK, 6);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 2);
        world.requireNation(3).setUnitCount(MilitaryUnit.SOLDIER, 72);
        world.requireNation(3).setUnitCount(MilitaryUnit.TANK, 22);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new WeakSubsetFocus());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2, 3), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2013, action.warId());
        assertEquals(AttackType.GROUND, action.attackType());
    }

    @Test
    void highScoreEarlyStrikePrefersHighValueTargetEarly() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 150d, 150d));
        world.addNation(nation(2, WarPolicy.TURTLE, 4_300d, 70d, 70d));
        world.addNation(nation(3, WarPolicy.TURTLE, 9_000d, 140d, 140d));

        world.declareWar(2015, 1, 2, WarType.ORD);
        world.declareWar(2016, 1, 3, WarType.ORD);

        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 50);
        world.requireNation(1).setUnitCount(MilitaryUnit.TANK, 20);

        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 24);
        world.requireNation(2).setUnitCount(MilitaryUnit.TANK, 6);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 2);
        world.requireNation(3).setUnitCount(MilitaryUnit.SOLDIER, 12);
        world.requireNation(3).setUnitCount(MilitaryUnit.TANK, 4);
        world.requireNation(3).setUnitCount(MilitaryUnit.AIRCRAFT, 2);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new HighScoreEarlyStrike());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2, 3), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        AttackAction action = assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(2016, action.warId());
        assertEquals(AttackType.GROUND, action.attackType());
    }

    @Test
    void mapReserveForCoordinationReservesFlexibleWar() {
        SimWorld world = new SimWorld();
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 100d, 100d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 100d, 100d));
        world.addNation(nation(3, WarPolicy.TURTLE, 5_000d, 100d, 100d));

        world.declareWar(2004, 1, 2, WarType.ORD);
        world.declareWar(2005, 1, 3, WarType.ORD);
        world.reserveMaps(2005, 1, 2);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new MapReserveForCoordination());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2, 3), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        ReserveMapAction action = assertInstanceOf(ReserveMapAction.class, actions.get(0));
        assertEquals(2004, action.warId());
        assertEquals(1, action.mapsToReserve());
    }

    @Test
    void mapReserveForReactionTargetsPressureWindowOnSingleWar() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(2));
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 120d, 120d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 120d, 120d));

        world.declareWar(2008, 1, 2, WarType.ORD);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 6);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 10);
        world.applyControlFlagChanges(2008, 2, 0, 1, 0);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new MapReserveForReaction());

        DecisionContext ctx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);
        List<SimAction> actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        ReserveMapAction action = assertInstanceOf(ReserveMapAction.class, actions.get(0));
        assertEquals(2008, action.warId());
        assertEquals(1, action.mapsToReserve());
    }

    @Test
    void saveAndStrikeWaitsBeforeResetAndStrikesOnReset() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(22));
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 140d, 140d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 140d, 140d));

        world.declareWar(2009, 1, 2, WarType.ORD);
        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 40);
        world.requireNation(1).setUnitCount(MilitaryUnit.TANK, 12);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 6);
        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 24);
        world.applyControlFlagChanges(2009, 1, 1, 1, 0);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new SaveAndStrike());

        DecisionContext saveCtx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);
        List<SimAction> saveActions = actor.decide(world, world.requireNation(1), saveCtx);

        assertEquals(1, saveActions.size());
        WaitAction waitAction = assertInstanceOf(WaitAction.class, saveActions.get(0));
        assertEquals(1, waitAction.nationId());

        world.stepTurnStart();

        DecisionContext strikeCtx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);
        List<SimAction> strikeActions = actor.decide(world, world.requireNation(1), strikeCtx);

        assertEquals(1, strikeActions.size());
        AttackAction attackAction = assertInstanceOf(AttackAction.class, strikeActions.get(0));
        assertEquals(2009, attackAction.warId());
        assertEquals(AttackType.AIRSTRIKE_INFRA, attackAction.attackType());
    }

    @Test
    void saveAndStrikeStaysInactiveWhenNoWarHasLegalStrike() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(22));
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 140d, 140d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 140d, 140d));

        world.declareWar(2010, 1, 2, WarType.ORD);
        world.requireNation(1).setUnitCount(MilitaryUnit.SHIP, 4);
        world.requireNation(2).setUnitCount(MilitaryUnit.SHIP, 0);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new SaveAndStrike());

        DecisionContext ctx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);

        assertTrue(actor.decide(world, world.requireNation(1), ctx).isEmpty());
    }

    @Test
    void doubleBuyWindowQueuesResetBoundaryPurchaseAndRepeatsAfterReset() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(22));
        world.addNation(nation(1, WarPolicy.FORTRESS, 5_000d, 150d, 150d));
        world.addNation(nation(2, WarPolicy.TURTLE, 5_000d, 150d, 150d));

        world.declareWar(2006, 1, 2, WarType.ORD);
        world.requireNation(1).setDailyBuyCap(MilitaryUnit.AIRCRAFT, 2);
        world.requireNation(1).setUnitCap(MilitaryUnit.AIRCRAFT, 6);
        world.requireNation(1).addResource(ResourceType.MONEY, 100_000d);
        world.requireNation(1).addResource(ResourceType.ALUMINUM, 100d);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 0);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 12);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new DoubleBuyWindow());

        DecisionContext beforeResetCtx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);
        List<SimAction> firstActions = actor.decide(world, world.requireNation(1), beforeResetCtx);

        assertEquals(1, firstActions.size());
        BuyUnitsAction firstBuy = assertInstanceOf(BuyUnitsAction.class, firstActions.get(0));
        assertEquals(Map.of(MilitaryUnit.AIRCRAFT, 2), firstBuy.units());

        world.apply(firstBuy);
        assertEquals(2, world.requireNation(1).pendingBuys(MilitaryUnit.AIRCRAFT));
        assertEquals(2, world.requireNation(1).unitsBoughtToday(MilitaryUnit.AIRCRAFT));

        world.stepTurnStart();

        assertEquals(2, world.requireNation(1).units(MilitaryUnit.AIRCRAFT));
        assertEquals(0, world.requireNation(1).pendingBuys(MilitaryUnit.AIRCRAFT));
        assertEquals(0, world.requireNation(1).unitsBoughtToday(MilitaryUnit.AIRCRAFT));
        assertEquals(0, world.requireNation(1).dayPhaseTurn());

        DecisionContext afterResetCtx = new DecisionContext(world, world.currentTurn(), Set.of(2), Objective.DAMAGE);
        List<SimAction> secondActions = actor.decide(world, world.requireNation(1), afterResetCtx);

        assertEquals(1, secondActions.size());
        BuyUnitsAction secondBuy = assertInstanceOf(BuyUnitsAction.class, secondActions.get(0));
        assertEquals(Map.of(MilitaryUnit.AIRCRAFT, 2), secondBuy.units());
    }

    private static SimNation nation(int nationId, WarPolicy policy, double scoreBase, double... cityInfra) {
        return new SimNation(nationId, policy, ResourceType.getBuffer(), scoreBase, cityInfra, 4);
    }
}
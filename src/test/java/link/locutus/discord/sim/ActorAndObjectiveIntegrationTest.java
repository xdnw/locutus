package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.input.NationInit;
import link.locutus.discord.sim.strategy.AirControlBuild;
import link.locutus.discord.sim.strategy.GroundUnderAir;
import link.locutus.discord.sim.strategy.RuleBasedActor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for M1 provider seams: Actor, Objective, ActivityProvider, etc.
 */
class ActorAndObjectiveIntegrationTest {

    @Test
    void ruleBasedActorWithSimplePrimitives() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 10000.0, 50.0, 4));
        world.addNation(new SimNation(2, WarPolicy.TURTLE, 10000.0, 50.0, 4));

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());

        SimNation self = world.requireNation(1);
        Set<Integer> neighbors = new HashSet<>();
        neighbors.add(2);
        DecisionContext ctx = new DecisionContext(world, 0, neighbors, Objective.DAMAGE);

        var actions = actor.decide(world, self, ctx);
        assertNotNull(actions);
        // With no active offensive wars, both tactical primitives should pass.
        assertTrue(actions.isEmpty());
    }

    @Test
    void airControlBuildTargetsOffensiveWarWithAirPressure() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 10000.0, 50.0, 4));
        world.addNation(new SimNation(2, WarPolicy.TURTLE, 10000.0, 50.0, 4));

        world.declareWar(1001, 1, 2, WarType.ORD);

        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 120);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 40);
        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 300);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        var actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(AttackType.AIRSTRIKE_AIRCRAFT, ((AttackAction) actions.get(0)).attackType());
        assertEquals(1, world.activeWarsForNation(1).size());
    }

    @Test
    void groundUnderAirTargetsGroundAfterAirControlIsHeld() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 10000.0, 50.0, 4));
        world.addNation(new SimNation(2, WarPolicy.TURTLE, 10000.0, 50.0, 4));

        world.declareWar(1002, 1, 2, WarType.ORD);

        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 120);
        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 600);
        world.requireNation(1).setUnitCount(MilitaryUnit.TANK, 80);
        world.requireNation(2).setUnitCount(MilitaryUnit.AIRCRAFT, 60);
        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 400);
        world.requireNation(2).setUnitCount(MilitaryUnit.TANK, 120);
        world.applyControlFlagChanges(1002, 1, 0, 1, 0);

        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());

        DecisionContext ctx = new DecisionContext(world, 0, Set.of(2), Objective.DAMAGE);
        var actions = actor.decide(world, world.requireNation(1), ctx);

        assertEquals(1, actions.size());
        assertInstanceOf(AttackAction.class, actions.get(0));
        assertEquals(AttackType.GROUND, ((AttackAction) actions.get(0)).attackType());
    }

    @Test
    void damageObjectiveScoresTerminal() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(new NationInit(
                1,
                99,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
                15.0,
                new double[0],
                5,
                (byte) 0
        )));
        world.addNation(new SimNation(new NationInit(
                2,
                99,
                WarPolicy.TURTLE,
                ResourceType.getBuffer(),
                10.0,
                new double[0],
                5,
                (byte) 0
        )));
        world.addNation(new SimNation(new NationInit(
                3,
                3,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
                40.0,
                new double[0],
                5,
                (byte) 0
        )));

        Objective objective = Objective.DAMAGE;
        double team99Score = objective.scoreTerminal(world, 99);
        double team3Score = objective.scoreTerminal(world, 3);

        // DamageObjective: scoreTerminal = ownTeamScore - enemyTeamScore (net proxy)
        // team99: own=15+10=25, enemy=40  => 25-40 = -15
        // team3:  own=40,       enemy=25  => 40-25 = 15
        assertEquals(-15.0, team99Score, 0.01);
        assertEquals(15.0, team3Score, 0.01);
    }

    @Test
    void decisionContextCachesNeighbors() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));

        Set<Integer> neighbors = new HashSet<>();
        neighbors.add(2);

        DecisionContext ctx = new DecisionContext(world, 0, neighbors, Objective.DAMAGE);
        assertEquals(0, ctx.turn());
        assertEquals(neighbors, ctx.neighborNationsInRange());
        assertNotNull(ctx.objective());
    }

    @Test
    void activityProviderBaselineReturnsConstant() {
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS);
        double activity0 = ActivityProvider.BASELINE.activityAt(nation, 0);
        double activity10 = ActivityProvider.BASELINE.activityAt(nation, 10);

        assertEquals(0.5, activity0);
        assertEquals(0.5, activity10);
    }

    @Test
    void activityProviderAlwaysActiveReturnsOne() {
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS);
        double activity = ActivityProvider.ALWAYS_ACTIVE.activityAt(nation, 0);
        assertEquals(1.0, activity);
    }

    @Test
    void resetTimeProviderFromNationReturnsNationResetHour() {
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS, 1000.0, 50.0, 4, (byte) 12);
        byte resetHour = ResetTimeProvider.FROM_NATION.resetHourUtc(nation, 0);
        assertEquals(12, resetHour);
    }

    @Test
    void economyProviderNoOpVictoryLootAcceptsCall() {
        EconomyProvider provider = EconomyProvider.NO_OP;
        SimNation winner = new SimNation(1, WarPolicy.FORTRESS);
        SimNation loser = new SimNation(2, WarPolicy.TURTLE);
        SimWar war = new SimWar(10, 1, 2, WarType.ORD);
        
        // Should not throw
        assertDoesNotThrow(() -> provider.onVictoryLootTransferred(winner, loser, war, 0d));
    }

    @Test
    void simNationHasTeamId() {
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS);
        // Default: teamId equals nationId
        assertEquals(1, nation.teamId());
    }

    @Test
    void simWorldNationsIterableReturnsAllNations() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));
        world.addNation(new SimNation(3, WarPolicy.FORTRESS));

        int count = 0;
        for (SimNation nation : world.nations()) {
            count++;
        }
        assertEquals(3, count);
    }
}

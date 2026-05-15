package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for M1 provider seams: Actor, Objective, ActivityProvider, etc.
 */
class ActorAndObjectiveIntegrationTest {

    @Test
    void damageObjectiveScoresTerminal() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(new NationInit(
                1,
                99,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
            new double[]{1_000, 1_000, 1_000},
                5,
                (byte) 0
        )));
        world.addNation(new SimNation(new NationInit(
                2,
                99,
                WarPolicy.TURTLE,
                ResourceType.getBuffer(),
            new double[]{900, 900, 900},
                5,
                (byte) 0
        )));
        world.addNation(new SimNation(new NationInit(
                3,
                3,
                WarPolicy.FORTRESS,
                ResourceType.getBuffer(),
            new double[]{1_400, 1_400, 1_400},
                5,
                (byte) 0
        )));
        world.requireNation(1).setUnitCount(MilitaryUnit.SOLDIER, 12_000);
        world.requireNation(1).setUnitCount(MilitaryUnit.AIRCRAFT, 400);
        world.requireNation(2).setUnitCount(MilitaryUnit.SOLDIER, 8_000);
        world.requireNation(2).setUnitCount(MilitaryUnit.TANK, 150);
        world.requireNation(3).setUnitCount(MilitaryUnit.SOLDIER, 18_000);
        world.requireNation(3).setUnitCount(MilitaryUnit.TANK, 400);
        world.requireNation(3).setUnitCount(MilitaryUnit.AIRCRAFT, 700);

        Objective objective = Objective.DAMAGE;
        double team99Score = objective.scoreTerminal(world, 99);
        double team3Score = objective.scoreTerminal(world, 3);
        StrategicValueTotals team99Totals = StrategicValueTotals.of(StrategicValueView.of(world), 99);
        StrategicValueTotals team3Totals = StrategicValueTotals.of(StrategicValueView.of(world), 3);

        assertEquals(team99Totals.ownValue() - team99Totals.enemyValue(), team99Score, 0.01);
        assertEquals(team3Totals.ownValue() - team3Totals.enemyValue(), team3Score, 0.01);
        assertTrue(team3Score > team99Score);
        assertEquals(-team99Score, team3Score, 0.01);
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
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS, 1000.0, 4, (byte) 12);
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


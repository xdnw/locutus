package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.DeclareWarAction;
import link.locutus.discord.sim.strategy.AirControlBuild;
import link.locutus.discord.sim.strategy.GroundUnderAir;
import link.locutus.discord.sim.strategy.RuleBasedActor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 end-to-end snapshot test: full sim turn with all provider seams wired.
 * 
 * This test exercises:
 * - SimWorld with ActivityProvider and ResetTimeProvider wired
 * - EconomyProvider callbacks (mocked)
 * - Actor decision-making with objectives
 * - Turn sequencing and state mutations
 */
class M1EndToEndSnapshotTest {

    @Test
    void fullSimTurnWithAllProvidersWired() {
        // Setup: Create a simple war scenario with all providers
        SimTuning tuning = new SimTuning(
                2, // intraTurnPasses
                Turn1DeclarePolicy.ATTACKERS_OPEN_THEN_FREE_DEFENDERS_COUNTER,
                0.15, // wartimeActivityUplift
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD, // activityActThreshold
                60, // policyCooldownTurns
                6, // peaceOfferLifetimeTurns
                1, // mapReserveLifetimeTurns
                250L, // localSearchBudgetMs
                500, // localSearchMaxIterations
                8, // candidatesPerAttacker
                24 // beigeTurnsOnDefeat
        );

        ActivityProvider activityProvider = ActivityProvider.ALWAYS_ACTIVE;
        ResetTimeProvider resetTimeProvider = ResetTimeProvider.FROM_NATION;
        EconomyProvider economyProvider = EconomyProvider.NO_OP;
        AllianceLootProvider allianceLootProvider = AllianceLootProvider.NO_OP;

        SimWorld world = new SimWorld(
                tuning,
                new SimClock(),
                economyProvider,
                allianceLootProvider,
                activityProvider,
                resetTimeProvider
        );

        // Add two nations
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 10000.0, 50.0, 4, (byte) 0));
        world.addNation(new SimNation(2, WarPolicy.TURTLE, 10000.0, 50.0, 4, (byte) 0));

        SimNation attacker = world.requireNation(1);
        SimNation defender = world.requireNation(2);

        // Test 1: Verify providers are accessible
        assertTrue(world.activityProvider() instanceof ActivityProvider);
        assertTrue(world.resetTimeProvider() instanceof ResetTimeProvider);

        // Test 2: Check effective activity with no wars
        double baseActivity = world.effectiveActivityAt(attacker);
        assertEquals(1.0, baseActivity, "ALWAYS_ACTIVE should return 1.0");

        // Test 3: Perform a declare and verify war is created
        world.declareWar(1001, 1, 2, WarType.ORD);
        SimWar war = world.requireWar(1001);
        assertTrue(war.isActive());
        assertEquals(WarType.ORD, war.warType());

        // Test 4: Check activity uplift during war
        double warActivity = world.effectiveActivityAt(attacker);
        assertTrue(warActivity >= 1.0, "Activity should be >= 1.0 due to war");

        // Test 5: Create actors with objective
        RuleBasedActor actor = new RuleBasedActor();
        actor.addPrimitive(new AirControlBuild());
        actor.addPrimitive(new GroundUnderAir());

        Set<Integer> neighbors = new HashSet<>();
        neighbors.add(2);
        DecisionContext ctx = new DecisionContext(world, 0, neighbors, Objective.DAMAGE);

        // Test 6: Actor decision-making
        List<link.locutus.discord.sim.actions.SimAction> actions = actor.decide(world, attacker, ctx);
        assertNotNull(actions, "Actor should return non-null action list");
        // No qualifying air or ground assets yet, so these tactical primitives should still pass.
        assertEquals(0, actions.size(), "No tactical candidate should be emitted without the needed unit mix");

        // Test 7: Manually buy units to verify economy provider integration
        EnumMap<MilitaryUnit, Integer> unitBuys = new EnumMap<>(MilitaryUnit.class);
        unitBuys.put(MilitaryUnit.SOLDIER, 100);

        world.buyUnits(1, unitBuys);
        assertEquals(100, attacker.pendingBuys(MilitaryUnit.SOLDIER), "Units should be queued");

        // Test 8: Advance turn and verify materialization
        world.stepTurnStart();
        assertEquals(100, attacker.units(MilitaryUnit.SOLDIER), "Pending buys should materialize");
        assertEquals(0, attacker.pendingBuys(MilitaryUnit.SOLDIER), "Pending should clear");

        // Test 9: Control flag changes
        boolean blockadeChanged = war.applyControlFlagChanges(1, 1, 0, 0);
        assertFalse(blockadeChanged, "Non-blockade delta should not trigger blockade callback");

        blockadeChanged = war.applyControlFlagChanges(1, 0, 0, 1);
        assertTrue(blockadeChanged, "Blockade delta should trigger callback");
        assertEquals(SimSide.ATTACKER, war.blockadeOwner(), "Attacker should own blockade");

        // Test 10: DamageObjective scoring
        Objective objective = Objective.DAMAGE;
        double teamScore = objective.scoreTerminal(world, 1);
        assertTrue(teamScore >= 0, "Damage score should be non-negative");

        // Test 11: Activity at various turns
        assertEquals(1.0, world.effectiveActivityAt(attacker), "Turn 0 activity");
        world.stepTurnStart();
        assertEquals(1.0, world.effectiveActivityAt(attacker), "Turn 1 activity");

        // Test 12: Verify turn tracking
        assertEquals(2, world.currentTurn(), "Should be on turn 2 after two stepTurnStart calls");

        // Test 13: Multiple nations' activity
        SimNation nation3 = new SimNation(3, WarPolicy.FORTRESS, 10000.0, 50.0, 4, (byte) 0);
        world.addNation(nation3);
        assertEquals(1.0, world.effectiveActivityAt(nation3), "New nation should have baseline activity");

        // Snapshot validation: just ensure no exceptions and key state is correct
        assertTrue(world.requireNation(1).units(MilitaryUnit.SOLDIER) > 0);
        assertEquals(2, world.currentTurn());
    }

    @Test
    void decisionContextCachesInfoCorrectly() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));

        Set<Integer> neighbors = new HashSet<>();
        neighbors.add(2);

        DecisionContext ctx = new DecisionContext(world, 5, neighbors, Objective.DAMAGE);

        assertEquals(5, ctx.turn(), "Turn should be cached");
        assertEquals(neighbors, ctx.neighborNationsInRange(), "Neighbors should be cached");
        assertNotNull(ctx.objective(), "Objective should be non-null");
    }

    @Test
    void activityProviderIntegrationWithTuning() {
        SimTuning tuning = new SimTuning(
                2,
                Turn1DeclarePolicy.ATTACKERS_OPEN_THEN_FREE_DEFENDERS_COUNTER,
                0.25, // High wartime uplift
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD, // activityActThreshold
                60,
                6,
                1,
                250L,
                500,
                8,
                24
        );

        ActivityProvider activity = ActivityProvider.BASELINE;
        SimWorld world = new SimWorld(
                tuning,
                new SimClock(),
                EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP,
                activity,
                ResetTimeProvider.FROM_NATION
        );

        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));

        SimNation nation1 = world.requireNation(1);

        // No wars: just baseline
        double noWarActivity = world.effectiveActivityAt(nation1);
        assertEquals(0.5 + 0.0, noWarActivity, 0.01, "No war should have baseline only");

        // Create a war
        world.declareWar(1001, 1, 2, WarType.ORD);

        // During war: baseline + uplift
        double withWarActivity = world.effectiveActivityAt(nation1);
        assertTrue(withWarActivity > noWarActivity, "War should add uplift");
        assertEquals(Math.min(1.0, 0.5 + 0.25), withWarActivity, 0.01, "Activity capped at 1.0");
    }

    @Test
    void resetTimeProviderControlsClockState() {
        byte resetHour = 12;
        SimNation nation = new SimNation(1, WarPolicy.FORTRESS, 1000.0, 50.0, 4, resetHour);

        ResetTimeProvider provider = ResetTimeProvider.FROM_NATION;
        byte returnedHour = provider.resetHourUtc(nation, 0);

        assertEquals(resetHour, returnedHour, "Provider should return nation's reset hour");
    }

    @Test
    void economyProviderCallbackOnBlockade() {
        List<SimWar> blockadeChanges = new ArrayList<>();

        EconomyProvider provider = new EconomyProvider() {
            @Override
            public void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney) {}

            @Override
            public void onControlFlagChange(SimWar war) {
                blockadeChanges.add(war);
            }
        };

        SimWorld world = new SimWorld(
                SimTuning.defaults(),
                EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE,
                ResetTimeProvider.FROM_NATION
        );
        
        // Replace with the tracking provider
        SimWorld worldWithTracking = new SimWorld(
                SimTuning.defaults(),
                provider,
                AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE,
                ResetTimeProvider.FROM_NATION
        );
        
        worldWithTracking.addNation(new SimNation(1, WarPolicy.FORTRESS));
        worldWithTracking.addNation(new SimNation(2, WarPolicy.TURTLE));

        worldWithTracking.declareWar(1001, 1, 2, WarType.ORD);

        // Non-blockade change should not trigger callback
        worldWithTracking.applyControlFlagChanges(1001, 1, 1, 0, 0);
        assertEquals(0, blockadeChanges.size(), "Non-blockade change should not trigger callback");

        // Blockade change should trigger callback
        worldWithTracking.applyControlFlagChanges(1001, 1, 0, 0, 1);
        assertEquals(1, blockadeChanges.size(), "Blockade change should trigger callback");
        assertEquals(SimSide.ATTACKER, worldWithTracking.requireWar(1001).blockadeOwner());
    }
}

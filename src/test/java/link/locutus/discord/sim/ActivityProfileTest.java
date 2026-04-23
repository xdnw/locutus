package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering the M4 activity model:
 * - Wartime uplift is applied dynamically based on active war state (not baked in)
 * - Peace → war → peace transitions immediately flip uplift
 * - MockActivityProvider cycles per-turn array correctly
 * - GlobalPriorActivityProvider returns the configured prior
 * - activityActThreshold is stored in SimTuning
 */
class ActivityProfileTest {

    private static SimNation nation(int id) {
        return new SimNation(id, WarPolicy.ATTRITION);
    }

    @Test
    void wartimeUpliftAppliedDynamicallyOnWarEntry() {
        double baseActivity = 0.4;
        MockActivityProvider provider = new MockActivityProvider(Map.of(1, new double[]{baseActivity}));
        SimTuning tuning = new SimTuning(
                SimTuning.DEFAULT_INTRA_TURN_PASSES,
                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                SimTuning.DEFAULT_PEACE_OFFER_LIFETIME_TURNS,
                SimTuning.DEFAULT_MAP_RESERVE_LIFETIME_TURNS,
                SimTuning.DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                SimTuning.DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT
        );
        SimWorld world = new SimWorld(tuning, new SimClock(), EconomyProvider.NO_OP, AllianceLootProvider.NO_OP,
                provider, ResetTimeProvider.FROM_NATION);

        NationInit init1 = NationInit.basic(1, WarPolicy.ATTRITION);
        NationInit init2 = NationInit.basic(2, WarPolicy.ATTRITION);
        world.addNation(init1);
        world.addNation(init2);

        SimNation n1 = world.requireNation(1);

        // Before any war: no uplift
        double peacetimeActivity = world.effectiveActivityAt(n1);
        assertEquals(baseActivity, peacetimeActivity, 1e-9, "No uplift when at peace");

        // After declaring war: uplift applied
        world.declareWar(100, 1, 2, WarType.ORD);
        double wartimeActivity = world.effectiveActivityAt(n1);
        double expectedUplift = Math.min(1.0, baseActivity + SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT);
        assertEquals(expectedUplift, wartimeActivity, 1e-9, "Uplift applied when at war");

        // Uplift is higher than peacetime
        assertTrue(wartimeActivity > peacetimeActivity, "Wartime activity exceeds peacetime activity");
    }

    @Test
    void wartimeUpliftRemovedOnWarEnd() {
        double baseActivity = 0.4;
        MockActivityProvider provider = new MockActivityProvider(Map.of(1, new double[]{baseActivity}));
        SimTuning tuning = new SimTuning(
                SimTuning.DEFAULT_INTRA_TURN_PASSES,
                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                1, // peaceOfferLifetimeTurns
                SimTuning.DEFAULT_MAP_RESERVE_LIFETIME_TURNS,
                SimTuning.DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                SimTuning.DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT
        );
        SimWorld world = new SimWorld(tuning, new SimClock(), EconomyProvider.NO_OP, AllianceLootProvider.NO_OP,
                provider, ResetTimeProvider.FROM_NATION);

        NationInit init1 = NationInit.basic(1, WarPolicy.ATTRITION);
        NationInit init2 = NationInit.basic(2, WarPolicy.ATTRITION);
        world.addNation(init1);
        world.addNation(init2);
        SimNation n1 = world.requireNation(1);

        world.declareWar(100, 1, 2, WarType.ORD);
        double wartimeActivity = world.effectiveActivityAt(n1);
        assertTrue(wartimeActivity > baseActivity, "Uplift applied in wartime");

        // End war via peace offer + acceptance
        world.apply(new link.locutus.discord.sim.actions.OfferPeaceAction(100, 1));
        world.apply(new link.locutus.discord.sim.actions.AcceptPeaceAction(100, 2));

        double peacetimeActivity = world.effectiveActivityAt(n1);
        assertEquals(baseActivity, peacetimeActivity, 1e-9, "Uplift removed after war ends");
    }

    @Test
    void mockActivityProviderCyclesPerTurnArray() {
        double[] pattern = {0.1, 0.5, 0.9};
        MockActivityProvider provider = new MockActivityProvider(Map.of(1, pattern));
        SimNation n = nation(1);
        SimNation n2 = nation(2); // not in map

        assertEquals(0.1, provider.activityAt(n, 0), 1e-9);
        assertEquals(0.5, provider.activityAt(n, 1), 1e-9);
        assertEquals(0.9, provider.activityAt(n, 2), 1e-9);
        // cycles
        assertEquals(0.1, provider.activityAt(n, 3), 1e-9);
        assertEquals(0.5, provider.activityAt(n, 4), 1e-9);
        // fallback for unknown nation
        assertEquals(0.5, provider.activityAt(n2, 0), 1e-9);
    }

    @Test
    void globalPriorReturnsConfiguredValue() {
        GlobalPriorActivityProvider provider = new GlobalPriorActivityProvider(0.3);
        SimNation n = nation(42);
        assertEquals(0.3, provider.activityAt(n, 0), 1e-9);
        assertEquals(0.3, provider.activityAt(n, 5), 1e-9);
    }

    @Test
    void globalPriorRejectsOutOfBoundsValue() {
        assertThrows(IllegalArgumentException.class, () -> new GlobalPriorActivityProvider(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new GlobalPriorActivityProvider(1.1));
    }

    @Test
    void activityActThresholdStoredInSimTuning() {
        SimTuning defaults = SimTuning.defaults();
        assertEquals(SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD, defaults.activityActThreshold(), 1e-9);
        // Custom value round-trips
        SimTuning custom = new SimTuning(
                1,
                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                0.0,
                0.7,
                60,
                6,
                1,
                250L,
                100,
                8,
                24
        );
        assertEquals(0.7, custom.activityActThreshold(), 1e-9);
    }

    @Test
    void activityActThresholdValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SimTuning(
                1, SimTuning.DEFAULT_TURN1_DECLARE_POLICY, 0.0, -0.1, 60, 6, 1, 250L, 100, 8, 24));
        assertThrows(IllegalArgumentException.class, () -> new SimTuning(
                1, SimTuning.DEFAULT_TURN1_DECLARE_POLICY, 0.0, 1.1, 60, 6, 1, 250L, 100, 8, 24));
    }

    @Test
    void wartimeUpliftClampedToOne() {
        // With baseActivity=0.95 and uplift=0.15, effective activity must not exceed 1.0
        double baseActivity = 0.95;
        MockActivityProvider provider = new MockActivityProvider(Map.of(1, new double[]{baseActivity}));
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(), EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP, provider, ResetTimeProvider.FROM_NATION);

        world.addNation(NationInit.basic(1, WarPolicy.ATTRITION));
        world.addNation(NationInit.basic(2, WarPolicy.ATTRITION));
        SimNation n1 = world.requireNation(1);

        world.declareWar(100, 1, 2, WarType.ORD);
        double activity = world.effectiveActivityAt(n1);
        assertEquals(1.0, activity, 1e-9, "Activity must be clamped to 1.0");
    }
}

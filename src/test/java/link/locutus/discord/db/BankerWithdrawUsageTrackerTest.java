package link.locutus.discord.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BankerWithdrawUsageTrackerTest {

    @Test
    public void normalizeInterval_defaultsToOneDayForMissingOrInvalidValues() {
        assertEquals(BankerWithdrawUsageTracker.DEFAULT_INTERVAL_MS, BankerWithdrawUsageTracker.normalizeInterval(null));
        assertEquals(BankerWithdrawUsageTracker.DEFAULT_INTERVAL_MS, BankerWithdrawUsageTracker.normalizeInterval(0L));
        assertEquals(BankerWithdrawUsageTracker.DEFAULT_INTERVAL_MS, BankerWithdrawUsageTracker.normalizeInterval(-1L));
    }

    @Test
    public void currentResetAt_usesFixedBucketBoundary() {
        assertEquals(3_600_000L, BankerWithdrawUsageTracker.currentResetAt(1L, 3_600_000L));
        assertEquals(7_200_000L, BankerWithdrawUsageTracker.currentResetAt(3_600_000L, 3_600_000L));
        assertEquals(7_200_000L, BankerWithdrawUsageTracker.currentResetAt(3_600_001L, 3_600_000L));
    }

    @Test
    public void snapshot_resetsStaleUsageIntoCurrentBucket() {
        BankerWithdrawUsageTracker.State state = new BankerWithdrawUsageTracker.State(
                new BankerWithdrawUsageTracker.StoredUsage(125D, 3_600_000L, 10L));

        BankerWithdrawUsageTracker.UsageSnapshot snapshot = state.snapshot(3_600_001L, 3_600_000L);

        assertEquals(0D, snapshot.usedValue());
        assertEquals(7_200_000L, snapshot.resetAt());
        assertEquals(3_600_000L, snapshot.intervalMs());
    }

    @Test
    public void tryReserve_enforcesLimitWithinCurrentBucket() {
        BankerWithdrawUsageTracker.State state = new BankerWithdrawUsageTracker.State(
                new BankerWithdrawUsageTracker.StoredUsage(25D, 3_600_000L, 10L));

        BankerWithdrawUsageTracker.Reservation accepted = state.tryReserve(50D, 100D, 1_000L, 3_600_000L);
        BankerWithdrawUsageTracker.Reservation denied = state.tryReserve(30D, 100D, 2_000L, 3_600_000L);
        BankerWithdrawUsageTracker.UsageSnapshot snapshot = state.snapshot(2_000L, 3_600_000L);

        assertNotNull(accepted);
        assertNull(denied);
        assertEquals(75D, snapshot.usedValue());
        assertEquals(3_600_000L, snapshot.resetAt());
    }

    @Test
    public void rollback_onlyRemovesReservedAmountFromSameBucket() {
        BankerWithdrawUsageTracker.State state = new BankerWithdrawUsageTracker.State(
                new BankerWithdrawUsageTracker.StoredUsage(10D, 3_600_000L, 10L));

        BankerWithdrawUsageTracker.Reservation first = state.tryReserve(25D, 100D, 1_000L, 3_600_000L);
        BankerWithdrawUsageTracker.Reservation second = state.tryReserve(15D, 100D, 2_000L, 3_600_000L);

        BankerWithdrawUsageTracker.UsageSnapshot rolledBack = state.rollback(first, 2_500L, 3_600_000L);
        BankerWithdrawUsageTracker.UsageSnapshot afterSecondRollback = state.rollback(second, 3_700_000L, 3_600_000L);

        assertEquals(25D, rolledBack.usedValue(), 0.0001D);
        assertEquals(0D, afterSecondRollback.usedValue(), 0.0001D, "Expired bucket rollback should not leak into the new bucket");
        assertEquals(7_200_000L, afterSecondRollback.resetAt());
    }
}


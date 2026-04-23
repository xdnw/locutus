package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledTimingComparisonTest {
    @Test
    void derivesAdjacentBucketComparisons() {
        ScheduledBucketAssignment currentBucket = new ScheduledBucketAssignment(
                0,
                5,
                List.of(7),
            List.of(7),
                new BlitzAssignment(Map.of(), List.of(), 10.0)
        );
        ScheduledBucketAssignment waitBucket = new ScheduledBucketAssignment(
                6,
                11,
                List.of(7),
            List.of(7),
                new BlitzAssignment(Map.of(), List.of(), 14.5)
        );

        List<ScheduledTimingComparison> comparisons = ScheduledTimingComparison.fromBuckets(List.of(currentBucket, waitBucket));

        assertEquals(1, comparisons.size());
        ScheduledTimingComparison comparison = comparisons.get(0);
        assertEquals(7, comparison.attackerId());
        assertEquals(0, comparison.currentBucketStartTurn());
        assertEquals(6, comparison.waitBucketStartTurn());
        assertEquals(4.5, comparison.objectiveDelta(), 1e-9);
        assertTrue(comparison.worthWaiting());
    }
}
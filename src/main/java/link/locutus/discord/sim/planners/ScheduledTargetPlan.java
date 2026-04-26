package link.locutus.discord.sim.planners;

import java.util.List;

public record ScheduledTargetPlan(
        int bucketSizeTurns,
        List<ScheduledBucketAssignment> buckets,
        List<ScheduledTimingComparison> timingComparisons,
        List<PlannerDiagnostic> diagnostics
) {
    public ScheduledTargetPlan {
        buckets = List.copyOf(buckets);
        timingComparisons = List.copyOf(timingComparisons);
        diagnostics = List.copyOf(diagnostics);
    }
}
package link.locutus.discord.sim.planners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured comparison between declaring in the current scheduled bucket and waiting for the
 * next bucket a nation remains eligible for.
 *
 * <p>The objective scores are whole-bucket scores from the scheduled planner, not per-attacker
 * marginal contributions.</p>
 */
public record ScheduledTimingComparison(
        int attackerId,
        int currentBucketStartTurn,
        int currentBucketEndTurnInclusive,
        double currentObjectiveScore,
        int waitBucketStartTurn,
        int waitBucketEndTurnInclusive,
        double waitObjectiveScore
) {
    private static final double EPSILON = 1e-9;

    public ScheduledTimingComparison {
        if (attackerId <= 0) {
            throw new IllegalArgumentException("attackerId must be > 0");
        }
        if (currentBucketStartTurn < 0) {
            throw new IllegalArgumentException("currentBucketStartTurn must be >= 0");
        }
        if (currentBucketEndTurnInclusive < currentBucketStartTurn) {
            throw new IllegalArgumentException("currentBucketEndTurnInclusive must be >= currentBucketStartTurn");
        }
        if (waitBucketStartTurn <= currentBucketStartTurn) {
            throw new IllegalArgumentException("waitBucketStartTurn must be > currentBucketStartTurn");
        }
        if (waitBucketEndTurnInclusive < waitBucketStartTurn) {
            throw new IllegalArgumentException("waitBucketEndTurnInclusive must be >= waitBucketStartTurn");
        }
        currentObjectiveScore = Double.isFinite(currentObjectiveScore) ? currentObjectiveScore : 0.0;
        waitObjectiveScore = Double.isFinite(waitObjectiveScore) ? waitObjectiveScore : 0.0;
    }

    public double objectiveDelta() {
        return waitObjectiveScore - currentObjectiveScore;
    }

    public boolean worthWaiting() {
        return objectiveDelta() > EPSILON;
    }

    static List<ScheduledTimingComparison> fromBuckets(List<ScheduledBucketAssignment> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<ScheduledBucketAssignment>> bucketsByAttacker = new LinkedHashMap<>();
        for (ScheduledBucketAssignment bucket : buckets) {
            for (int attackerId : bucket.eligibleAttackerIds()) {
                bucketsByAttacker.computeIfAbsent(attackerId, ignored -> new ArrayList<>()).add(bucket);
            }
        }

        List<ScheduledTimingComparison> comparisons = new ArrayList<>();
        for (Map.Entry<Integer, List<ScheduledBucketAssignment>> entry : bucketsByAttacker.entrySet()) {
            List<ScheduledBucketAssignment> eligibleBuckets = entry.getValue();
            for (int index = 0; index + 1 < eligibleBuckets.size(); index++) {
                ScheduledBucketAssignment currentBucket = eligibleBuckets.get(index);
                ScheduledBucketAssignment waitBucket = eligibleBuckets.get(index + 1);
                comparisons.add(new ScheduledTimingComparison(
                        entry.getKey(),
                        currentBucket.startTurn(),
                        currentBucket.endTurnInclusive(),
                        currentBucket.assignment().objectiveScore(),
                        waitBucket.startTurn(),
                        waitBucket.endTurnInclusive(),
                        waitBucket.assignment().objectiveScore()
                ));
            }
        }
        return List.copyOf(comparisons);
    }
}
package link.locutus.discord.sim.planners;

import java.util.List;

/**
 * Rolling scheduled bucket output.
 *
 * <p>{@code availableAttackerIds} are the nations whose time windows overlap the bucket.
 * {@code eligibleAttackerIds} are the attacker IDs actually passed into the bucket planner after
 * planner-side slot filtering.</p>
 */
public record ScheduledBucketAssignment(
        int startTurn,
        int endTurnInclusive,
        List<Integer> availableAttackerIds,
        List<Integer> eligibleAttackerIds,
        BlitzAssignment assignment
) {
    public ScheduledBucketAssignment {
        availableAttackerIds = List.copyOf(availableAttackerIds);
        eligibleAttackerIds = List.copyOf(eligibleAttackerIds);
    }
}
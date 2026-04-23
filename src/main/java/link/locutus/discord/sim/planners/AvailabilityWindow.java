package link.locutus.discord.sim.planners;

public record AvailabilityWindow(int startTurn, int endTurnInclusive) {
    public AvailabilityWindow {
        if (startTurn < 0) {
            throw new IllegalArgumentException("startTurn must be >= 0");
        }
        if (endTurnInclusive < startTurn) {
            throw new IllegalArgumentException("endTurnInclusive must be >= startTurn");
        }
    }

    public boolean overlapsBucket(int bucketStartTurn, int bucketEndTurnExclusive) {
        return startTurn < bucketEndTurnExclusive && endTurnInclusive >= bucketStartTurn;
    }
}
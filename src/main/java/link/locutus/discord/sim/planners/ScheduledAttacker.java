package link.locutus.discord.sim.planners;

import java.util.List;
import java.util.Objects;

public record ScheduledAttacker(DBNationSnapshot attacker, List<AvailabilityWindow> windows) {
    public ScheduledAttacker {
        Objects.requireNonNull(attacker, "attacker");
        windows = List.copyOf(windows);
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("windows must not be empty");
        }
    }

    public boolean isAvailable(int bucketStartTurn, int bucketEndTurnExclusive) {
        for (AvailabilityWindow window : windows) {
            if (window.overlapsBucket(bucketStartTurn, bucketEndTurnExclusive)) {
                return true;
            }
        }
        return false;
    }
}
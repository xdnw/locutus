package link.locutus.discord.util;

/**
 * Ordering for deferred or condensed work once we decide not to submit immediately.
 *
 * <p>Higher-priority tiers are drained first. USER_INTERACTION and HIGH also use the
 * wider submission threshold so they can consume reserved headroom before background work.</p>
 */
public enum DeferredPriority {
    USER_INTERACTION(true),
    HIGH(true),
    NORMAL(false),
    LOW(false);

    private final boolean reserveHeadroom;

    DeferredPriority(boolean reserveHeadroom) {
        this.reserveHeadroom = reserveHeadroom;
    }

    public boolean reserveHeadroom() {
        return reserveHeadroom;
    }

    public static DeferredPriority highest(DeferredPriority first, DeferredPriority second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }
}


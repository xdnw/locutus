package link.locutus.discord.sim;

/**
 * Activity provider that returns a constant global prior for all nations.
 *
 * <p>Used as a fallback when per-nation activity data is unavailable (e.g., nation
 * has fewer than {@code minHitsForIndividual} activity turns on record). The default
 * prior ({@value DEFAULT_PRIOR}) reflects moderate engagement typical of an active
 * alliance member.</p>
 *
 * <p>This implementation is turn-independent: the same value is returned at every
 * sim turn offset, reflecting the steady-state aggregate across all time-of-day
 * and day-of-week buckets. A more sophisticated implementation could vary by
 * time-of-day bucket once real calibration data is available.</p>
 */
public final class GlobalPriorActivityProvider implements ActivityProvider {

    /** Default global prior activity level (50% = moderate engagement). */
    public static final double DEFAULT_PRIOR = 0.5;

    private final double prior;

    public GlobalPriorActivityProvider() {
        this(DEFAULT_PRIOR);
    }

    public GlobalPriorActivityProvider(double prior) {
        if (prior < 0.0 || prior > 1.0) {
            throw new IllegalArgumentException("prior must be in [0, 1], got: " + prior);
        }
        this.prior = prior;
    }

    @Override
    public double activityAt(SimNation nation, int turn) {
        return prior;
    }
}

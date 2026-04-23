package link.locutus.discord.sim;

import java.util.Map;
import java.util.Objects;

/**
 * Deterministic activity provider for tests and synthetic simulation scenarios.
 *
 * <p>Activity is specified per nation as either:
 * <ul>
 *   <li>A fixed scalar (same value at every turn), or</li>
 *   <li>A per-turn array ({@code p[turn % array.length]}), allowing cyclic patterns.</li>
 * </ul>
 *
 * <p>Nations not present in the map fall back to a configurable default (typically
 * {@link ActivityProvider#BASELINE} level of 0.5). This mirrors the fallback
 * {@link GlobalPriorActivityProvider} would use in production.</p>
 */
public final class MockActivityProvider implements ActivityProvider {

    private final Map<Integer, double[]> activityByNationId;
    private final double fallback;

    /**
     * @param activityByNationId map of nationId to per-turn activity array; the array is cycled
     *                            by {@code turn % array.length}
     * @param fallback            activity level for nations not in the map (in [0, 1])
     */
    public MockActivityProvider(Map<Integer, double[]> activityByNationId, double fallback) {
        Objects.requireNonNull(activityByNationId, "activityByNationId");
        if (fallback < 0.0 || fallback > 1.0) {
            throw new IllegalArgumentException("fallback must be in [0, 1], got: " + fallback);
        }
        this.activityByNationId = Map.copyOf(activityByNationId);
        this.fallback = fallback;
    }

    /** Convenience: all nations use {@link ActivityProvider#BASELINE} (0.5) as default. */
    public MockActivityProvider(Map<Integer, double[]> activityByNationId) {
        this(activityByNationId, 0.5);
    }

    @Override
    public double activityAt(SimNation nation, int turn) {
        double[] arr = activityByNationId.get(nation.nationId());
        if (arr == null || arr.length == 0) {
            return fallback;
        }
        return arr[turn % arr.length];
    }
}

package link.locutus.discord.sim.planners.providers;

import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.sim.ActivityProvider;
import link.locutus.discord.sim.GlobalPriorActivityProvider;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.planners.SnapshotActivityProvider;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * DB-backed activity provider that uses per-nation {@link Activity} data loaded once at
 * planner entry. Activity data must not be loaded inside the sim layer — it is passed in
 * as an already-resolved map so the sim stays DB-free.
 *
 * <p>Each sim turn offset {@code t} is mapped to the appropriate {@code byWeekTurn}
 * bucket: {@code (simStartWeekTurn + t) % 84}. The result is the historical fraction of
 * turns during which that nation was active at that time-of-week.</p>
 *
 * <p>Nations with insufficient activity data (average weekly-turn activity below
 * {@link #DEFAULT_MIN_AVERAGE_ACTIVITY}) fall back to the provided
 * {@link ActivityProvider} (typically {@link GlobalPriorActivityProvider}).</p>
 *
 * <p><b>Planner-layer only.</b> This class imports {@link Activity} from
 * {@code db.entities}; it must not be used inside {@code sim/} or {@code combat/}.</p>
 */
public final class DbActivityProvider implements ActivityProvider {

    /**
     * Minimum average activity across week-turn buckets required to use the per-nation
     * distribution. Below this we have too little data to distinguish individual patterns
     * from noise, so we fall back to the global prior.
     */
    public static final double DEFAULT_MIN_AVERAGE_ACTIVITY = 0.01;

    /** Week-turn array length: 7 days × 12 turns/day. */
    private static final int WEEK_TURNS = 84;

    private final Map<Integer, Activity> activityByNationId;
    private final double minAverageActivity;
    private final ActivityProvider fallback;
    private final int simStartWeekTurn;

    /**
     * @param activityByNationId pre-loaded activity data keyed by nation ID; loaded once by
     *                            the planner before constructing {@link link.locutus.discord.sim.SimWorld}
     * @param simStartEpochMs     wall-clock epoch (ms UTC) at the start of the sim; used to
     *                            align sim turn offsets to the correct day-of-week / hour-of-day bucket
     * @param fallback            provider used when a nation's per-nation data is insufficient
     */
    public DbActivityProvider(
            Map<Integer, Activity> activityByNationId,
            long simStartEpochMs,
            ActivityProvider fallback
    ) {
        this(activityByNationId, simStartEpochMs, DEFAULT_MIN_AVERAGE_ACTIVITY, fallback);
    }

    /**
     * Convenience: uses {@link GlobalPriorActivityProvider} as the fallback.
     */
    public DbActivityProvider(Map<Integer, Activity> activityByNationId, long simStartEpochMs) {
        this(activityByNationId, simStartEpochMs, new GlobalPriorActivityProvider());
    }

    public DbActivityProvider(
            Map<Integer, Activity> activityByNationId,
            long simStartEpochMs,
            double minAverageActivity,
            ActivityProvider fallback
    ) {
        this.activityByNationId = Objects.requireNonNull(activityByNationId, "activityByNationId");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        if (minAverageActivity < 0.0 || minAverageActivity > 1.0) {
            throw new IllegalArgumentException("minAverageActivity must be in [0, 1], got: " + minAverageActivity);
        }
        this.minAverageActivity = minAverageActivity;
        this.simStartWeekTurn = weekTurnFromEpochMs(simStartEpochMs);
    }

    @Override
    public double activityAt(SimNation nation, int turn) {
        Activity activity = activityByNationId.get(nation.nationId());
        if (activity == null) {
            return fallback.activityAt(nation, turn);
        }
        if (activity.getAverageByWeekTurn() < minAverageActivity) {
            return fallback.activityAt(nation, turn);
        }
        double[] weekTurnArr = activity.getByWeekTurn();
        int idx = (simStartWeekTurn + turn) % WEEK_TURNS;
        return weekTurnArr[idx];
    }

    /**
     * Returns a snapshot-native planner adapter for this activity model.
     */
    public SnapshotActivityProvider asSnapshotProvider(double fallbackActivity) {
        if (fallbackActivity < 0.0 || fallbackActivity > 1.0) {
            throw new IllegalArgumentException("fallbackActivity must be in [0, 1], got: " + fallbackActivity);
        }
        return (snapshot, turn) -> {
            Activity activity = activityByNationId.get(snapshot.nationId());
            if (activity == null) {
                return fallbackActivity;
            }
            if (activity.getAverageByWeekTurn() < minAverageActivity) {
                return fallbackActivity;
            }
            double[] weekTurnArr = activity.getByWeekTurn();
            int idx = Math.floorMod(simStartWeekTurn + turn, WEEK_TURNS);
            return weekTurnArr[idx];
        };
    }

    /**
     * Loads activity data for a set of nation IDs from DBNation objects, using a fixed
     * lookback of {@code lookbackTurns} for historical data. Intended to be called once at
     * planner entry before constructing this provider.
     *
     * @param nations         the nations to load activity for
     * @param lookbackTurns   how many turns of history to include (e.g. 14*12 = two weeks)
     * @return map from nation ID to loaded Activity; nations with no data may be absent
     */
    public static Map<Integer, Activity> loadActivity(
            Iterable<link.locutus.discord.db.entities.DBNation> nations,
            long lookbackTurns
    ) {
        var cache = Activity.createCache((int) lookbackTurns);
        java.util.Map<Integer, Activity> result = new java.util.LinkedHashMap<>();
        for (link.locutus.discord.db.entities.DBNation nation : nations) {
            Activity a = cache.apply(nation);
            if (a != null) {
                result.put(nation.getNation_id(), a);
            }
        }
        return result;
    }

    private static int weekTurnFromEpochMs(long epochMs) {
        ZonedDateTime dt = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
        DayOfWeek day = dt.getDayOfWeek();
        int hourTurn = dt.getHour() / 2;
        return day.ordinal() * 12 + hourTurn;
    }
}

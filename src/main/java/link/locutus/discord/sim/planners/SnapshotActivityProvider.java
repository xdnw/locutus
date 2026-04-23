package link.locutus.discord.sim.planners;

/**
 * Planner-facing activity provider operating on immutable {@link DBNationSnapshot} data.
 *
 * <p>This interface parallels {@link link.locutus.discord.sim.ActivityProvider} but takes
 * a snapshot instead of a live {@code SimNation}, allowing the planner compilation path to
 * compute activity weights without constructing a {@code SimWorld}.</p>
 *
 * <p>For live-sim actor stepping, use {@code ActivityProvider} directly.
 * This interface is for compile-time planner inputs only.</p>
 */
@FunctionalInterface
public interface SnapshotActivityProvider {

    /** Constant baseline: 0.5 activity for all nations. */
    SnapshotActivityProvider BASELINE = (snapshot, turn) -> 0.5;

    /** Always active: 1.0 activity for all nations. */
    SnapshotActivityProvider ALWAYS_ACTIVE = (snapshot, turn) -> 1.0;

    /** Completely inactive: 0.0 activity for all nations. */
    SnapshotActivityProvider INACTIVE = (snapshot, turn) -> 0.0;

    /**
     * Returns the activity level [0,1] for the given nation snapshot at the given turn.
     *
     * @param snapshot the nation to evaluate
     * @param turn     current planning turn (0-indexed)
     * @return activity in [0, 1]
     */
    double activityForSnapshot(DBNationSnapshot snapshot, int turn);

    /**
     * Returns a provider that applies additive wartime activity uplift based on snapshot war state.
     *
     * <p>If the nation has any active wars (offensive, defensive, or active opponent IDs),
     * {@code upliftAmount} is added to the base activity, capped at 1.0. This preserves
     * the wartime uplift semantics from {@link link.locutus.discord.sim.SimTuning#wartimeActivityUplift()}
     * without requiring a live {@code SimWorld}.</p>
     *
     * @param upliftAmount additive activity uplift for wartime nations, typically from
     *                     {@link link.locutus.discord.sim.SimTuning#wartimeActivityUplift()}
     * @return a new provider with wartime uplift applied
     */
    default SnapshotActivityProvider withWartimeUplift(double upliftAmount) {
        if (upliftAmount == 0.0) {
            return this;
        }
        return (snapshot, turn) -> {
            double base = this.activityForSnapshot(snapshot, turn);
            return snapshot.hasActiveWars() ? Math.min(1.0, base + upliftAmount) : base;
        };
    }
}

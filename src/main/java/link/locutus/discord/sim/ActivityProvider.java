package link.locutus.discord.sim;

/**
 * Provides activity profile for a nation during simulation.
 * Activity affects action priority ordering and availability thresholds.
 * 
 * Activity values are normalized to [0, 1]:
 * - 0.0: completely inactive (will not act)
 * - 0.5: baseline activity (default player)
 * - 1.0: maximally active (always acts, no waiting)
 * 
 * Implementations may derive activity from:
 * - Historical player behavior during wars
 * - Time-of-day patterns
 * - Alliance-wide conventions
 * - Prior-game data skewed per nation
 */
public interface ActivityProvider {
    /**
     * Default: constant baseline activity (0.5).
     */
    ActivityProvider BASELINE = (nation, turn) -> 0.5;

    /**
     * No-op: constant zero activity. Nations never act.
     */
    ActivityProvider INACTIVE = (nation, turn) -> 0.0;

    /**
     * Maximum activity: nations always act.
     */
    ActivityProvider ALWAYS_ACTIVE = (nation, turn) -> 1.0;

    /**
     * Return the activity level for a nation at a given turn.
     * 
     * @param nation the nation to evaluate
     * @param turn the current simulation turn (0-indexed)
     * @return activity in [0, 1]
     */
    double activityAt(SimNation nation, int turn);

    /**
     * Return the wartime activity uplift for a nation that is currently in war.
      * Default: no provider-specific uplift; callers may layer {@link SimTuning#wartimeActivityUplift()} separately.
     * 
     * @param nation the nation in war
     * @param turn the current simulation turn
     * @return additive uplift in [0, 1]
     */
    default double wartimeUplift(SimNation nation, int turn) {
        return 0.0; // Will be augmented by SimTuning.wartimeActivityUplift
    }
}

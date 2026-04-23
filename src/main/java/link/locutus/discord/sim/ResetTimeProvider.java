package link.locutus.discord.sim;

/**
 * Provides the UTC reset hour for a nation during simulation.
 * 
 * This is a lightweight provider because reset time is typically static per nation.
 * It exists as a seam for future extensions (e.g., changing reset hour mid-sim if needed).
 */
public interface ResetTimeProvider {
    /**
     * Default: return the hour exactly as initialized on the nation.
     */
    ResetTimeProvider FROM_NATION = (nation, turn) -> nation.resetHourUtc();

    /**
     * Return the UTC reset hour (0-23) for a nation at a given turn.
     * 
     * @param nation the nation to query
     * @param turn the current simulation turn
     * @return UTC hour (0-23)
     */
    byte resetHourUtc(SimNation nation, int turn);
}

package link.locutus.discord.sim.planners;

/**
 * Injected filter used by {@link BlitzPlanner} to exclude illegal or friendly pairs from candidate
 * generation before any scoring happens.
 *
 * <p>The planner calls {@code isTreated(attackerId, defenderId)} and skips the pair if {@code true}.
 * An attacker and defender in the same alliance or in a protected treaty relationship should return
 * {@code true}. Nations with no treaty relationship return {@code false}.</p>
 *
 * <p>Implementations live in the {@code planners} layer and may read DB state. The sim layer never
 * imports this interface.</p>
 */
@FunctionalInterface
public interface TreatyProvider {

    /**
     * Returns {@code true} when the given attacker is prohibited from declaring war on the defender
     * due to treaty or alliance membership.
     */
    boolean isTreated(int attackerNationId, int defenderNationId);

    /** No treaty restrictions: all pairs are candidates. Used in tests and synthetic simulations. */
    TreatyProvider NONE = (a, d) -> false;

    /** Blocks any pair where both nations share the same alliance ID (same-AA protection). */
    static TreatyProvider sameAlliance(java.util.function.IntUnaryOperator nationToAlliance) {
        return (a, d) -> nationToAlliance.applyAsInt(a) == nationToAlliance.applyAsInt(d)
                && nationToAlliance.applyAsInt(a) != 0;
    }
}

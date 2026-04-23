package link.locutus.discord.sim;

import java.util.Set;

/**
 * Immutable context provided to an {@link Actor} for decision-making within a turn.
 * 
 * Caches per-turn information so actors don't need to repeatedly query the world.
 */
public final class DecisionContext {
    private final SimWorld world;
    private final int turn;
    private final Set<Integer> neighborNationsInRange;
    private final Objective objective;

    public DecisionContext(
            SimWorld world,
            int turn,
            Set<Integer> neighborNationsInRange,
            Objective objective
    ) {
        this.world = world;
        this.turn = turn;
        this.neighborNationsInRange = neighborNationsInRange;
        this.objective = objective;
    }

    public SimWorld world() {
        return world;
    }

    public int turn() {
        return turn;
    }

    /**
     * Nations in score range for potential declarations.
     * Does not include nations already at war.
     */
    public Set<Integer> neighborNationsInRange() {
        return neighborNationsInRange;
    }

    public Objective objective() {
        return objective;
    }
}

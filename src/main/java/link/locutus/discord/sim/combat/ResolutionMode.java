package link.locutus.discord.sim.combat;

public enum ResolutionMode {
    /** Probability-weighted mean over the four {@link link.locutus.discord.apiv1.enums.SuccessType}
     *  outcomes; casualty range collapsed to its midpoint. Reproducible and RNG-free. */
    DETERMINISTIC_EV,

    /** Sample success level from the odds vector, then sample casualties uniformly
     *  in [min, max] via a {@link RandomSource}. Reproducible for a given seed. */
    STOCHASTIC,

    /** Argmax success level, midpoint casualties. Useful for planner dry-runs. */
    MOST_LIKELY
}

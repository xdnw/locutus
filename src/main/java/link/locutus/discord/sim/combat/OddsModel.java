package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;

/**
 * Swappable odds model. Injecting this into {@link AttackResolver} keeps swappable odds
 * honored by combat truth, not only by planner-side scoring.
 *
 * <p>The default implementation, {@link #DEFAULT}, delegates to {@link OddsCalculator} and
 * therefore to {@link link.locutus.discord.util.PW#getOdds}. Alternative implementations
 * (e.g. learned per-attack-type adjustments, or test doubles that pin a specific
 * {@link link.locutus.discord.apiv1.enums.SuccessType}) replace the distribution without
 * needing to re-route every call site.
 */
@FunctionalInterface
public interface OddsModel {

    /**
     * Length-{@code SuccessType.values.length} distribution over success outcomes for the
     * given matchup. Must sum to 1 within floating-point tolerance and contain no negatives.
     */
    double[] odds(double attStrength, double defStrength, AttackType type);

    /** Wraps {@link OddsCalculator#odds}. */
    OddsModel DEFAULT = OddsCalculator::odds;
}

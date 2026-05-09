package link.locutus.discord.sim;

/**
 * Canonical strategic component language shared by opening rollout, exact validation, and
 * objective scoring.
 */
public interface StrategicEvaluationComponents {
    double immediateHarm();

    double selfExposure();

    double resourceSwing();

    double controlLeverage();

    /**
     * How much of the defender's resistance has been drained in this war (0→1 as resistance falls
     * from full to zero). Measures tactical transition propensity: a fully drained war frees the
     * attacker to pivot to new engagements. This is a current-state signal, not a force-window signal.
     */
    default double tacticalMomentum() {
        return 0d;
    }

    /**
     * Relative unit-advantage accumulated across ground, air, and naval domains (each domain
     * contributes a positive relative-gain fraction). Measures how much action-space the attacker
     * has opened up through unit attrition versus the defender's losses. Independent of resistance.
     */
    default double forceWindowAdvantage() {
        return 0d;
    }

    /**
     * Backward-compatible follow-on leverage seam for opening/objective callers.
     *
     * <p>This intentionally excludes raw resistance drain. Resistance remains available through
     * {@link #tacticalMomentum()} as a tactical-transition diagnostic, but follow-on leverage is
     * restricted to mechanics-facing force-window advantage until a dedicated timing/control owner
     * replaces this compatibility surface.</p>
     */
    default double futureWarLeverage() {
        return forceWindowAdvantage();
    }

    default double targetPressure() {
        return 0d;
    }
}

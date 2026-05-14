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
     * Declaration-only opportunity signal for rows that remain visible without realized tactical
     * leverage from an opening attack.
     */
    default double declarationReadiness() {
        return 0d;
    }

    /**
     * How much of the defender's resistance has been drained in this war (0→1 as resistance falls
     * from full to zero). Measures tactical transition propensity: a fully drained war frees the
     * attacker to pivot to new engagements. This is a current-state signal, not an action-space
     * quality signal.
     */
    default double tacticalMomentum() {
        return 0d;
    }

    /**
     * Positive change in projected combat option dominance across ground, air, and naval domains.
     * This is derived from current/projected unit state rather than relative attrition alone.
     */
    default double actionSpaceQuality() {
        return 0d;
    }

    /**
     * Positive change in the side's useful control/timing window. This separates attacks that
     * create a tenable follow-through window from attacks that merely drain resistance in a war
     * whose timing is already strategically empty or worsening.
     */
    default double timingWindowAdvantage() {
        return 0d;
    }

    /**
     * Follow-on leverage seam for opening/objective callers.
     *
     * <p>This intentionally excludes raw resistance drain. Resistance remains available through
     * {@link #tacticalMomentum()} as a tactical-transition diagnostic, but follow-on leverage is
     * restricted to mechanics-facing action-space quality and timing-window advantage.</p>
     */
    default double futureWarLeverage() {
        return actionSpaceQuality() + timingWindowAdvantage();
    }

    default double targetPressure() {
        return 0d;
    }
}

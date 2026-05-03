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

    double futureWarLeverage();

    default double targetPressure() {
        return 0d;
    }
}

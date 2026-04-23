package link.locutus.discord.sim;

/**
 * Objective-facing summary of a bounded opening rollout.
 */
public record OpeningMetricVector(
        double immediateHarm,
        double selfExposure,
        double resourceSwing,
        double controlLeverage,
        double futureWarLeverage
) {
    public static final OpeningMetricVector ZERO = new OpeningMetricVector(0d, 0d, 0d, 0d, 0d);
}
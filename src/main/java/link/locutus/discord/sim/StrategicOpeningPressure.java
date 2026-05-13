package link.locutus.discord.sim;

final class StrategicOpeningPressure {
    private StrategicOpeningPressure() {
    }

    static double capturableTargetPressure(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure
    ) {
        double pressure = positiveFinite(targetPressure);
        if (!(pressure > 0d)) {
            return 0d;
        }
        double progress = pressureCaptureProgress(
            immediateHarm,
            selfExposure,
            resourceSwing,
            controlLeverage,
            futureWarLeverage
        );
        if (!(progress > 0d)) {
            return 0d;
        }
        return pressure * (1d - Math.exp(-progress / pressure));
    }

    private static double pressureCaptureProgress(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage
    ) {
        double positiveProgress = (0.10d * positiveFinite(immediateHarm))
                + (0.05d * positiveFinite(resourceSwing))
                + (4.00d * positiveFinite(controlLeverage))
                + (3.00d * positiveFinite(futureWarLeverage));
        return Math.max(0d, positiveProgress - (0.35d * positiveFinite(selfExposure)));
    }

    private static double positiveFinite(double value) {
        return Double.isFinite(value) ? Math.max(0d, value) : 0d;
    }
}
package link.locutus.discord.sim;

/**
 * Objective-facing summary of a bounded opening rollout.
 */
public class OpeningMetricVector {
    public static final OpeningMetricVector ZERO = new OpeningMetricVector(0d, 0d, 0d, 0d, 0d);

    private final double immediateHarm;
    private final double selfExposure;
    private final double resourceSwing;
    private final double controlLeverage;
    private final double futureWarLeverage;

    public OpeningMetricVector(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage
    ) {
        this.immediateHarm = immediateHarm;
        this.selfExposure = selfExposure;
        this.resourceSwing = resourceSwing;
        this.controlLeverage = controlLeverage;
        this.futureWarLeverage = futureWarLeverage;
    }

    public double immediateHarm() {
        return immediateHarm;
    }

    public double selfExposure() {
        return selfExposure;
    }

    public double resourceSwing() {
        return resourceSwing;
    }

    public double controlLeverage() {
        return controlLeverage;
    }

    public double futureWarLeverage() {
        return futureWarLeverage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OpeningMetricVector that)) {
            return false;
        }
        return Double.compare(immediateHarm(), that.immediateHarm()) == 0
                && Double.compare(selfExposure(), that.selfExposure()) == 0
                && Double.compare(resourceSwing(), that.resourceSwing()) == 0
                && Double.compare(controlLeverage(), that.controlLeverage()) == 0
                && Double.compare(futureWarLeverage(), that.futureWarLeverage()) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(immediateHarm());
        result = 31 * result + Double.hashCode(selfExposure());
        result = 31 * result + Double.hashCode(resourceSwing());
        result = 31 * result + Double.hashCode(controlLeverage());
        result = 31 * result + Double.hashCode(futureWarLeverage());
        return result;
    }

    @Override
    public String toString() {
        return "OpeningMetricVector["
                + "immediateHarm=" + immediateHarm()
                + ", selfExposure=" + selfExposure()
                + ", resourceSwing=" + resourceSwing()
                + ", controlLeverage=" + controlLeverage()
                + ", futureWarLeverage=" + futureWarLeverage()
                + ']';
    }

    /**
     * Caller-owned metric scratch for hot planner rollout.
     *
     * <p>The base type remains an immutable value for boundary callers; this subtype is used
     * only where a read-only {@link OpeningMetricVector} view is needed without allocating a
     * new value for every projected attack.</p>
     */
    public static final class Mutable extends OpeningMetricVector {
        private double immediateHarm;
        private double selfExposure;
        private double resourceSwing;
        private double controlLeverage;
        private double futureWarLeverage;

        public Mutable() {
            super(0d, 0d, 0d, 0d, 0d);
        }

        public void clear() {
            set(0d, 0d, 0d, 0d, 0d);
        }

        public void copyFrom(OpeningMetricVector source) {
            set(
                    source.immediateHarm(),
                    source.selfExposure(),
                    source.resourceSwing(),
                    source.controlLeverage(),
                    source.futureWarLeverage()
            );
        }

        public void set(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double futureWarLeverage
        ) {
            this.immediateHarm = immediateHarm;
            this.selfExposure = selfExposure;
            this.resourceSwing = resourceSwing;
            this.controlLeverage = controlLeverage;
            this.futureWarLeverage = futureWarLeverage;
        }

        @Override
        public double immediateHarm() {
            return immediateHarm;
        }

        @Override
        public double selfExposure() {
            return selfExposure;
        }

        @Override
        public double resourceSwing() {
            return resourceSwing;
        }

        @Override
        public double controlLeverage() {
            return controlLeverage;
        }

        @Override
        public double futureWarLeverage() {
            return futureWarLeverage;
        }
    }
}

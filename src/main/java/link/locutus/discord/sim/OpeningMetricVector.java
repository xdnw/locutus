package link.locutus.discord.sim;

/**
 * Objective-facing summary of a bounded opening rollout.
 */
public class OpeningMetricVector implements StrategicEvaluationComponents {
    public static final OpeningMetricVector ZERO = new OpeningMetricVector(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d);

    private final double immediateHarm;
    private final double selfExposure;
    private final double resourceSwing;
    private final double controlLeverage;
    private final double declarationReadiness;
    private final double tacticalMomentum;
    private final double actionSpaceQuality;
    private final double timingWindowAdvantage;
    private final double targetPressure;

    public OpeningMetricVector(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double tacticalMomentum,
            double actionSpaceQuality
    ) {
        this(immediateHarm, selfExposure, resourceSwing, controlLeverage, tacticalMomentum, actionSpaceQuality, 0d, 0d, 0d);
    }

    public OpeningMetricVector(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double tacticalMomentum,
            double actionSpaceQuality,
            double targetPressure
    ) {
        this(immediateHarm, selfExposure, resourceSwing, controlLeverage, tacticalMomentum, actionSpaceQuality, 0d, 0d, targetPressure);
    }

    public OpeningMetricVector(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double tacticalMomentum,
            double actionSpaceQuality,
            double timingWindowAdvantage,
            double targetPressure
    ) {
        this(
            immediateHarm,
            selfExposure,
            resourceSwing,
            controlLeverage,
            tacticalMomentum,
            actionSpaceQuality,
            timingWindowAdvantage,
            0d,
            targetPressure
        );
        }

        public OpeningMetricVector(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double tacticalMomentum,
            double actionSpaceQuality,
            double timingWindowAdvantage,
            double declarationReadiness,
            double targetPressure
        ) {
        this.immediateHarm = immediateHarm;
        this.selfExposure = selfExposure;
        this.resourceSwing = resourceSwing;
        this.controlLeverage = controlLeverage;
        this.declarationReadiness = declarationReadiness;
        this.tacticalMomentum = tacticalMomentum;
        this.actionSpaceQuality = actionSpaceQuality;
        this.timingWindowAdvantage = timingWindowAdvantage;
        this.targetPressure = targetPressure;
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

    @Override
    public double declarationReadiness() {
        return declarationReadiness;
    }

    @Override
    public double tacticalMomentum() {
        return tacticalMomentum;
    }

    @Override
    public double actionSpaceQuality() {
        return actionSpaceQuality;
    }

    @Override
    public double timingWindowAdvantage() {
        return timingWindowAdvantage;
    }

    public double targetPressure() {
        return targetPressure;
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
            && Double.compare(declarationReadiness(), that.declarationReadiness()) == 0
                && Double.compare(tacticalMomentum(), that.tacticalMomentum()) == 0
                && Double.compare(actionSpaceQuality(), that.actionSpaceQuality()) == 0
                && Double.compare(timingWindowAdvantage(), that.timingWindowAdvantage()) == 0
                && Double.compare(targetPressure(), that.targetPressure()) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(immediateHarm());
        result = 31 * result + Double.hashCode(selfExposure());
        result = 31 * result + Double.hashCode(resourceSwing());
        result = 31 * result + Double.hashCode(controlLeverage());
        result = 31 * result + Double.hashCode(declarationReadiness());
        result = 31 * result + Double.hashCode(tacticalMomentum());
        result = 31 * result + Double.hashCode(actionSpaceQuality());
        result = 31 * result + Double.hashCode(timingWindowAdvantage());
        result = 31 * result + Double.hashCode(targetPressure());
        return result;
    }

    @Override
    public String toString() {
        return "OpeningMetricVector["
                + "immediateHarm=" + immediateHarm()
                + ", selfExposure=" + selfExposure()
                + ", resourceSwing=" + resourceSwing()
                + ", controlLeverage=" + controlLeverage()
                + ", declarationReadiness=" + declarationReadiness()
                + ", tacticalMomentum=" + tacticalMomentum()
                + ", actionSpaceQuality=" + actionSpaceQuality()
                + ", timingWindowAdvantage=" + timingWindowAdvantage()
                + ", targetPressure=" + targetPressure()
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
        private double declarationReadiness;
        private double tacticalMomentum;
        private double actionSpaceQuality;
        private double timingWindowAdvantage;
        private double targetPressure;

        public Mutable() {
            super(0d, 0d, 0d, 0d, 0d, 0d);
        }

        public void clear() {
            set(0d, 0d, 0d, 0d, 0d, 0d);
        }

        public void copyFrom(OpeningMetricVector source) {
            set(
                    source.immediateHarm(),
                    source.selfExposure(),
                    source.resourceSwing(),
                    source.controlLeverage(),
                    source.declarationReadiness(),
                    source.tacticalMomentum(),
                    source.actionSpaceQuality(),
                    source.timingWindowAdvantage(),
                    source.targetPressure()
            );
        }

        public void set(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double tacticalMomentum,
                double actionSpaceQuality
        ) {
            set(immediateHarm, selfExposure, resourceSwing, controlLeverage, 0d, tacticalMomentum, actionSpaceQuality, 0d, 0d);
        }

        public void set(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double tacticalMomentum,
                double actionSpaceQuality,
                double targetPressure
        ) {
            set(immediateHarm, selfExposure, resourceSwing, controlLeverage, 0d, tacticalMomentum, actionSpaceQuality, 0d, targetPressure);
        }

        public void set(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double tacticalMomentum,
                double actionSpaceQuality,
                double timingWindowAdvantage,
                double targetPressure
        ) {
            set(immediateHarm, selfExposure, resourceSwing, controlLeverage, 0d, tacticalMomentum, actionSpaceQuality, timingWindowAdvantage, targetPressure);
        }

        public void set(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double declarationReadiness,
                double tacticalMomentum,
                double actionSpaceQuality,
                double timingWindowAdvantage,
                double targetPressure
        ) {
            this.immediateHarm = immediateHarm;
            this.selfExposure = selfExposure;
            this.resourceSwing = resourceSwing;
            this.controlLeverage = controlLeverage;
            this.declarationReadiness = declarationReadiness;
            this.tacticalMomentum = tacticalMomentum;
            this.actionSpaceQuality = actionSpaceQuality;
            this.timingWindowAdvantage = timingWindowAdvantage;
            this.targetPressure = targetPressure;
        }

        @Override
        public double declarationReadiness() {
            return declarationReadiness;
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
        public double tacticalMomentum() {
            return tacticalMomentum;
        }

        @Override
        public double actionSpaceQuality() {
            return actionSpaceQuality;
        }

        @Override
        public double timingWindowAdvantage() {
            return timingWindowAdvantage;
        }

        @Override
        public double targetPressure() {
            return targetPressure;
        }
    }
}

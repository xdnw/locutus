package link.locutus.discord.sim;

/**
 * Damage/resource summary of a bounded opening rollout used by local shell selection.
 */
public class OpeningMetricVector {
    public static final OpeningMetricVector ZERO = new OpeningMetricVector(0d, 0d);

    private final double immediateHarm;
    private final double resourceSwing;

    public OpeningMetricVector(
            double immediateHarm,
            double resourceSwing
        ) {
        this.immediateHarm = immediateHarm;
        this.resourceSwing = resourceSwing;
    }

    public double immediateHarm() {
        return immediateHarm;
    }

    public double resourceSwing() {
        return resourceSwing;
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
                && Double.compare(resourceSwing(), that.resourceSwing()) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(immediateHarm());
        result = 31 * result + Double.hashCode(resourceSwing());
        return result;
    }

    @Override
    public String toString() {
        return "OpeningMetricVector["
                + "immediateHarm=" + immediateHarm()
                + ", resourceSwing=" + resourceSwing()
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
        private double resourceSwing;

        public Mutable() {
            super(0d, 0d);
        }

        public void clear() {
            set(0d, 0d);
        }

        public void copyFrom(OpeningMetricVector source) {
            set(
                    source.immediateHarm(),
                    source.resourceSwing()
            );
        }

        public void set(
                double immediateHarm,
                double resourceSwing
        ) {
            this.immediateHarm = immediateHarm;
            this.resourceSwing = resourceSwing;
        }

        public double immediateHarm() {
            return immediateHarm;
        }

        public double resourceSwing() {
            return resourceSwing;
        }
    }
}

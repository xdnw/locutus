package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

import java.util.Arrays;

/**
 * Optional primitive-backed storage for retained candidate-edge component values.
 *
 * <p>The active objective owns which component arrays exist. Callers can store and swap all
 * retained metrics through this helper without branching on a separate all-or-nothing flag.</p>
 */
final class CandidateEdgeComponents {
    private float[] immediateHarms;
    private float[] selfExposures;
    private float[] resourceSwings;
    private float[] controlLeverages;
    private float[] futureWarLeverages;

    CandidateEdgeComponents(int capacity, CandidateEdgeComponentPolicy policy) {
        CandidateEdgeComponentPolicy effectivePolicy = policy == null ? CandidateEdgeComponentPolicy.none() : policy;
        int effectiveCapacity = Math.max(0, capacity);
        immediateHarms = effectivePolicy.retainImmediateHarm() ? new float[effectiveCapacity] : null;
        selfExposures = effectivePolicy.retainSelfExposure() ? new float[effectiveCapacity] : null;
        resourceSwings = effectivePolicy.retainResourceSwing() ? new float[effectiveCapacity] : null;
        controlLeverages = effectivePolicy.retainControlLeverage() ? new float[effectiveCapacity] : null;
        futureWarLeverages = effectivePolicy.retainFutureWarLeverage() ? new float[effectiveCapacity] : null;
    }

    boolean retainsImmediateHarm() {
        return immediateHarms != null;
    }

    boolean retainsSelfExposure() {
        return selfExposures != null;
    }

    boolean retainsResourceSwing() {
        return resourceSwings != null;
    }

    boolean retainsControlLeverage() {
        return controlLeverages != null;
    }

    boolean retainsFutureWarLeverage() {
        return futureWarLeverages != null;
    }

    void ensureCapacity(int needed) {
        if (immediateHarms != null && immediateHarms.length < needed) {
            immediateHarms = Arrays.copyOf(immediateHarms, needed);
        }
        if (selfExposures != null && selfExposures.length < needed) {
            selfExposures = Arrays.copyOf(selfExposures, needed);
        }
        if (resourceSwings != null && resourceSwings.length < needed) {
            resourceSwings = Arrays.copyOf(resourceSwings, needed);
        }
        if (controlLeverages != null && controlLeverages.length < needed) {
            controlLeverages = Arrays.copyOf(controlLeverages, needed);
        }
        if (futureWarLeverages != null && futureWarLeverages.length < needed) {
            futureWarLeverages = Arrays.copyOf(futureWarLeverages, needed);
        }
    }

    void set(
            int index,
            float immediateHarm,
            float selfExposure,
            float resourceSwing,
            float controlLeverage,
            float futureWarLeverage
    ) {
        if (immediateHarms != null) {
            immediateHarms[index] = immediateHarm;
        }
        if (selfExposures != null) {
            selfExposures[index] = selfExposure;
        }
        if (resourceSwings != null) {
            resourceSwings[index] = resourceSwing;
        }
        if (controlLeverages != null) {
            controlLeverages[index] = controlLeverage;
        }
        if (futureWarLeverages != null) {
            futureWarLeverages[index] = futureWarLeverage;
        }
    }

    CandidateEdgeComponents deepCopy() {
        CandidateEdgeComponents copy = new CandidateEdgeComponents(0, CandidateEdgeComponentPolicy.none());
        copy.immediateHarms = immediateHarms == null ? null : Arrays.copyOf(immediateHarms, immediateHarms.length);
        copy.selfExposures = selfExposures == null ? null : Arrays.copyOf(selfExposures, selfExposures.length);
        copy.resourceSwings = resourceSwings == null ? null : Arrays.copyOf(resourceSwings, resourceSwings.length);
        copy.controlLeverages = controlLeverages == null ? null : Arrays.copyOf(controlLeverages, controlLeverages.length);
        copy.futureWarLeverages = futureWarLeverages == null ? null : Arrays.copyOf(futureWarLeverages, futureWarLeverages.length);
        return copy;
    }

    /**
     * Multiplies all retained component values for a single edge by {@code factor}. Used by the
     * long-horizon optimizer to rebuild candidate edge components from a projected mid-horizon
     * {@code ProjectionState} snapshot rather than only scaling the scalar score.
     */
    void scale(int index, float factor) {
        if (immediateHarms != null) {
            immediateHarms[index] *= factor;
        }
        if (selfExposures != null) {
            selfExposures[index] *= factor;
        }
        if (resourceSwings != null) {
            resourceSwings[index] *= factor;
        }
        if (controlLeverages != null) {
            controlLeverages[index] *= factor;
        }
        if (futureWarLeverages != null) {
            futureWarLeverages[index] *= factor;
        }
    }

    void swap(int lhs, int rhs) {
        if (immediateHarms != null) {
            float swap = immediateHarms[lhs];
            immediateHarms[lhs] = immediateHarms[rhs];
            immediateHarms[rhs] = swap;
        }
        if (selfExposures != null) {
            float swap = selfExposures[lhs];
            selfExposures[lhs] = selfExposures[rhs];
            selfExposures[rhs] = swap;
        }
        if (resourceSwings != null) {
            float swap = resourceSwings[lhs];
            resourceSwings[lhs] = resourceSwings[rhs];
            resourceSwings[rhs] = swap;
        }
        if (controlLeverages != null) {
            float swap = controlLeverages[lhs];
            controlLeverages[lhs] = controlLeverages[rhs];
            controlLeverages[rhs] = swap;
        }
        if (futureWarLeverages != null) {
            float swap = futureWarLeverages[lhs];
            futureWarLeverages[lhs] = futureWarLeverages[rhs];
            futureWarLeverages[rhs] = swap;
        }
    }

    float immediateHarm(int index) {
        requireRetained(immediateHarms, "immediateHarm");
        return immediateHarms[index];
    }

    float selfExposure(int index) {
        requireRetained(selfExposures, "selfExposure");
        return selfExposures[index];
    }

    float resourceSwing(int index) {
        requireRetained(resourceSwings, "resourceSwing");
        return resourceSwings[index];
    }

    float controlLeverage(int index) {
        requireRetained(controlLeverages, "controlLeverage");
        return controlLeverages[index];
    }

    float futureWarLeverage(int index) {
        requireRetained(futureWarLeverages, "futureWarLeverage");
        return futureWarLeverages[index];
    }

    private static void requireRetained(float[] values, String name) {
        if (values == null) {
            throw new IllegalStateException(name + " was not retained for this candidate table");
        }
    }
}

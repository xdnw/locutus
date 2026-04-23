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

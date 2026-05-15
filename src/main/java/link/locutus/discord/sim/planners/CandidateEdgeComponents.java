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

    CandidateEdgeComponents(int capacity, CandidateEdgeComponentPolicy policy) {
        CandidateEdgeComponentPolicy effectivePolicy = policy == null ? CandidateEdgeComponentPolicy.none() : policy;
        int effectiveCapacity = Math.max(0, capacity);
        immediateHarms = effectivePolicy.retainImmediateHarm() ? new float[effectiveCapacity] : null;
        selfExposures = effectivePolicy.retainSelfExposure() ? new float[effectiveCapacity] : null;
        resourceSwings = effectivePolicy.retainResourceSwing() ? new float[effectiveCapacity] : null;
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
    }

    void set(
            int index,
            float immediateHarm,
            float selfExposure,
            float resourceSwing
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
    }

    CandidateEdgeComponents deepCopy() {
        CandidateEdgeComponents copy = new CandidateEdgeComponents(0, CandidateEdgeComponentPolicy.none());
        copy.immediateHarms = immediateHarms == null ? null : Arrays.copyOf(immediateHarms, immediateHarms.length);
        copy.selfExposures = selfExposures == null ? null : Arrays.copyOf(selfExposures, selfExposures.length);
        copy.resourceSwings = resourceSwings == null ? null : Arrays.copyOf(resourceSwings, resourceSwings.length);
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

    private static void requireRetained(float[] values, String name) {
        if (values == null) {
            throw new IllegalStateException(name + " was not retained for this candidate table");
        }
    }
}

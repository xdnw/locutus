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

    CandidateEdgeComponents(int capacity, CandidateEdgeComponentPolicy policy) {
        CandidateEdgeComponentPolicy effectivePolicy = policy == null ? CandidateEdgeComponentPolicy.none() : policy;
        int effectiveCapacity = Math.max(0, capacity);
        immediateHarms = effectivePolicy.retainImmediateHarm() ? new float[effectiveCapacity] : null;
    }

    boolean retainsImmediateHarm() {
        return immediateHarms != null;
    }

    void ensureCapacity(int needed) {
        if (immediateHarms != null && immediateHarms.length < needed) {
            immediateHarms = Arrays.copyOf(immediateHarms, needed);
        }
    }

    void set(
            int index,
            float immediateHarm
    ) {
        if (immediateHarms != null) {
            immediateHarms[index] = immediateHarm;
        }
    }

    CandidateEdgeComponents deepCopy() {
        CandidateEdgeComponents copy = new CandidateEdgeComponents(0, CandidateEdgeComponentPolicy.none());
        copy.immediateHarms = immediateHarms == null ? null : Arrays.copyOf(immediateHarms, immediateHarms.length);
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
    }

    void swap(int lhs, int rhs) {
        if (immediateHarms != null) {
            float swap = immediateHarms[lhs];
            immediateHarms[lhs] = immediateHarms[rhs];
            immediateHarms[rhs] = swap;
        }
    }

    float immediateHarm(int index) {
        requireRetained(immediateHarms, "immediateHarm");
        return immediateHarms[index];
    }

    private static void requireRetained(float[] values, String name) {
        if (values == null) {
            throw new IllegalStateException(name + " was not retained for this candidate table");
        }
    }
}

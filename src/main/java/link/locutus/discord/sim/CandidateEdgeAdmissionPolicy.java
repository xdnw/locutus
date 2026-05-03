package link.locutus.discord.sim;

/**
 * Declares how aggressively planner candidate generation may prune edges before scoring.
 *
 * <p>The policy lives in the sim layer so objectives can relax pruning for specialist
 * strategies without embedding that rule in planner code.</p>
 */
public record CandidateEdgeAdmissionPolicy(
        double minimumViabilityProbe,
        boolean allowLegalSpecialistFallback,
        boolean admitPositiveOpeningBaseline
) {

    public static final double DEFAULT_MINIMUM_VIABILITY_PROBE = 0.15d;
    public static final CandidateEdgeAdmissionPolicy DEFAULT = new CandidateEdgeAdmissionPolicy(DEFAULT_MINIMUM_VIABILITY_PROBE, false, false);
    public static final CandidateEdgeAdmissionPolicy LOW_PROBE_SPECIALISTS = new CandidateEdgeAdmissionPolicy(DEFAULT_MINIMUM_VIABILITY_PROBE, true, false);
    public static final CandidateEdgeAdmissionPolicy POSITIVE_OPENING_BASELINE = new CandidateEdgeAdmissionPolicy(DEFAULT_MINIMUM_VIABILITY_PROBE, false, true);

    public CandidateEdgeAdmissionPolicy(double minimumViabilityProbe) {
        this(minimumViabilityProbe, false, false);
    }

    public CandidateEdgeAdmissionPolicy(double minimumViabilityProbe, boolean allowLegalSpecialistFallback) {
        this(minimumViabilityProbe, allowLegalSpecialistFallback, false);
    }

    public static CandidateEdgeAdmissionPolicy defaultPolicy() {
        return DEFAULT;
    }

    public static CandidateEdgeAdmissionPolicy lowProbeSpecialists() {
        return LOW_PROBE_SPECIALISTS;
    }

    public static CandidateEdgeAdmissionPolicy positiveOpeningBaseline() {
        return POSITIVE_OPENING_BASELINE;
    }

    public CandidateEdgeAdmissionPolicy {
        if (!Double.isFinite(minimumViabilityProbe) || minimumViabilityProbe < 0.0d || minimumViabilityProbe > 1.0d) {
            throw new IllegalArgumentException("minimumViabilityProbe must be finite and in [0, 1]");
        }
    }
}

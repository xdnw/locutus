package link.locutus.discord.sim;

/**
 * Declares which per-edge diagnostic components a planner objective wants retained.
 *
 * <p>The policy lives in the sim layer so objective implementations can name their retained
 * opening-value breakdowns without depending on planner package internals.</p>
 */
public record CandidateEdgeComponentPolicy(
        boolean retainImmediateHarm,
        boolean retainSelfExposure,
        boolean retainResourceSwing,
        boolean retainControlLeverage,
        boolean retainFutureWarLeverage
) {
    public static final CandidateEdgeComponentPolicy NONE = new CandidateEdgeComponentPolicy(false, false, false, false, false);
    public static final CandidateEdgeComponentPolicy HARM_EXPOSURE_ONLY = new CandidateEdgeComponentPolicy(true, true, false, false, false);

    public static CandidateEdgeComponentPolicy none() {
        return NONE;
    }

    public static CandidateEdgeComponentPolicy harmExposureOnly() {
        return HARM_EXPOSURE_ONLY;
    }

    public boolean retainsAny() {
        return retainImmediateHarm
                || retainSelfExposure
                || retainResourceSwing
                || retainControlLeverage
                || retainFutureWarLeverage;
    }
}
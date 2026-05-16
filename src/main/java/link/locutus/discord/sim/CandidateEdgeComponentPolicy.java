package link.locutus.discord.sim;

/**
 * Declares which per-edge diagnostic components a planner objective wants retained.
 *
 * <p>The policy lives in the sim layer so objective implementations can name their retained
 * opening-value breakdowns without depending on planner package internals.</p>
 */
public record CandidateEdgeComponentPolicy(
        boolean retainImmediateHarm
) {
    public static final CandidateEdgeComponentPolicy NONE = new CandidateEdgeComponentPolicy(false);
    public static final CandidateEdgeComponentPolicy HARM_ONLY = new CandidateEdgeComponentPolicy(true);

    public static CandidateEdgeComponentPolicy none() {
        return NONE;
    }

    public static CandidateEdgeComponentPolicy harmOnly() {
        return HARM_ONLY;
    }

    public boolean retainsAny() {
        return retainImmediateHarm;
    }
}

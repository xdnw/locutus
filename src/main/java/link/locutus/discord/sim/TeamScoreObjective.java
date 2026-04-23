package link.locutus.discord.sim;

/**
 * Objective specialization that can score a planner-local team score surface.
 */
public interface TeamScoreObjective extends Objective {
    double scoreTerminal(TeamScoreView view, int teamId);

    /**
     * Scores a bounded opening rollout directly from its retained planner metrics.
     */
    double scoreOpening(OpeningMetricVector metrics, int teamId);

    default CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.none();
    }

    /**
     * Returns the minimum probe required for a candidate edge to be admitted before scoring.
     *
     * <p>Specialist objectives can lower this floor to keep low-probe edges that still carry
     * objective value, while damage-style objectives can rely on the default pruning floor.</p>
     */
    default CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.defaultPolicy();
    }

    @Override
    default double scoreTerminal(SimWorld world, int teamId) {
        return scoreTerminal(TeamScoreView.of(world), teamId);
    }
}
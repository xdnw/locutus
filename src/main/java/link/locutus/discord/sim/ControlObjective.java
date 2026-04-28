package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes control flags and follow-up war leverage. */
final class ControlObjective implements TeamScoreObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.lowProbeSpecialists();
    }

    @Override
    public double scoreOpening(OpeningMetricVector metrics, int teamId) {
        return (4.0d * metrics.controlLeverage())
                + (3.0d * metrics.futureWarLeverage())
                + (0.20d * metrics.immediateHarm())
                - (0.35d * metrics.selfExposure());
    }

    @Override
    public double scoreTerminal(TeamScoreView view, int teamId) {
        ScoreTotals totals = ScoreTotals.of(view, teamId);
        double score = (0.20d * (totals.ownScore() - totals.enemyScore()));
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.controlScoreForTeam(teamId);
        }
        return score;
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }
}
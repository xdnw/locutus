package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that maximizes damage dealt while ignoring own losses. */
final class DamageDealtObjective implements TeamScoreObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, false, false, false, false);
    }

    @Override
    public double scoreOpening(OpeningMetricVector metrics, int teamId) {
        return metrics.immediateHarm();
    }

    @Override
    public double scoreTerminal(TeamScoreView view, int teamId) {
        ScoreTotals totals = ScoreTotals.of(view, teamId);
        return -totals.enemyScore();
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }
}
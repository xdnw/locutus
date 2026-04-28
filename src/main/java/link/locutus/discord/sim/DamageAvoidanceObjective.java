package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that favors low-exposure declares while still requiring useful damage. */
final class DamageAvoidanceObjective implements TeamScoreObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, false, false);
    }

    @Override
    public double scoreOpening(OpeningMetricVector metrics, int teamId) {
        return (0.35d * metrics.immediateHarm()) - metrics.selfExposure();
    }

    @Override
    public double scoreTerminal(TeamScoreView view, int teamId) {
        ScoreTotals totals = ScoreTotals.of(view, teamId);
        return totals.ownScore() - (0.35d * totals.enemyScore());
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }
}
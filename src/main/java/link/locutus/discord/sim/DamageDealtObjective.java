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
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = -totals.enemyValue();
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.activeWarStrategicScoreForTeam(teamId, 1.0d, 1.0d);
            score += controlView.controlRegimeScoreForTeam(teamId);
        }
        return score;
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }
}
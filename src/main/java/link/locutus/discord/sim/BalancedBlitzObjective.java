package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that blends damage, exposure, control, resources, and follow-up leverage. */
final class BalancedBlitzObjective implements TeamScoreObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, true, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.lowProbeSpecialists();
    }

    @Override
    public double scoreOpening(OpeningMetricVector metrics, int teamId) {
        return metrics.immediateHarm()
                - (0.75d * metrics.selfExposure())
                + (1.50d * metrics.controlLeverage())
                + (1.00d * metrics.futureWarLeverage())
                + (1.00d * metrics.targetPressure())
                + (0.000001d * metrics.resourceSwing());
    }

    @Override
    public double scoreTerminal(TeamScoreView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = totals.ownValue() - totals.enemyValue();
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.controlScoreForTeam(teamId);
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

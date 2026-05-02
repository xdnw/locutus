package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes control flags and follow-up war leverage. */
final class ControlObjective implements StrategicObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.lowProbeSpecialists();
    }

    @Override
    public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return (4.0d * metrics.controlLeverage())
                + (3.0d * metrics.futureWarLeverage())
                + (4.0d * metrics.targetPressure())
                + (0.20d * metrics.immediateHarm())
                - (0.35d * metrics.selfExposure());
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = (0.20d * (totals.ownValue() - totals.enemyValue()));
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.controlScoreForTeam(teamId);
            score += controlView.activeWarStrategicScoreForTeam(teamId, 4.0d, 3.0d);
            score += 1.5d * controlView.controlRegimeScoreForTeam(teamId);
        }
        return score;
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }
}

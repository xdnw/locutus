package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that favors low-exposure declares while still requiring useful damage. */
final class DamageAvoidanceObjective implements StrategicObjective {
    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, false, false);
    }

    @Override
    public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return (0.35d * metrics.immediateHarm()) - metrics.selfExposure();
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = totals.ownValue() - (0.35d * totals.enemyValue());
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.controlCompositeScoreForTeam(
                    teamId,
                    new TeamWarControlView.ControlComponentWeights(0.35d, 0.35d, 0.0d, 0.35d, 0.35d, 0.35d)
            );
        }
        return score;
    }

    @Override
    public boolean usesWarSlotDenial() {
        return true;
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }

}

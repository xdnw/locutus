package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that maximizes damage dealt while ignoring own losses. */
final class DamageDealtObjective implements StrategicObjective {
    private static final StrategicControlReducer.ControlWeights TERMINAL_CONTROL_WEIGHTS =
            new StrategicControlReducer.ControlWeights(0.0d, 1.0d, 0.0d, 1.0d, 1.0d, 1.0d);

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, false, false, false, false);
    }

    @Override
    public double scoreOpening(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure,
            int teamId
    ) {
        return immediateHarm;
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = -totals.enemyValue();
        if (view instanceof TeamWarControlView controlView) {
            score += StrategicControlReducer.score(controlView, teamId, TERMINAL_CONTROL_WEIGHTS);
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

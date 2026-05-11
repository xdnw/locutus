package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that blends damage, exposure, control, resources, and follow-up leverage. */
final class BalancedBlitzObjective implements StrategicObjective {
    private static final StrategicControlReducer.ControlWeights TERMINAL_CONTROL_WEIGHTS =
            new StrategicControlReducer.ControlWeights(1.0d, 1.0d, 0.0d, 1.0d, 1.0d, 1.0d);

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, true, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.positiveOpeningBaseline();
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
        return immediateHarm
            - (0.75d * selfExposure)
            + (1.50d * controlLeverage)
            + futureWarLeverage
            + targetPressure
            + (0.000001d * resourceSwing);
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = totals.ownValue() - totals.enemyValue();
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

package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes control flags and follow-up war leverage. */
final class ControlObjective implements StrategicObjective {
    private static final StrategicControlReducer.ControlWeights TERMINAL_CONTROL_WEIGHTS =
            new StrategicControlReducer.ControlWeights(1.0d, 4.0d, 0.0d, 3.0d, 1.5d, 1.5d);

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return new CandidateEdgeAdmissionPolicy(
                CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                true,
                true
        );
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
        double effectiveResourceSwing = Math.max(0d, resourceSwing);
        double effectiveTargetPressure = StrategicOpeningPressure.capturableTargetPressure(
            immediateHarm,
            selfExposure,
            effectiveResourceSwing,
            controlLeverage,
            futureWarLeverage,
            targetPressure
        );
        return (4.0d * controlLeverage)
            + (3.0d * futureWarLeverage)
            + (4.0d * effectiveTargetPressure)
            + (0.05d * effectiveResourceSwing)
            + (0.10d * immediateHarm)
            - (0.35d * selfExposure);
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = (0.20d * (totals.ownValue() - totals.enemyValue()));
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

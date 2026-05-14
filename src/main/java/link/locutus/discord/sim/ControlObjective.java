package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes durable control, slot denial, and follow-through leverage. */
final class ControlObjective implements StrategicObjective {
    private static final StrategicControlReducer.ControlWeights TERMINAL_CONTROL_WEIGHTS =
            new StrategicControlReducer.ControlWeights(0.0d, 0.0d, 0.0d, 0.0d, 1.5d, 1.5d);

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, true, true);
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return new CandidateEdgeAdmissionPolicy(
                CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                true,
                false
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
        return scoreOpening(immediateHarm, selfExposure, resourceSwing, controlLeverage, 0d, futureWarLeverage, targetPressure);
    }

    @Override
    public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return scoreOpening(
                metrics.immediateHarm(),
                metrics.selfExposure(),
                metrics.resourceSwing(),
                metrics.controlLeverage(),
                metrics.tacticalMomentum(),
                metrics.futureWarLeverage(),
                metrics.targetPressure()
        );
    }

    private static double scoreOpening(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double tacticalMomentum,
            double futureWarLeverage,
            double targetPressure
    ) {
        double effectiveResourceSwing = Math.max(0d, resourceSwing);
        double effectiveTargetPressure = StrategicOpeningPressure.capturableTargetPressure(
            immediateHarm,
            selfExposure,
            effectiveResourceSwing,
            controlLeverage,
            futureWarLeverage + Math.max(0d, tacticalMomentum),
            targetPressure
        );
        return (3.0d * futureWarLeverage)
            + (1.5d * Math.max(0d, tacticalMomentum))
            + (4.0d * effectiveTargetPressure)
            + (0.10d * immediateHarm)
            + (0.02d * effectiveResourceSwing)
            - (0.85d * selfExposure);
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        if (view instanceof TeamWarControlView controlView) {
            return StrategicControlReducer.score(controlView, teamId, TERMINAL_CONTROL_WEIGHTS);
        }
        return 0d;
    }

    @Override
    public double scoreTerminalComparison(StrategicValueView view, int teamId, int opposingTeamId) {
        return scoreTerminal(view, teamId) - scoreTerminal(view, opposingTeamId);
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

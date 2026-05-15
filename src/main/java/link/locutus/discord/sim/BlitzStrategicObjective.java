package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes opening leverage while reading terminal value from projected strategic totals. */
final class BlitzStrategicObjective implements StrategicObjective {
    private static final double ACTIONABLE_SLOT_WEIGHT = 1.5d;
    private static final double ACTION_SPACE_QUALITY_WEIGHT = 3.0d;
    private static final double TIMING_WEIGHT = 1.5d;
    private static final double PRESSURE_WEIGHT = 4.0d;
    private static final double IMMEDIATE_HARM_WEIGHT = 0.10d;
    private static final double RESOURCE_SWING_WEIGHT = 0.02d;
    private static final double INDUCED_EXPOSURE_WEIGHT = 0.85d;

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
        return OpeningVector.fromOpening(
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                tacticalMomentum,
                futureWarLeverage,
                targetPressure
        ).score();
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        return (totals.ownValue() - totals.enemyValue())
                + (ACTIONABLE_SLOT_WEIGHT * StrategicValueTotals.slotBalanceOf(view, teamId));
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

    private record OpeningVector(
            double actionSpaceQuality,
            double timing,
            double pressure,
            double pressureProgress,
            double immediateHarm,
            double resourceSwing,
            double inducedExposure
    ) {
        static OpeningVector fromOpening(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double tacticalMomentum,
                double futureWarLeverage,
                double targetPressure
        ) {
            double actionSpaceQuality = Math.max(0d, futureWarLeverage);
            double timing = Math.max(0d, tacticalMomentum)
                    + controlPressureTiming(controlLeverage, targetPressure);
            return new OpeningVector(
                    actionSpaceQuality,
                    timing,
                    Math.max(0d, targetPressure),
                    Math.max(0d, controlLeverage),
                    Math.max(0d, immediateHarm),
                    Math.max(0d, resourceSwing),
                    Math.max(0d, selfExposure)
            );
        }

        double score() {
            double positiveActionSpaceQuality = Math.max(0d, actionSpaceQuality);
            double positiveTiming = Math.max(0d, timing);
            double effectivePressure = StrategicOpeningPressure.capturableTargetPressure(
                    immediateHarm,
                    inducedExposure,
                    Math.max(0d, resourceSwing),
                    Math.max(0d, pressureProgress),
                    positiveActionSpaceQuality + positiveTiming,
                    pressure
            );
            return (ACTION_SPACE_QUALITY_WEIGHT * actionSpaceQuality)
                    + (TIMING_WEIGHT * timing)
                    + (PRESSURE_WEIGHT * effectivePressure)
                    + (IMMEDIATE_HARM_WEIGHT * immediateHarm)
                    + (RESOURCE_SWING_WEIGHT * Math.max(0d, resourceSwing))
                    - (INDUCED_EXPOSURE_WEIGHT * inducedExposure);
        }
    }

    private static double controlPressureTiming(double controlLeverage, double targetPressure) {
        if (!(controlLeverage > 0d) || !(targetPressure > 0d)) {
            return 0d;
        }
        double targetOpportunity = targetPressure / (targetPressure + 12d);
        return Math.min(Math.max(0d, controlLeverage), targetOpportunity);
    }
}

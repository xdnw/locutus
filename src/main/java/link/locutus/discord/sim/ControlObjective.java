package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that prioritizes durable control, slot denial, and follow-through leverage. */
final class ControlObjective implements StrategicObjective {
    private static final double DURABLE_CONTROL_WEIGHT = 1.5d;
    private static final double ACTIONABLE_SLOT_WEIGHT = 1.5d;
    private static final double ACTION_SPACE_QUALITY_WEIGHT = 3.0d;
    private static final double TIMING_WEIGHT = 1.5d;
    private static final double PRESSURE_WEIGHT = 4.0d;
    private static final double IMMEDIATE_HARM_WEIGHT = 0.10d;
    private static final double RESOURCE_SWING_WEIGHT = 0.02d;
    private static final double INDUCED_EXPOSURE_WEIGHT = 0.85d;

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false, true, true, true);
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
        return scoreOpening(immediateHarm, selfExposure, resourceSwing, controlLeverage, 0d, 0d, futureWarLeverage, targetPressure);
    }

    @Override
    public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return scoreOpening(
                metrics.immediateHarm(),
                metrics.selfExposure(),
                metrics.resourceSwing(),
                metrics.controlLeverage(),
                metrics.declarationReadiness(),
                metrics.tacticalMomentum(),
                metrics.futureWarLeverage(),
                metrics.targetPressure()
        );
    }

    @Override
    public double scoreLaterDeclaration(LaterDeclarationEvaluation metrics, int teamId) {
        double objectiveScore = scoreOpening(metrics, teamId);
        if (isReadinessOnly(metrics)) {
            double actionableScore = scoreOpening(
                    metrics.immediateHarm(),
                    metrics.selfExposure(),
                    metrics.resourceSwing(),
                    metrics.controlLeverage(),
                    0d,
                    metrics.tacticalMomentum(),
                    metrics.futureWarLeverage(),
                    metrics.targetPressure()
            );
            if (!(actionableScore > 0d)) {
                return 0d;
            }
        }
        if (!(objectiveScore > 0d)) {
            return 0d;
        }
        return objectiveScore
                * StrategicObjective.laterRebuildFit(metrics)
                * StrategicObjective.laterExposureFit(metrics)
                * StrategicObjective.laterTargetOpportunityFit(metrics)
                * StrategicObjective.laterSupportFit(metrics);
    }

    private static double scoreOpening(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double declarationReadiness,
            double tacticalMomentum,
            double futureWarLeverage,
            double targetPressure
    ) {
        return ControlVector.fromOpening(
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                declarationReadiness,
                tacticalMomentum,
                futureWarLeverage,
                targetPressure
        ).score();
    }

    private static double declarationReadinessContribution(
            double declarationReadiness,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure
    ) {
        if (!(declarationReadiness > 0d) || !(targetPressure > 0d)) {
            return 0d;
        }
        double targetOpportunity = targetPressure / (targetPressure + 12d);
        double visibilityContribution = 1.20d * Math.min(1d, declarationReadiness) * targetOpportunity;
        double realizedLeverage = Math.max(0d, controlLeverage) + Math.max(0d, futureWarLeverage);
        if (!(realizedLeverage > 0d)) {
            return visibilityContribution;
        }
        return Math.min(0.20d * realizedLeverage, 0.35d * visibilityContribution);
    }

    private static boolean isReadinessOnly(StrategicEvaluationComponents metrics) {
        return metrics.declarationReadiness() > 0d
                && !(metrics.resourceSwing() > 0d)
                && !(metrics.controlLeverage() > 0d)
                && !(metrics.futureWarLeverage() > 0d);
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        if (view instanceof TeamWarControlView controlView) {
            return ControlVector.fromTerminal(StrategicControlReducer.reduce(controlView, teamId)).score();
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

    private record ControlVector(
            double durableControl,
            double actionableSlotValue,
            double actionSpaceQuality,
            double timing,
            double pressure,
            double pressureProgress,
            double immediateHarm,
            double resourceSwing,
            double inducedExposure
    ) {
        static ControlVector fromOpening(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double declarationReadiness,
                double tacticalMomentum,
                double futureWarLeverage,
                double targetPressure
        ) {
            double actionSpaceQuality = Math.max(0d, futureWarLeverage);
            double timing = Math.max(0d, tacticalMomentum)
                    + declarationReadinessContribution(
                            declarationReadiness,
                            controlLeverage,
                            futureWarLeverage,
                            targetPressure
                    ) / TIMING_WEIGHT
                    + controlPressureTiming(controlLeverage, targetPressure);
            return new ControlVector(
                    0d,
                    0d,
                    actionSpaceQuality,
                    timing,
                    Math.max(0d, targetPressure),
                    Math.max(0d, controlLeverage),
                    Math.max(0d, immediateHarm),
                    Math.max(0d, resourceSwing),
                    Math.max(0d, selfExposure)
            );
        }

        static ControlVector fromTerminal(StrategicControlReducer.ControlComponents components) {
            return new ControlVector(
                    components.durableControl(),
                    components.slotDenial(),
                    components.followOnLeverage(),
                    components.tacticalMomentum(),
                    components.targetPressure(),
                    Math.max(0d, components.durableControl())
                            + Math.max(0d, components.slotDenial())
                            + Math.max(0d, components.followOnLeverage())
                            + Math.max(0d, components.tacticalMomentum()),
                    0d,
                    0d,
                    0d
            );
        }

        double score() {
            double positiveDurableControl = Math.max(0d, durableControl);
            double positiveActionableSlotValue = Math.max(0d, actionableSlotValue);
            double positiveActionSpaceQuality = Math.max(0d, actionSpaceQuality);
            double positiveTiming = Math.max(0d, timing);
            double effectivePressure = StrategicOpeningPressure.capturableTargetPressure(
                    immediateHarm,
                    inducedExposure,
                    Math.max(0d, resourceSwing),
                    positiveDurableControl + positiveActionableSlotValue + Math.max(0d, pressureProgress),
                    positiveActionSpaceQuality + positiveTiming,
                    pressure
            );
            return (DURABLE_CONTROL_WEIGHT * durableControl)
                    + (ACTIONABLE_SLOT_WEIGHT * actionableSlotValue)
                    + (ACTION_SPACE_QUALITY_WEIGHT * actionSpaceQuality)
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

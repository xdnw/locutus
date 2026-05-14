package link.locutus.discord.sim;

/**
 * Objective specialization that can score planner-local strategic value surfaces.
 */
public interface StrategicObjective extends Objective {
    interface LaterDeclarationEvaluation extends StrategicEvaluationComponents {
        double declarerStrength();

        double targetStrength();

        double declarerRebuildStrengthGain();

        int remainingDeclarerSlots();

        int remainingTargetSlots();

        double slotActionability();

        double targetBestActionability();

        double targetSupportActionability();

        double activityWeight();
    }

    double scoreTerminal(StrategicValueView view, int teamId);

    default double scoreTerminalComparison(StrategicValueView view, int teamId, int opposingTeamId) {
        return scoreTerminal(view, teamId);
    }

    /**
     * Scores a bounded opening rollout directly from its retained planner metrics.
     */
    double scoreOpening(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure,
            int teamId
    );

    default double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return scoreOpening(
                metrics.immediateHarm(),
                metrics.selfExposure(),
                metrics.resourceSwing(),
                metrics.controlLeverage(),
                metrics.futureWarLeverage(),
                metrics.targetPressure(),
                teamId
        );
    }

    default double scoreLaterDeclaration(LaterDeclarationEvaluation metrics, int teamId) {
        double objectiveScore = scoreOpening(metrics, teamId);
        if (!(objectiveScore > 0d)) {
            return 0d;
        }
        return objectiveScore
                * laterRebuildFit(metrics)
                * laterExposureFit(metrics)
                * laterTargetOpportunityFit(metrics)
                * laterSupportFit(metrics);
    }

    static double laterSupportFit(LaterDeclarationEvaluation metrics) {
        double slotActionability = Math.max(0d, metrics.slotActionability());
        if (slotActionability >= 1d || !(metrics.targetPressure() > 0d)) {
            return 1d;
        }
        double support = Math.max(0d, metrics.targetSupportActionability());
        double unsupportedNeed = Math.max(0d, 1d - slotActionability);
        if (!(unsupportedNeed > 0d)) {
            return 1d;
        }
        double supportedFit = Math.min(1d, slotActionability + (0.65d * support));
        double pressureWeight = metrics.targetPressure() / (metrics.targetPressure() + 12d);
        double readiness = 1d - (pressureWeight * (1d - supportedFit));
        return clamp(readiness, 0.15d, 1d);
    }

    static double laterTargetOpportunityFit(LaterDeclarationEvaluation metrics) {
        double slotActionability = Math.max(0d, metrics.slotActionability());
        double bestActionability = Math.max(0d, metrics.targetBestActionability());
        if (!(bestActionability > slotActionability) || !(slotActionability > 0d)) {
            return 1d;
        }
        double relativeActionability = clamp(slotActionability / bestActionability, 0d, 1d);
        double slotScarcity = 1d / Math.sqrt(Math.max(1, metrics.remainingTargetSlots()));
        double readiness = 1d - (slotScarcity * (1d - (relativeActionability * relativeActionability)));
        return clamp(readiness, 0.05d, 1d);
    }

    static double laterExposureFit(LaterDeclarationEvaluation metrics) {
        double exposure = Math.max(0d, metrics.selfExposure());
        if (!(exposure > 0d)) {
            return 1d;
        }
        double actionableProgress = Math.max(0d, metrics.immediateHarm())
                + (0.01d * Math.max(0d, metrics.resourceSwing()))
                + (2.0d * Math.max(0d, metrics.controlLeverage()))
                + (3.0d * Math.max(0d, metrics.futureWarLeverage()));
        if (!(actionableProgress > 0d)) {
            return 0d;
        }
        if (actionableProgress >= exposure) {
            return 1d;
        }
        double fit = actionableProgress / (actionableProgress + exposure);
        return fit * fit;
    }

    static double laterRebuildFit(LaterDeclarationEvaluation metrics) {
        if (!(metrics.declarerRebuildStrengthGain() > 0d)
                || !(metrics.declarerStrength() > 0d)
                || !(metrics.targetStrength() > 0d)
                || metrics.declarerStrength() >= metrics.targetStrength()) {
            return 1d;
        }
        double rebuiltStrength = metrics.declarerStrength() + metrics.declarerRebuildStrengthGain();
        if (rebuiltStrength <= metrics.declarerStrength()) {
            return 1d;
        }
        double readiness = metrics.declarerStrength() / rebuiltStrength;
        return clamp(readiness, 0.20d, 1d);
    }

    private static double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    default CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.none();
    }

    /**
     * Returns the minimum probe required for a candidate edge to be admitted before scoring.
     *
     * <p>Specialist objectives can lower this floor to keep low-probe edges that still carry
     * objective value, while damage-style objectives can rely on the default pruning floor.</p>
     */
    default CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.defaultPolicy();
    }

    default boolean usesWarSlotDenial() {
        return false;
    }

    @Override
    default double scoreTerminal(SimWorld world, int teamId) {
        return scoreTerminal(StrategicValueView.of(world), teamId);
    }
}

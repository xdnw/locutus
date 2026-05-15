package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

final class ObjectiveDrivenLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final int teamId;
    private final LaterDeclarationMetrics metrics = new LaterDeclarationMetrics();
    private final LaterDeclarationMetrics actionableMetrics = new LaterDeclarationMetrics();

    ObjectiveDrivenLaterDeclarationScoringPolicy(StrategicObjective objective) {
        this(objective, DEFAULT_TEAM_ID);
    }

    ObjectiveDrivenLaterDeclarationScoringPolicy(StrategicObjective objective, int teamId) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        this.objective = objective;
        this.teamId = teamId;
    }

    @Override
    public boolean usesPrimitiveProjectedComponents() {
        return true;
    }

    @Override
    public double score(LaterDeclarationScoreContext context) {
        double actionability = LaterDeclarationFit.actionability(context.declarerStrength(), context.targetStrength());
        double slotActionability = context.resourceSwing() > 0d
                ? Math.max(actionability, LaterDeclarationFit.specialistSlotActionability(
                        context.resourceSwing(),
                        context.targetPressure()
                ))
                : actionability;
        metrics.set(context);
        double objectiveScore = objective.scoreOpening(metrics, teamId);
        if (isReadinessOnly(metrics)) {
            actionableMetrics.set(context, 0d);
            if (!(objective.scoreOpening(actionableMetrics, teamId) > 0d)) {
                return 0d;
            }
        }
        if (!(objectiveScore > 0d)) {
            return 0d;
        }
        return context.activityWeight()
                * objectiveScore
                * actionability
                * LaterDeclarationFit.slotFit(context.remainingDeclarerSlots(), context.remainingTargetSlots(), slotActionability);
    }

    private static boolean isReadinessOnly(LaterDeclarationMetrics metrics) {
        return metrics.declarationReadiness() > 0d
                && !(metrics.resourceSwing() > 0d)
                && !(metrics.controlLeverage() > 0d)
                && !(metrics.futureWarLeverage() > 0d);
    }

    private static final class LaterDeclarationMetrics implements link.locutus.discord.sim.StrategicEvaluationComponents {
        private double immediateHarm;
        private double selfExposure;
        private double resourceSwing;
        private double controlLeverage;
        private double declarationReadiness;
        private double futureWarLeverage;
        private double targetPressure;

        private void set(LaterDeclarationScoreContext context) {
            set(context, Math.max(0d, context.declarationReadiness()));
        }

        private void set(LaterDeclarationScoreContext context, double declarationReadiness) {
            this.immediateHarm = Math.max(0d, context.immediateHarm());
            this.selfExposure = Math.max(0d, context.selfExposure());
            this.resourceSwing = Math.max(0d, context.resourceSwing());
            this.controlLeverage = Math.max(0d, context.controlLeverage());
            this.declarationReadiness = Math.max(0d, declarationReadiness);
            this.futureWarLeverage = Math.max(0d, context.futureWarLeverage());
            this.targetPressure = Math.max(0d, context.targetPressure());
        }

        @Override
        public double immediateHarm() {
            return immediateHarm;
        }

        @Override
        public double selfExposure() {
            return selfExposure;
        }

        @Override
        public double resourceSwing() {
            return resourceSwing;
        }

        @Override
        public double controlLeverage() {
            return controlLeverage;
        }

        @Override
        public double declarationReadiness() {
            return declarationReadiness;
        }

        @Override
        public double futureWarLeverage() {
            return futureWarLeverage;
        }

        @Override
        public double targetPressure() {
            return targetPressure;
        }

    }
}

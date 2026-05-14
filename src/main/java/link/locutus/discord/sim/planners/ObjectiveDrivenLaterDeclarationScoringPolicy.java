package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

final class ObjectiveDrivenLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final int teamId;
    private final LaterDeclarationMetrics metrics = new LaterDeclarationMetrics();

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
        metrics.set(context, slotActionability);
        double objectiveScore = objective.scoreLaterDeclaration(metrics, teamId);
        if (!(objectiveScore > 0d)) {
            return 0d;
        }
        return context.activityWeight()
                * objectiveScore
                * actionability
                * LaterDeclarationFit.slotFit(context.remainingDeclarerSlots(), context.remainingTargetSlots(), slotActionability);
    }

    private static final class LaterDeclarationMetrics implements StrategicObjective.LaterDeclarationEvaluation {
        private double immediateHarm;
        private double selfExposure;
        private double resourceSwing;
        private double controlLeverage;
        private double declarationReadiness;
        private double futureWarLeverage;
        private double targetPressure;
        private double declarerStrength;
        private double targetStrength;
        private double declarerRebuildStrengthGain;
        private int remainingDeclarerSlots;
        private int remainingTargetSlots;
        private double slotActionability;
        private double targetBestActionability;
        private double targetSupportActionability;
        private double activityWeight;

        private void set(LaterDeclarationScoreContext context, double slotActionability) {
            this.immediateHarm = Math.max(0d, context.immediateHarm());
            this.selfExposure = Math.max(0d, context.selfExposure());
            this.resourceSwing = Math.max(0d, context.resourceSwing());
            this.controlLeverage = Math.max(0d, context.controlLeverage());
            this.declarationReadiness = Math.max(0d, context.declarationReadiness());
            this.futureWarLeverage = Math.max(0d, context.futureWarLeverage());
            this.targetPressure = Math.max(0d, context.targetPressure());
            this.declarerStrength = Math.max(0d, context.declarerStrength());
            this.targetStrength = Math.max(0d, context.targetStrength());
            this.declarerRebuildStrengthGain = Math.max(0d, context.declarerRebuildStrengthGain());
            this.remainingDeclarerSlots = context.remainingDeclarerSlots();
            this.remainingTargetSlots = context.remainingTargetSlots();
            this.slotActionability = Math.max(0d, slotActionability);
            this.targetBestActionability = Math.max(0d, context.targetBestActionability());
            this.targetSupportActionability = Math.max(0d, context.targetSupportActionability());
            this.activityWeight = Math.max(0d, context.activityWeight());
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

        @Override
        public double declarerStrength() {
            return declarerStrength;
        }

        @Override
        public double targetStrength() {
            return targetStrength;
        }

        @Override
        public double declarerRebuildStrengthGain() {
            return declarerRebuildStrengthGain;
        }

        @Override
        public int remainingDeclarerSlots() {
            return remainingDeclarerSlots;
        }

        @Override
        public int remainingTargetSlots() {
            return remainingTargetSlots;
        }

        @Override
        public double slotActionability() {
            return slotActionability;
        }

        @Override
        public double targetBestActionability() {
            return targetBestActionability;
        }

        @Override
        public double targetSupportActionability() {
            return targetSupportActionability;
        }

        @Override
        public double activityWeight() {
            return activityWeight;
        }
    }
}

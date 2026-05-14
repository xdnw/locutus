package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.StrategicObjective;

final class ObjectiveDrivenLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final int teamId;
    private final OpeningMetricVector.Mutable metrics = new OpeningMetricVector.Mutable();

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
        metrics.set(
                Math.max(0d, context.immediateHarm()),
                Math.max(0d, context.selfExposure()),
                Math.max(0d, context.resourceSwing()),
                Math.max(0d, context.controlLeverage()),
                0d,
                Math.max(0d, context.futureWarLeverage()),
                Math.max(0d, context.targetPressure())
        );
        double objectiveScore = objective.scoreOpening(metrics, teamId);
        if (!(objectiveScore > 0d)) {
            return 0d;
        }
        double actionability = LaterDeclarationFit.actionability(context.declarerStrength(), context.targetStrength());
        double slotActionability = context.resourceSwing() > 0d
            ? Math.max(actionability, LaterDeclarationFit.specialistSlotActionability(
                    context.resourceSwing(),
                    context.targetPressure()
            ))
            : actionability;
        return context.activityWeight()
            * objectiveScore
            * actionability
            * rebuildReadiness(context)
                * exposureReadiness(context)
                * targetOpportunityReadiness(context, slotActionability)
            * LaterDeclarationFit.slotFit(context.remainingDeclarerSlots(), context.remainingTargetSlots(), slotActionability);
    }

    private static double targetOpportunityReadiness(LaterDeclarationScoreContext context, double slotActionability) {
        double bestActionability = Math.max(0d, context.targetBestActionability());
        if (!(bestActionability > slotActionability) || !(slotActionability > 0d)) {
            return 1d;
        }
        double relativeActionability = Math.max(0d, Math.min(1d, slotActionability / bestActionability));
        double slotScarcity = 1d / Math.sqrt(Math.max(1, context.remainingTargetSlots()));
        double readiness = 1d - (slotScarcity * (1d - (relativeActionability * relativeActionability)));
        return Math.max(0.05d, Math.min(1d, readiness));
    }

    private static double exposureReadiness(LaterDeclarationScoreContext context) {
        double exposure = Math.max(0d, context.selfExposure());
        if (!(exposure > 0d)) {
            return 1d;
        }
        double actionableProgress = Math.max(0d, context.immediateHarm())
            + (0.01d * Math.max(0d, context.resourceSwing()))
                + (2.0d * Math.max(0d, context.controlLeverage()))
                + (3.0d * Math.max(0d, context.futureWarLeverage()));
        if (!(actionableProgress > 0d)) {
            return 0d;
        }
        if (actionableProgress >= exposure) {
            return 1d;
        }
        double fit = actionableProgress / (actionableProgress + exposure);
        return fit * fit;
    }

    private static double rebuildReadiness(LaterDeclarationScoreContext context) {
        if (!(context.declarerRebuildStrengthGain() > 0d)
                || !(context.declarerStrength() > 0d)
                || !(context.targetStrength() > 0d)
                || context.declarerStrength() >= context.targetStrength()) {
            return 1d;
        }
        double rebuiltStrength = context.declarerStrength() + context.declarerRebuildStrengthGain();
        if (rebuiltStrength <= context.declarerStrength()) {
            return 1d;
        }
        double readiness = context.declarerStrength() / rebuiltStrength;
        return Math.max(0.20d, Math.min(1d, readiness));
    }
}

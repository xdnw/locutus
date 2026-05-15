package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

final class ObjectiveDrivenLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final int teamId;

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
    public double score(LaterDeclarationScoreContext context) {
        double actionability = LaterDeclarationFit.actionability(context.declarerStrength(), context.targetStrength());
        double slotActionability = context.resourceSwing() > 0d
                ? Math.max(actionability, LaterDeclarationFit.specialistSlotActionability(
                        context.resourceSwing(),
                        context.targetPressure()
                ))
                : actionability;
        double objectiveScore = objective.scoreOpening(
                Math.max(0d, context.immediateHarm()),
                Math.max(0d, context.selfExposure()),
                Math.max(0d, context.resourceSwing()),
                Math.max(0d, context.controlLeverage()),
                Math.max(0d, context.futureWarLeverage()),
                Math.max(0d, context.targetPressure()),
                teamId
        );
        if (isReadinessOnly(context)) {
            if (!(objectiveScore > 0d)) {
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

    private static boolean isReadinessOnly(LaterDeclarationScoreContext context) {
        return context.declarationReadiness() > 0d
                && !(context.resourceSwing() > 0d)
                && !(context.controlLeverage() > 0d)
                && !(context.futureWarLeverage() > 0d);
    }
}

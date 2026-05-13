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
        return context.activityWeight()
            * objectiveScore
            * actionability(context)
            * rebuildReadiness(context)
            / slotContention(context);
    }

    private static double actionability(LaterDeclarationScoreContext context) {
        if (!(context.declarerStrength() > 0d) || !(context.targetStrength() > 0d)) {
            return 0d;
        }
        double strengthRatio = context.declarerStrength() / context.targetStrength();
        if (strengthRatio < 1d) {
            return strengthRatio * strengthRatio;
        }
        return Math.min(1.5d, Math.sqrt(strengthRatio));
    }

    private static double slotContention(LaterDeclarationScoreContext context) {
        return Math.max(1, Math.min(
                Math.max(1, context.remainingDeclarerSlots()),
                Math.max(1, context.remainingTargetSlots())
        ));
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

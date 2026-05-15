package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

final class ObjectiveDrivenLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    ObjectiveDrivenLaterDeclarationScoringPolicy(StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
    }

    @Override
    public double score(LaterDeclarationScoreContext context) {
        double projectedValue = Math.max(0d, context.projectedValue());
        if (!(projectedValue > 0d)) {
            return 0d;
        }
        return clamp01(context.activityWeight()) * projectedValue;
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}

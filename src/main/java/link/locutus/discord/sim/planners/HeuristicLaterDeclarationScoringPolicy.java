package link.locutus.discord.sim.planners;

final class HeuristicLaterDeclarationScoringPolicy implements LaterDeclarationScoringPolicy {
    static final HeuristicLaterDeclarationScoringPolicy INSTANCE = new HeuristicLaterDeclarationScoringPolicy();

    private static final double MIN_TARGET_VALUE = 50d;
    private static final double TARGET_VALUE_MULTIPLIER = 0.10d;
    private static final double MAX_STRENGTH_RATIO = 2.0d;

    private HeuristicLaterDeclarationScoringPolicy() {
    }

    @Override
    public double score(LaterDeclarationScoreContext context) {
        if (!(context.declarerStrength() > 0d) || !(context.targetStrength() > 0d)) {
            return context.openingScore();
        }
        double activity = clamp01(context.activityWeight());
        double strengthRatio = context.declarerStrength() / Math.max(1d, context.targetStrength());
        double targetValue = Math.max(MIN_TARGET_VALUE, context.targetPressure() * TARGET_VALUE_MULTIPLIER);
        double declarationScore = activity
                * targetValue
                * Math.min(MAX_STRENGTH_RATIO, strengthRatio)
                * LaterDeclarationFit.slotFit(context.remainingDeclarerSlots(), context.remainingTargetSlots());
        return Math.max(context.openingScore(), declarationScore);
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

package link.locutus.discord.sim.planners;

interface LaterDeclarationScoringPolicy {
    double score(LaterDeclarationScoreContext context);

    record LaterDeclarationScoreContext(
            double projectedValue,
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double targetPressure,
            double declarerStrength,
            double targetStrength,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double activityWeight
    ) {
    }
}

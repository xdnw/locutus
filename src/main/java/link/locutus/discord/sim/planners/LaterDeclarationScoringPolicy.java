package link.locutus.discord.sim.planners;

interface LaterDeclarationScoringPolicy {
    double score(LaterDeclarationScoreContext context);

    record LaterDeclarationScoreContext(
            double openingScore,
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure,
            double declarerStrength,
            double targetStrength,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double activityWeight
    ) {
    }
}

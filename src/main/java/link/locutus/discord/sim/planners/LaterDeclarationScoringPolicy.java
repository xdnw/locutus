package link.locutus.discord.sim.planners;

interface LaterDeclarationScoringPolicy {
    double score(LaterDeclarationScoreContext context);

    default boolean usesPrimitiveProjectedComponents() {
        return false;
    }

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
                double declarerRebuildStrengthGain,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double targetBestActionability,
            double targetSupportActionability,
            double activityWeight
    ) {
    }
}

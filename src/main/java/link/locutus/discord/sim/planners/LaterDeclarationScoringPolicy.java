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
            double declarationReadiness,
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
        LaterDeclarationScoreContext(
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
            this(
                    openingScore,
                    immediateHarm,
                    selfExposure,
                    resourceSwing,
                    controlLeverage,
                    futureWarLeverage,
                    0d,
                    targetPressure,
                    declarerStrength,
                    targetStrength,
                    declarerRebuildStrengthGain,
                    remainingDeclarerSlots,
                    remainingTargetSlots,
                    targetBestActionability,
                    targetSupportActionability,
                    activityWeight
            );
        }
    }
}

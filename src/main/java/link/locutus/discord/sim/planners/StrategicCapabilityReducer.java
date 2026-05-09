package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicAssetValue;

/**
 * Shared reducer from primitive capability vectors to planner-facing strategic lanes.
 */
final class StrategicCapabilityReducer {
    private static final double SLOT_CAPABILITY_SCALE = 60.0d;
    private static final double STRATEGIC_CAPABILITY_SCALE = 14.0d;
    private static final double STRATEGIC_RUNWAY_SCALE = 12.0d;
    private static final double SLOT_GROUND_WEIGHT = 22d;
    private static final double SLOT_AIR_WEIGHT = 28d;
    private static final double SLOT_NAVAL_WEIGHT = 12d;
    private static final double SLOT_MISSILE_WEIGHT = 6d;
    private static final double SLOT_NUKE_WEIGHT = 8d;
    private static final double SLOT_GROUND_REBUILD_WEIGHT = 6d;
    private static final double SLOT_AIR_REBUILD_WEIGHT = 8d;
    private static final double SLOT_NAVAL_REBUILD_WEIGHT = 4d;
    private static final double SLOT_GROUND_REFERENCE = 1_500_000d;
    private static final double SLOT_AIR_REFERENCE = 3_000d;
    private static final double SLOT_NAVAL_REFERENCE = 300d;
    private static final double SLOT_MISSILE_REFERENCE = 18d;
    private static final double SLOT_NUKE_REFERENCE = 12d;

    private StrategicCapabilityReducer() {
    }

    static double slotCapabilityValue(StrategicCapabilityVector vector) {
        if (vector == null) {
            return 0d;
        }
        return (combatCapabilityComponent(vector) + rebuildRunwayComponent(vector)) * SLOT_CAPABILITY_SCALE;
    }

    static double strategicMilitaryValue(
            StrategicCapabilityVector vector,
            StrategicAssetValue.StrategicRelevance relevance
    ) {
        if (vector == null) {
            return 0d;
        }
        double relevanceMultiplier = StrategicAssetValue.strategicRelevanceMultiplier(relevance);
        double capabilityValue = combatCapabilityComponent(vector) * STRATEGIC_CAPABILITY_SCALE;
        double runwayValue = rebuildRunwayComponent(vector) * STRATEGIC_RUNWAY_SCALE;
        return (capabilityValue + runwayValue) * relevanceMultiplier;
    }

    static double offensiveSlotCapabilityValue(StrategicCapabilityVector vector, double slotPressure) {
        return offensiveSlotCapabilityValue(slotCapabilityValue(vector), slotPressure);
    }

    static double defensiveSlotCapabilityValue(StrategicCapabilityVector vector, double slotPressure) {
        return defensiveSlotCapabilityValue(slotCapabilityValue(vector), slotPressure);
    }

    static double offensiveSlotCapabilityValue(double slotCapabilityValue, double slotPressure) {
        return Math.max(0d, slotCapabilityValue) * (0.20d + (0.40d * clamp01(slotPressure)));
    }

    static double defensiveSlotCapabilityValue(double slotCapabilityValue, double slotPressure) {
        return Math.max(0d, slotCapabilityValue) * (0.90d + (0.20d * clamp01(slotPressure)));
    }

    private static double combatCapabilityComponent(StrategicCapabilityVector vector) {
        double total = 0d;
        total += SLOT_GROUND_WEIGHT * normalizedLog(vector.groundCapability(), SLOT_GROUND_REFERENCE);
        total += SLOT_AIR_WEIGHT * normalizedLog(vector.airCapability(), SLOT_AIR_REFERENCE);
        total += SLOT_NAVAL_WEIGHT * normalizedLog(vector.navalCapability(), SLOT_NAVAL_REFERENCE);
        total += SLOT_MISSILE_WEIGHT * normalizedLog(vector.missileCapability(), SLOT_MISSILE_REFERENCE);
        total += SLOT_NUKE_WEIGHT * normalizedLog(vector.nukeCapability(), SLOT_NUKE_REFERENCE);
        return total;
    }

    private static double rebuildRunwayComponent(StrategicCapabilityVector vector) {
        double total = 0d;
        total += SLOT_GROUND_REBUILD_WEIGHT * rebuildPressure(
                vector.soldierRemainingRecovery(),
                vector.soldierDailyCap(),
                vector.tankRemainingRecovery(),
                vector.tankDailyCap(),
                vector.groundCapability(),
                SLOT_GROUND_REFERENCE
        );
        total += SLOT_AIR_REBUILD_WEIGHT * rebuildPressure(
                vector.airRemainingRecovery(),
                vector.airDailyCap(),
                0,
                0,
                vector.airCapability(),
                SLOT_AIR_REFERENCE
        );
        total += SLOT_NAVAL_REBUILD_WEIGHT * rebuildPressure(
                vector.navalRemainingRecovery(),
                vector.navalDailyCap(),
                0,
                0,
                vector.navalCapability(),
                SLOT_NAVAL_REFERENCE
        );
        return total;
    }

    private static double rebuildPressure(
            int remainingPrimary,
            int dailyPrimary,
            int remainingSecondary,
            int dailySecondary,
            double currentCapability,
            double reference
    ) {
        double remainingFraction = dailySecondary <= 0
                ? fraction(remainingPrimary, dailyPrimary)
                : Math.max(fraction(remainingPrimary, dailyPrimary), fraction(remainingSecondary, dailySecondary));
        return remainingFraction * (0.35d + normalizedLog(currentCapability, reference));
    }

    private static double fraction(int value, int max) {
        if (value <= 0 || max <= 0) {
            return 0d;
        }
        return Math.min(1d, value / (double) max);
    }

    private static double normalizedLog(double value, double reference) {
        if (!(value > 0d) || !(reference > 0d)) {
            return 0d;
        }
        return Math.min(1d, Math.log1p(value) / Math.log1p(reference));
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

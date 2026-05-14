package link.locutus.discord.sim.planners;

final class DeclarationReadiness {
    private DeclarationReadiness() {
    }

    static double opening(double attackerStrength, double targetStrength, boolean attackerCanDeclare, boolean attackerSlotReady) {
        return readiness(attackerStrength, targetStrength, attackerCanDeclare, attackerSlotReady, 1d, 1d);
    }

    static double projected(
            double attackerStrength,
            double targetStrength,
            boolean attackerCanDeclare,
            boolean attackerSlotReady,
            int remainingTargetSlots,
            double targetBestActionability
    ) {
        double targetSlotReady = remainingTargetSlots > 0 ? 1d : 0d;
        return readiness(
                attackerStrength,
                targetStrength,
                attackerCanDeclare,
                attackerSlotReady,
                clamp01(targetBestActionability),
                targetSlotReady
        );
    }

    private static double readiness(
            double attackerStrength,
            double targetStrength,
            boolean attackerCanDeclare,
            boolean attackerSlotReady,
            double targetActionabilityReady,
            double targetSlotReady
    ) {
        if (!attackerCanDeclare || !attackerSlotReady) {
            return 0d;
        }
        double strengthReadiness = strengthReadiness(strengthRatio(attackerStrength, targetStrength));
        if (!(strengthReadiness > 0d)) {
            return 0d;
        }
        return clamp01(strengthReadiness * clamp01(targetActionabilityReady) * clamp01(targetSlotReady));
    }

    private static double strengthReadiness(double strengthRatio) {
        if (!Double.isFinite(strengthRatio)) {
            return 1d;
        }
        if (strengthRatio <= 0.75d) {
            return 0d;
        }
        return clamp01((strengthRatio - 0.75d) / 0.55d);
    }

    private static double strengthRatio(double attackerStrength, double targetStrength) {
        if (targetStrength <= 0d) {
            return attackerStrength > 0d ? Double.POSITIVE_INFINITY : 1d;
        }
        return Math.max(0d, attackerStrength) / targetStrength;
    }

    private static double clamp01(double value) {
        if (!(value > 0d)) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}
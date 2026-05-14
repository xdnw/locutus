package link.locutus.discord.sim.planners;

final class LaterDeclarationFit {
    private LaterDeclarationFit() {
    }

    static double actionability(double declarerStrength, double targetStrength) {
        if (!(declarerStrength > 0d) || !(targetStrength > 0d)) {
            return 0d;
        }
        double strengthRatio = declarerStrength / targetStrength;
        if (strengthRatio < 0.75d) {
            double normalized = Math.max(0d, strengthRatio) / 0.75d;
            return 0.35d * normalized * normalized * normalized;
        }
        if (strengthRatio < 1d) {
            double normalized = (strengthRatio - 0.75d) / 0.25d;
            return 0.35d + (0.65d * normalized * normalized);
        }
        return Math.min(1.5d, Math.sqrt(strengthRatio));
    }

    static double slotFit(int remainingDeclarerSlots, int remainingTargetSlots) {
        return slotFit(remainingDeclarerSlots, remainingTargetSlots, 1d);
    }

    static double slotFit(int remainingDeclarerSlots, int remainingTargetSlots, double actionability) {
        int declarerSlots = Math.max(1, remainingDeclarerSlots);
        int targetSlots = Math.max(1, remainingTargetSlots);
        double declarerBreadth = 0.70d + (0.15d * Math.min(3, declarerSlots));
        double targetContention = 1d / Math.sqrt(targetSlots);
        double boundedActionability = clamp01(actionability);
        double targetOpportunityFit = 1d - (targetContention * (1d - boundedActionability));
        return declarerBreadth * targetContention * Math.max(0d, targetOpportunityFit);
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

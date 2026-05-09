package link.locutus.discord.sim;

/**
 * Mechanics-facing timing helpers for declaration and redeclaration windows.
 */
public final class StrategicTimingValue {
    private StrategicTimingValue() {
    }

    public static int redeclareBlockedTurns(int attackerBeigeTurns, int defenderBeigeTurns, int sameOpponentLockoutTurns) {
        return Math.max(0, Math.max(attackerBeigeTurns, Math.max(defenderBeigeTurns, sameOpponentLockoutTurns)));
    }

    public static double redeclareWaitDiscount(int blockedTurns, int horizonRemainingTurns) {
        if (blockedTurns <= 0) {
            return 1d;
        }
        if (horizonRemainingTurns <= 0 || blockedTurns >= horizonRemainingTurns) {
            return 0d;
        }
        double remainingShare = (horizonRemainingTurns - blockedTurns) / (double) horizonRemainingTurns;
        double delayPenalty = 1d / (1d + blockedTurns);
        return Math.max(0d, remainingShare * delayPenalty);
    }

    public static double victoryTimingWindowValue(
            int ownResistance,
            int enemyResistance,
            int ownControls,
            int enemyControls
    ) {
        int controlEdge = Math.max(0, Math.max(0, ownControls) - Math.max(0, enemyControls));
        if (controlEdge <= 0) {
            return 0d;
        }
        double ownHoldability = normalizedResistance(ownResistance);
        double progress = 1d - normalizedResistance(enemyResistance);
        double contestedWindow = 4d * progress * (1d - progress);
        return controlEdge * ownHoldability * Math.max(0d, contestedWindow);
    }

    private static double normalizedResistance(int resistance) {
        return Math.max(0d, Math.min(1d, Math.max(0, resistance) / 100d));
    }
}
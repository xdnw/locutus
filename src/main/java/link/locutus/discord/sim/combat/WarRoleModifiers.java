package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.WarType;

public final class WarRoleModifiers {
    private WarRoleModifiers() {
    }

    public static double lootModifier(WarType warType, boolean attackerIsOriginalAttacker) {
        return switch (warType) {
            case RAID -> 1.0;
            case ORD -> 0.5;
            case ATT -> attackerIsOriginalAttacker ? 0.25 : 0.5;
            default -> throw new UnsupportedOperationException("Unknown war type: " + warType);
        };
    }

    public static double infraModifier(WarType warType, boolean attackerIsOriginalAttacker) {
        return switch (warType) {
            case RAID -> attackerIsOriginalAttacker ? 0.25 : 0.5;
            case ORD -> 0.5;
            case ATT -> 1.0;
            default -> throw new UnsupportedOperationException("Unknown war type: " + warType);
        };
    }
}
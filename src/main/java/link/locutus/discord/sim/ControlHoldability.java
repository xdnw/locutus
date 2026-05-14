package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;

/**
 * Shared rules for deciding whether tactical control flags are backed by units that can hold them.
 */
public final class ControlHoldability {
    private ControlHoldability() {
    }

    public static int backedControlCount(
            int ownTeamId,
            int groundSuperiorityTeamId,
            int airSuperiorityTeamId,
            int blockadeTeamId,
            UnitReader units
    ) {
        if (units == null) {
            return 0;
        }
        int backed = 0;
        if (groundSuperiorityTeamId == ownTeamId && canHoldGround(units)) {
            backed++;
        }
        if (airSuperiorityTeamId == ownTeamId && canHoldAir(units)) {
            backed++;
        }
        if (blockadeTeamId == ownTeamId && canHoldBlockade(units)) {
            backed++;
        }
        return backed;
    }

    public static boolean canHoldGround(UnitReader units) {
        return units != null
                && Math.max(0, units.units(MilitaryUnit.SOLDIER)) + Math.max(0, units.units(MilitaryUnit.TANK)) > 0;
    }

    public static boolean canHoldAir(UnitReader units) {
        return units != null && units.units(MilitaryUnit.AIRCRAFT) > 0;
    }

    public static boolean canHoldBlockade(UnitReader units) {
        return units != null && units.units(MilitaryUnit.SHIP) > 0;
    }

    @FunctionalInterface
    public interface UnitReader {
        int units(MilitaryUnit unit);
    }
}

package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicAssetValue;

final class PlannerControlStateReducer {
    private PlannerControlStateReducer() {
    }

    static int controlCountForOwnerCode(int ownerCode, int groundOwner, int airOwner, int blockadeOwner) {
        int count = 0;
        if (groundOwner == ownerCode) {
            count++;
        }
        if (airOwner == ownerCode) {
            count++;
        }
        if (blockadeOwner == ownerCode) {
            count++;
        }
        return count;
    }

    static int controlCountForProjectedWar(PlannerProjectedWar war, PlannerLocalConflict.ControlOwner owner) {
        int count = 0;
        if (war.groundSuperiorityOwner() == owner) {
            count++;
        }
        if (war.airSuperiorityOwner() == owner) {
            count++;
        }
        if (war.blockadeOwner() == owner) {
            count++;
        }
        return count;
    }

    static StrategicAssetValue.ActiveWarContext activeWarContextFromRelativeState(
            int activeOpponents,
            double slotPressure,
            int ownMaps,
            int enemyMaps,
            int ownResistance,
            int enemyResistance,
            int ownControls,
            int enemyControls
    ) {
        return StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                activeOpponents,
                slotPressure,
                ownMaps,
                enemyMaps,
                ownResistance,
                enemyResistance,
                ownControls,
                enemyControls
        );
    }
}

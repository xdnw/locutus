package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;

record PlannerProjectedWar(
        int attackerNationId,
        int defenderNationId,
        WarType warType,
        int startTurn,
        WarStatus status,
        int attackerMaps,
        int defenderMaps,
        int attackerResistance,
        int defenderResistance,
        PlannerLocalConflict.ControlOwner groundControlOwner,
        PlannerLocalConflict.ControlOwner airSuperiorityOwner,
        PlannerLocalConflict.ControlOwner blockadeOwner,
        boolean attackerFortified,
        boolean defenderFortified
) {
    PlannerProjectedWar {
        if (startTurn < 0) {
            throw new IllegalArgumentException("startTurn must be >= 0");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (!status.isActive()) {
            throw new IllegalArgumentException("Projected wars must remain active: " + status);
        }
    }

    long pairKey() {
        return PlannerLocalConflict.pairKey(attackerNationId, defenderNationId);
    }

    int remainingTurns(int currentTurn) {
        int elapsed = Math.max(0, currentTurn - startTurn);
        return Math.max(0, 60 - elapsed);
    }
}
package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.apiv1.enums.WarType;

/**
 * Dense-scenario seed for an already active war between compiled nations.
 *
 * <p>The row is oriented by the real war declarer/target, not by the current
 * planner side. Projection code maps nation IDs to dense slots when it seeds
 * the terminal war table.</p>
 */
public record CompiledActiveWar(
        int attackerNationId,
        int defenderNationId,
        WarType warType,
        int startTurn,
        int attackerMaps,
        int defenderMaps,
        int attackerResistance,
        int defenderResistance,
        ControlOwner groundSuperiorityOwner,
        ControlOwner airSuperiorityOwner,
        ControlOwner blockadeOwner,
        boolean attackerFortified,
        boolean defenderFortified
) {
    public CompiledActiveWar {
        if (attackerNationId <= 0 || defenderNationId <= 0) {
            throw new IllegalArgumentException("war nation IDs must be > 0");
        }
        if (attackerNationId == defenderNationId) {
            throw new IllegalArgumentException("war participants must differ");
        }
        warType = warType == null ? WarType.ORD : warType;
        if (startTurn < 0) {
            throw new IllegalArgumentException("startTurn must be >= 0");
        }
        attackerMaps = clamp(attackerMaps, 0, 12);
        defenderMaps = clamp(defenderMaps, 0, 12);
        attackerResistance = clamp(attackerResistance, 0, 100);
        defenderResistance = clamp(defenderResistance, 0, 100);
        groundSuperiorityOwner = groundSuperiorityOwner == null ? ControlOwner.NONE : groundSuperiorityOwner;
        airSuperiorityOwner = airSuperiorityOwner == null ? ControlOwner.NONE : airSuperiorityOwner;
        blockadeOwner = blockadeOwner == null ? ControlOwner.NONE : blockadeOwner;
    }

    public enum ControlOwner {
        NONE,
        ATTACKER,
        DEFENDER
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

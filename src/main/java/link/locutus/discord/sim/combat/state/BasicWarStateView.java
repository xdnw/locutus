package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.WarType;

import java.util.Objects;

public record BasicWarStateView(
        WarType warType,
    boolean attackerIsOriginalAttacker,
        boolean attackerHasAirControl,
        boolean defenderHasAirControl,
        boolean attackerHasGroundControl,
        boolean defenderHasGroundControl,
        boolean attackerFortified,
        boolean defenderFortified,
        int attackerMaps,
        int defenderMaps,
        int attackerResistance,
        int defenderResistance,
        int blockadeOwner
) implements WarStateView {

    public BasicWarStateView {
        Objects.requireNonNull(warType, "warType");
        attackerMaps = Math.max(0, attackerMaps);
        defenderMaps = Math.max(0, defenderMaps);
        attackerResistance = clampResistance(attackerResistance);
        defenderResistance = clampResistance(defenderResistance);
        if (blockadeOwner < BLOCKADE_NONE || blockadeOwner > BLOCKADE_DEFENDER) {
            throw new IllegalArgumentException("blockadeOwner must be one of BLOCKADE_NONE/BLOCKADE_ATTACKER/BLOCKADE_DEFENDER");
        }
    }

    public static BasicWarStateView simple(WarType warType) {
        return ofActorPerspective(
                warType,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                100,
                100,
                BLOCKADE_NONE
        );
    }

    public static BasicWarStateView ofRelative(
            WarType warType,
            boolean attackerHasAirControl,
            boolean defenderHasAirControl,
            boolean attackerHasGroundControl,
            boolean defenderFortified
    ) {
        return ofActorPerspective(
                warType,
                true,
                attackerHasAirControl,
                defenderHasAirControl,
                attackerHasGroundControl,
                false,
                false,
                defenderFortified,
                0,
                0,
                100,
                100,
                BLOCKADE_NONE
        );
    }

    public static BasicWarStateView ofActorPerspective(
            WarType warType,
            boolean actorIsOriginalAttacker,
            boolean originalAttackerHasAirControl,
            boolean originalDefenderHasAirControl,
            boolean originalAttackerHasGroundControl,
            boolean originalDefenderHasGroundControl,
            boolean originalAttackerFortified,
            boolean originalDefenderFortified,
            int originalAttackerMaps,
            int originalDefenderMaps,
            int originalAttackerResistance,
            int originalDefenderResistance,
            int originalBlockadeOwner
    ) {
        boolean attackerHasAirControl = actorIsOriginalAttacker
                ? originalAttackerHasAirControl
                : originalDefenderHasAirControl;
        boolean defenderHasAirControl = actorIsOriginalAttacker
                ? originalDefenderHasAirControl
                : originalAttackerHasAirControl;
        boolean attackerHasGroundControl = actorIsOriginalAttacker
                ? originalAttackerHasGroundControl
                : originalDefenderHasGroundControl;
        boolean defenderHasGroundControl = actorIsOriginalAttacker
                ? originalDefenderHasGroundControl
                : originalAttackerHasGroundControl;
        boolean attackerFortified = actorIsOriginalAttacker
                ? originalAttackerFortified
                : originalDefenderFortified;
        boolean defenderFortified = actorIsOriginalAttacker
                ? originalDefenderFortified
                : originalAttackerFortified;
        int attackerMaps = actorIsOriginalAttacker ? originalAttackerMaps : originalDefenderMaps;
        int defenderMaps = actorIsOriginalAttacker ? originalDefenderMaps : originalAttackerMaps;
        int attackerResistance = actorIsOriginalAttacker
                ? originalAttackerResistance
                : originalDefenderResistance;
        int defenderResistance = actorIsOriginalAttacker
                ? originalDefenderResistance
                : originalAttackerResistance;
        int blockadeOwner = relativeBlockadeOwner(actorIsOriginalAttacker, originalBlockadeOwner);
        return new BasicWarStateView(
                warType,
                actorIsOriginalAttacker,
                attackerHasAirControl,
                defenderHasAirControl,
                attackerHasGroundControl,
                defenderHasGroundControl,
                attackerFortified,
                defenderFortified,
                attackerMaps,
                defenderMaps,
                attackerResistance,
                defenderResistance,
                blockadeOwner
        );
    }

    private static int relativeBlockadeOwner(boolean actorIsOriginalAttacker, int originalBlockadeOwner) {
        if (originalBlockadeOwner == BLOCKADE_NONE || actorIsOriginalAttacker) {
            return originalBlockadeOwner;
        }
        return originalBlockadeOwner == BLOCKADE_ATTACKER ? BLOCKADE_DEFENDER : BLOCKADE_ATTACKER;
    }

    private static int clampResistance(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
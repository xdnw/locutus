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
        return new BasicWarStateView(
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
        return new BasicWarStateView(
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

    private static int clampResistance(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
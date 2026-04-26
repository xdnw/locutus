package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.CombatKernel;

public interface WarStateView {
    /** @see CombatKernel.AttackContext#BLOCKADE_NONE */
    int BLOCKADE_NONE = CombatKernel.AttackContext.BLOCKADE_NONE;
    /** @see CombatKernel.AttackContext#BLOCKADE_ATTACKER */
    int BLOCKADE_ATTACKER = CombatKernel.AttackContext.BLOCKADE_ATTACKER;
    /** @see CombatKernel.AttackContext#BLOCKADE_DEFENDER */
    int BLOCKADE_DEFENDER = CombatKernel.AttackContext.BLOCKADE_DEFENDER;

    WarType warType();

    boolean attackerIsOriginalAttacker();

    boolean attackerHasAirControl();

    boolean defenderHasAirControl();

    boolean attackerHasGroundControl();

    boolean defenderHasGroundControl();

    boolean attackerFortified();

    boolean defenderFortified();

    int attackerMaps();

    int defenderMaps();

    int attackerResistance();

    int defenderResistance();

    int blockadeOwner();
}
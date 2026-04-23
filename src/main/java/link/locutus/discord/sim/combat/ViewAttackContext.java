package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.WarStateView;

record ViewAttackContext(
        CombatantView attacker,
        CombatantView defender,
        WarStateView war
) implements CombatKernel.AttackContext {
    @Override
    public WarType warType() {
        return war.warType();
    }

    @Override
    public boolean attackerIsOriginalAttacker() {
        return war.attackerIsOriginalAttacker();
    }

    @Override
    public boolean attackerHasAirControl() {
        return war.attackerHasAirControl();
    }

    @Override
    public boolean defenderHasAirControl() {
        return war.defenderHasAirControl();
    }

    @Override
    public boolean attackerHasGroundControl() {
        return war.attackerHasGroundControl();
    }

    @Override
    public boolean defenderHasGroundControl() {
        return war.defenderHasGroundControl();
    }

    @Override
    public boolean attackerFortified() {
        return war.attackerFortified();
    }

    @Override
    public boolean defenderFortified() {
        return war.defenderFortified();
    }

    @Override
    public int attackerMaps() {
        return war.attackerMaps();
    }

    @Override
    public int defenderMaps() {
        return war.defenderMaps();
    }

    @Override
    public int attackerResistance() {
        return war.attackerResistance();
    }

    @Override
    public int defenderResistance() {
        return war.defenderResistance();
    }

    @Override
    public int blockadeOwner() {
        return war.blockadeOwner();
    }
}
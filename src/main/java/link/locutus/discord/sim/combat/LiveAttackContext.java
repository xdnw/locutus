package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;

import java.util.Objects;

/**
 * Reusable kernel-facing adapter over live mutable sim state.
 *
 * <p>This keeps the live attack seam next to the other combat-side attack-context
 * adapters instead of exposing a public sim-package helper only for cross-package
 * visibility.</p>
 */
public final class LiveAttackContext implements CombatKernel.AttackContext {
    private SimNation attacker;
    private SimNation defender;
    private SimWar war;
    private SimSide actorSide;

    public LiveAttackContext bind(SimNation attacker, SimNation defender, SimWar war, SimSide actorSide) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.defender = Objects.requireNonNull(defender, "defender");
        this.war = Objects.requireNonNull(war, "war");
        this.actorSide = Objects.requireNonNull(actorSide, "actorSide");
        return this;
    }

    @Override
    public CombatKernel.NationState attacker() {
        return attacker;
    }

    @Override
    public CombatKernel.NationState defender() {
        return defender;
    }

    @Override
    public WarType warType() {
        return war.warType();
    }

    @Override
    public boolean attackerIsOriginalAttacker() {
        return actorSide == SimSide.ATTACKER;
    }

    @Override
    public boolean attackerHasAirControl() {
        return controlOwnerMatchesPerspective(war.airSuperiorityOwner());
    }

    @Override
    public boolean defenderHasAirControl() {
        return opposingControlOwnerMatchesPerspective(war.airSuperiorityOwner());
    }

    @Override
    public boolean attackerHasGroundControl() {
        return controlOwnerMatchesPerspective(war.groundControlOwner());
    }

    @Override
    public boolean defenderHasGroundControl() {
        return opposingControlOwnerMatchesPerspective(war.groundControlOwner());
    }

    @Override
    public boolean attackerFortified() {
        return actorSide == SimSide.ATTACKER ? war.attackerFortified() : war.defenderFortified();
    }

    @Override
    public boolean defenderFortified() {
        return actorSide == SimSide.ATTACKER ? war.defenderFortified() : war.attackerFortified();
    }

    @Override
    public int attackerMaps() {
        return actorSide == SimSide.ATTACKER ? war.attackerSpendableMaps() : war.defenderSpendableMaps();
    }

    @Override
    public int defenderMaps() {
        return actorSide == SimSide.ATTACKER ? war.defenderSpendableMaps() : war.attackerSpendableMaps();
    }

    @Override
    public int attackerResistance() {
        return actorSide == SimSide.ATTACKER ? war.attackerResistance() : war.defenderResistance();
    }

    @Override
    public int defenderResistance() {
        return actorSide == SimSide.ATTACKER ? war.defenderResistance() : war.attackerResistance();
    }

    @Override
    public int blockadeOwner() {
        SimSide blockadeOwner = war.blockadeOwner();
        if (blockadeOwner == null) {
            return CombatKernel.AttackContext.BLOCKADE_NONE;
        }
        return controlOwnerMatchesPerspective(blockadeOwner)
                ? CombatKernel.AttackContext.BLOCKADE_ATTACKER
                : CombatKernel.AttackContext.BLOCKADE_DEFENDER;
    }

    private boolean controlOwnerMatchesPerspective(SimSide controlOwner) {
        if (controlOwner == null) {
            return false;
        }
        return actorSide == SimSide.ATTACKER ? controlOwner == SimSide.ATTACKER : controlOwner == SimSide.DEFENDER;
    }

    private boolean opposingControlOwnerMatchesPerspective(SimSide controlOwner) {
        if (controlOwner == null) {
            return false;
        }
        return actorSide == SimSide.ATTACKER ? controlOwner == SimSide.DEFENDER : controlOwner == SimSide.ATTACKER;
    }
}
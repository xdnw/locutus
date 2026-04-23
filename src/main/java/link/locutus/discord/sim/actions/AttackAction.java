package link.locutus.discord.sim.actions;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.SimWorld;

import java.util.Objects;

public record AttackAction(int warId, int actorNationId, AttackType attackType) implements SimAction {

    public AttackAction {
        Objects.requireNonNull(attackType, "attackType");
        if (attackType == AttackType.PEACE || attackType.isVictory()) {
            throw new IllegalArgumentException("AttackAction only supports combat attacks and FORTIFY: " + attackType);
        }
    }

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.ATTACK;
    }

    @Override
    public void apply(SimWorld world) {
        world.resolveAttack(warId, actorNationId, attackType);
    }
}

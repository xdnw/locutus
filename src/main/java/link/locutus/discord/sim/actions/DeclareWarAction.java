package link.locutus.discord.sim.actions;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.SimWorld;

import java.util.Objects;

public record DeclareWarAction(int warId, int attackerNationId, int defenderNationId, WarType warType) implements SimAction {

    public DeclareWarAction {
        Objects.requireNonNull(warType, "warType");
    }

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.DECLARE;
    }

    @Override
    public void apply(SimWorld world) {
        world.declareWar(warId, attackerNationId, defenderNationId, warType);
    }
}

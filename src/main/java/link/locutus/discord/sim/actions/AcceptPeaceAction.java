package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

public record AcceptPeaceAction(int warId, int actorNationId) implements SimAction {

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        world.requireWar(warId).acceptPeace(actorNationId);
    }
}

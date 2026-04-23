package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

public record ReleaseMapAction(int warId, int actorNationId) implements SimAction {
    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        world.releaseReservedMaps(warId, actorNationId);
    }
}
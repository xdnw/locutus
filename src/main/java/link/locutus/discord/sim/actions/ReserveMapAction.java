package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

public record ReserveMapAction(int warId, int actorNationId, int mapsToReserve) implements SimAction {

    public ReserveMapAction {
        if (mapsToReserve <= 0) {
            throw new IllegalArgumentException("mapsToReserve must be > 0");
        }
    }

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        world.reserveMaps(warId, actorNationId, mapsToReserve);
    }
}

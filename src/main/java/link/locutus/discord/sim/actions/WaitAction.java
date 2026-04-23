package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

/**
 * No-op action: the nation passes this turn or pass.
 * Used by Actor implementations to explicitly signal inactivity rather than returning an empty list,
 * and as the default action when effective activity falls below the threshold in STOCHASTIC mode.
 */
public record WaitAction(int nationId) implements SimAction {

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        // No-op: waiting produces no state changes.
        world.requireNation(nationId); // validate nation exists
    }
}

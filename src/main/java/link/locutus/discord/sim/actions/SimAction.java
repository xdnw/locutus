package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

public sealed interface SimAction permits AttackAction, SetPolicyAction, DeclareWarAction, BuyUnitsAction, ReserveMapAction, ReleaseMapAction, WaitAction {
    SimActionPhase phase();

    void apply(SimWorld world);
}

package link.locutus.discord.sim.actions;

import link.locutus.discord.sim.SimWorld;

public sealed interface SimAction permits AttackAction, SetPolicyAction, OfferPeaceAction, AcceptPeaceAction, DeclareWarAction, BuyUnitsAction, ReserveMapAction, ReleaseMapAction, WaitAction {
    SimActionPhase phase();

    void apply(SimWorld world);
}

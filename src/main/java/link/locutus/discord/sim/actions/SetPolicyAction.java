package link.locutus.discord.sim.actions;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.SimWorld;

import java.util.Objects;

public record SetPolicyAction(int nationId, WarPolicy policy) implements SimAction {

    public SetPolicyAction {
        Objects.requireNonNull(policy, "policy");
    }

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        world.requireNation(nationId).changePolicy(policy, world.tuning().policyCooldownTurns());
    }
}
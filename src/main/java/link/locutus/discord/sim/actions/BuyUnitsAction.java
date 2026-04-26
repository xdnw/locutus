package link.locutus.discord.sim.actions;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimWorld;

import java.util.Map;
import java.util.Objects;

public record BuyUnitsAction(int nationId, Map<MilitaryUnit, Integer> units) implements SimAction {

    public BuyUnitsAction {
        Objects.requireNonNull(units, "units");
        if (units.isEmpty()) {
            throw new IllegalArgumentException("units must not be empty");
        }
    }

    @Override
    public SimActionPhase phase() {
        return SimActionPhase.PRE_ACTION;
    }

    @Override
    public void apply(SimWorld world) {
        world.buyUnits(nationId, units);
    }
}

package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Exploit a ship-short target with direct naval pressure.
 */
public class ShipShortageNaval extends OffensiveWarPrimitive {

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return self.units(MilitaryUnit.SHIP) > 0;
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        SimNation defender = world.requireNation(war.defenderNationId());
        int selfShips = self.units(MilitaryUnit.SHIP);
        int defenderShips = defender.units(MilitaryUnit.SHIP);
        return selfShips - (defenderShips * 2.0);
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return new AttackAction(war.warId(), self.nationId(), AttackType.NAVAL);
    }
}
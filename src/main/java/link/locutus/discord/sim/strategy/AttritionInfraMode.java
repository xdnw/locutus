package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Pressure the defender's infrastructure once air superiority is already secured.
 */
public class AttritionInfraMode extends OffensiveWarPrimitive {

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return self.units(MilitaryUnit.AIRCRAFT) > 0
                && war.airSuperiorityOwner() == SimSide.ATTACKER;
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        SimNation defender = world.requireNation(war.defenderNationId());
        return defender.totalInfra() / 10.0;
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return new AttackAction(war.warId(), self.nationId(), AttackType.AIRSTRIKE_INFRA);
    }
}
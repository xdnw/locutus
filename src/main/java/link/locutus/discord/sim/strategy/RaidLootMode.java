package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.combat.CombatKernel;

/**
 * Convert a controlled air war into direct loot pressure.
 */
public class RaidLootMode extends OffensiveWarPrimitive {

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return CombatKernel.canUseAttackType(self, AttackType.AIRSTRIKE_MONEY)
                && war.airSuperiorityOwner() == SimSide.ATTACKER;
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        SimNation defender = world.requireNation(war.defenderNationId());
        double moneyValue = defender.resource(ResourceType.MONEY) / 100_000.0;
        double resourcesValue = defender.convertedResources() / 5_000_000.0;
        return moneyValue + resourcesValue;
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return new AttackAction(war.warId(), self.nationId(), AttackType.AIRSTRIKE_MONEY);
    }
}
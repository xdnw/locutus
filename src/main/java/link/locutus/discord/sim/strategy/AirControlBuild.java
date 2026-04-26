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
import link.locutus.discord.sim.combat.CombatKernel;

import java.util.List;

/**
 * Acquire air superiority in eligible offensive wars.
 */
public class AirControlBuild extends OffensiveWarPrimitive {

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return CombatKernel.canUseAttackType(self, AttackType.AIRSTRIKE_AIRCRAFT)
                && war.airSuperiorityOwner() != SimSide.ATTACKER;
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        SimNation defender = world.requireNation(war.defenderNationId());
        int selfAircraft = self.units(MilitaryUnit.AIRCRAFT);
        int defenderAircraft = defender.units(MilitaryUnit.AIRCRAFT);
        return (selfAircraft * 2.0) - defenderAircraft;
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return new AttackAction(war.warId(), self.nationId(), AttackType.AIRSTRIKE_AIRCRAFT);
    }
}

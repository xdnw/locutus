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
import link.locutus.discord.sim.actions.WaitAction;
import link.locutus.discord.sim.combat.CombatKernel;

import java.util.List;

/**
 * Save MAP on the pre-reset pass, then strike a high-value war on the reset turn when the window
 * opens again.
 */
public class SaveAndStrike implements StrategyPrimitive {

    private static final List<AttackType> STRIKE_PRIORITY = List.of(
            AttackType.GROUND,
            AttackType.AIRSTRIKE_AIRCRAFT,
            AttackType.NAVAL,
            AttackType.AIRSTRIKE_INFRA
    );

    @Override
    public boolean isActive(SimWorld world, SimNation self, DecisionContext ctx) {
        return selectPlan(world, self, ctx) != null;
    }

    @Override
    public List<List<SimAction>> nominate(SimWorld world, SimNation self, DecisionContext ctx) {
        Plan selection = selectPlan(world, self, ctx);
        if (selection == null) {
            return List.of();
        }
        return List.of(List.of(selection.action()));
    }

    @Override
    public double expectedDelta(SimWorld world, SimNation self, DecisionContext ctx) {
        Plan selection = selectPlan(world, self, ctx);
        return selection == null ? Double.NEGATIVE_INFINITY : selection.score();
    }

    private Plan selectPlan(SimWorld world, SimNation self, DecisionContext ctx) {
        int phase = self.dayPhaseTurn();
        if (!StrategyTiming.isSaveWindow(self) && !StrategyTiming.isStrikeWindow(self)) {
            return null;
        }

        Plan best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SimWar war : world.activeWarsForNation(self.nationId())) {
            if (war.attackerNationId() != self.nationId()) {
                continue;
            }

            AttackType strikeType = selectStrikeType(world, self, war);
            if (strikeType == null) {
                continue;
            }

            SimAction action = phase == 11
                    ? new WaitAction(self.nationId())
                    : new AttackAction(war.warId(), self.nationId(), strikeType);
            double score = scoreWar(world, self, war, phase);
            if (score > bestScore) {
                bestScore = score;
                best = new Plan(action, score);
            }
        }

        return best;
    }

    private static double scoreWar(SimWorld world, SimNation self, SimWar war, int phase) {
        SimNation defender = world.requireNation(war.defenderNationId());
        double targetValue = defender.totalInfra() / 10.0 + defender.score() / 1000.0;
        double timingBonus = phase == 11 ? 9.0 : 6.0;
        double controlBonus = war.airSuperiorityOwner() == SimSide.ATTACKER ? 2.0 : 0.0;
        double unitPressure = (self.units(MilitaryUnit.SOLDIER)
                + self.units(MilitaryUnit.TANK)
                + self.units(MilitaryUnit.AIRCRAFT)
                + self.units(MilitaryUnit.SHIP)) / 20.0;
        return targetValue + timingBonus + controlBonus + unitPressure;
    }

    private static AttackType selectStrikeType(SimWorld world, SimNation self, SimWar war) {
        SimNation defender = world.requireNation(war.defenderNationId());

        for (AttackType attackType : STRIKE_PRIORITY) {
            if (!CombatKernel.canUseAttackType(self, attackType)) {
                continue;
            }
            if (attackType == AttackType.GROUND && war.airSuperiorityOwner() != SimSide.ATTACKER) {
                continue;
            }
            if (attackType == AttackType.AIRSTRIKE_AIRCRAFT && defender.units(MilitaryUnit.AIRCRAFT) <= 0) {
                continue;
            }
            if (attackType == AttackType.NAVAL && defender.units(MilitaryUnit.SHIP) <= 0) {
                continue;
            }
            return attackType;
        }
        return null;
    }

    private record Plan(SimAction action, double score) {
    }
}

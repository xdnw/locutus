package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Focus the weakest reachable subset of wars instead of spreading effort across harder targets.
 */
public class WeakSubsetFocus extends OffensiveWarPrimitive {

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return !StrategyAttackSelection.candidateAttackTypes(self).isEmpty();
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        StrategyAttackSelection.AttackChoice choice = selectChoice(world, self, ctx, war);
        return choice == null ? Double.NEGATIVE_INFINITY : choice.score();
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        StrategyAttackSelection.AttackChoice choice = selectChoice(world, self, ctx, war);
        if (choice == null) {
            throw new IllegalStateException("No weak-subset attack available for war " + war.warId());
        }
        return new AttackAction(war.warId(), self.nationId(), choice.attackType());
    }

    private static StrategyAttackSelection.AttackChoice selectChoice(
            SimWorld world,
            SimNation self,
            DecisionContext ctx,
            SimWar war
    ) {
        SimNation defender = world.requireNation(war.defenderNationId());
        double weaknessBonus = Math.max(0d, self.score() - defender.score()) / 30.0;
        return StrategyAttackSelection.selectBestChoice(
                world,
                self,
                war,
                ctx,
                (candidateWorld, attacker, candidateWar, candidateCtx, choice) ->
                        StrategyAttackSelection.netDamageScore(choice) + weaknessBonus
        );
    }
}
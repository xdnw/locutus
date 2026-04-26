package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Strike the highest-value target early, while the score advantage is still present.
 */
public class HighScoreEarlyStrike extends OffensiveWarPrimitive {
    private static final double EARLY_TURN_LIMIT = 4.0;
    private static final double EARLY_TURN_WEIGHT = 6.0;

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
            throw new IllegalStateException("No high-score strike available for war " + war.warId());
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
        double earlyBonus = Math.max(0d, EARLY_TURN_LIMIT - ctx.turn()) * EARLY_TURN_WEIGHT;
        double targetValue = defender.score() / 30.0 + defender.totalInfra() / 80.0 + earlyBonus;
        return StrategyAttackSelection.selectBestChoice(
                world,
                self,
                war,
                ctx,
                (candidateWorld, attacker, candidateWar, candidateCtx, choice) ->
                        StrategyAttackSelection.netDamageScore(choice) + targetValue
        );
    }
}
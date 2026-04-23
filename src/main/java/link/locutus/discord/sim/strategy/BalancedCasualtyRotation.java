package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rotate casualty families across active offensive wars instead of funneling every war into the
 * same damage shape.
 */
public class BalancedCasualtyRotation extends OffensiveWarPrimitive {
    private static final double SCARCITY_WEIGHT = 60.0;

    @Override
    protected boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        return !StrategyAttackSelection.candidateAttackTypes(self).isEmpty();
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        StrategyAttackSelection.AttackChoice choice = selectChoice(world, self, ctx, war, buildSelectionContext(world, self, ctx).familyCounts());
        return choice == null ? Double.NEGATIVE_INFINITY : choice.score();
    }

    @Override
    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war, SelectionContext selectionContext) {
        RotationSelectionContext rotationSelectionContext = selectionContext instanceof RotationSelectionContext rotation
                ? rotation
                : buildSelectionContext(world, self, ctx);
        StrategyAttackSelection.AttackChoice choice = selectChoice(world, self, ctx, war, rotationSelectionContext.familyCounts());
        return choice == null ? Double.NEGATIVE_INFINITY : choice.score();
    }

    @Override
    protected SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war) {
        StrategyAttackSelection.AttackChoice choice = selectChoice(world, self, ctx, war, buildSelectionContext(world, self, ctx).familyCounts());
        if (choice == null) {
            throw new IllegalStateException("No casualty-rotation attack available for war " + war.warId());
        }
        return new AttackAction(war.warId(), self.nationId(), choice.attackType());
    }

    @Override
    protected SelectionContext prepareSelectionContext(SimWorld world, SimNation self, DecisionContext ctx) {
        return buildSelectionContext(world, self, ctx);
    }

    private static RotationSelectionContext buildSelectionContext(SimWorld world, SimNation self, DecisionContext ctx) {
        EnumMap<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts = new EnumMap<>(StrategyAttackSelection.CasualtyFamily.class);
        for (SimWar activeWar : world.activeWarsForNation(self.nationId())) {
            if (activeWar.attackerNationId() != self.nationId()) {
                continue;
            }
            StrategyAttackSelection.AttackChoice bestDamageChoice = StrategyAttackSelection.bestDamageChoice(world, self, activeWar, ctx);
            if (bestDamageChoice == null) {
                continue;
            }
            familyCounts.merge(bestDamageChoice.family(), 1, Integer::sum);
        }
        return new RotationSelectionContext(familyCounts);
    }

    private static StrategyAttackSelection.AttackChoice selectChoice(
            SimWorld world,
            SimNation self,
            DecisionContext ctx,
            SimWar war,
            Map<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts
    ) {
        return StrategyAttackSelection.selectBestChoice(
                world,
                self,
                war,
                ctx,
                (candidateWorld, attacker, candidateWar, candidateCtx, choice) ->
                        StrategyAttackSelection.netDamageScore(choice)
                                + scarcityBonus(familyCounts, choice.family())
        );
    }

    private static double scarcityBonus(
            Map<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts,
            StrategyAttackSelection.CasualtyFamily family
    ) {
        return SCARCITY_WEIGHT / (1.0 + familyCounts.getOrDefault(family, 0));
    }

    private static final class RotationSelectionContext extends SelectionContext {
        private final Map<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts;

        private RotationSelectionContext(Map<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts) {
            this.familyCounts = Map.copyOf(familyCounts);
        }

        private Map<StrategyAttackSelection.CasualtyFamily, Integer> familyCounts() {
            return familyCounts;
        }
    }
}
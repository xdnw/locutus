package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.SimAction;

import java.util.List;
import java.util.Objects;

abstract class OffensiveWarPrimitive implements StrategyPrimitive {

    @Override
    public final boolean isActive(SimWorld world, SimNation self, DecisionContext ctx) {
        return selectBestWar(world, self, ctx) != null;
    }

    @Override
    public final List<List<SimAction>> nominate(SimWorld world, SimNation self, DecisionContext ctx) {
        SelectedWar selection = selectBestWar(world, self, ctx);
        if (selection == null) {
            return List.of();
        }
        return List.of(List.of(createAction(world, self, ctx, selection.war())));
    }

    @Override
    public final double expectedDelta(SimWorld world, SimNation self, DecisionContext ctx) {
        SelectedWar selection = selectBestWar(world, self, ctx);
        return selection == null ? Double.NEGATIVE_INFINITY : selection.score();
    }

    protected abstract boolean canConsiderWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war);

    protected SelectionContext prepareSelectionContext(SimWorld world, SimNation self, DecisionContext ctx) {
        return new SelectionContext();
    }

    protected double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war, SelectionContext selectionContext) {
        return scoreWar(world, self, ctx, war);
    }

    protected abstract double scoreWar(SimWorld world, SimNation self, DecisionContext ctx, SimWar war);

    protected abstract SimAction createAction(SimWorld world, SimNation self, DecisionContext ctx, SimWar war);

    private SelectedWar selectBestWar(SimWorld world, SimNation self, DecisionContext ctx) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(ctx, "ctx");

        SelectionContext selectionContext = prepareSelectionContext(world, self, ctx);
        SelectedWar best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SimWar war : world.activeWarsForNation(self.nationId())) {
            if (war.attackerNationId() != self.nationId()) {
                continue;
            }
            if (!canConsiderWar(world, self, ctx, war)) {
                continue;
            }

            double score = scoreWar(world, self, ctx, war, selectionContext);
            if (score > bestScore) {
                bestScore = score;
                best = new SelectedWar(war, score);
            }
        }

        return best != null && best.score() > 0.0 ? best : null;
    }

    private record SelectedWar(SimWar war, double score) {
    }

    protected static class SelectionContext {
    }
}
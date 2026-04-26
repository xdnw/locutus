package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.actions.SimAction;

import java.util.List;

/**
 * Hold back one MAP on the most flexible offensive war when coordination or reset timing matters.
 */
public class MapReserveForCoordination implements StrategyPrimitive {

    @Override
    public boolean isActive(SimWorld world, SimNation self, DecisionContext ctx) {
        return selectReservation(world, self, ctx) != null;
    }

    @Override
    public List<List<SimAction>> nominate(SimWorld world, SimNation self, DecisionContext ctx) {
        Reservation selection = selectReservation(world, self, ctx);
        if (selection == null) {
            return List.of();
        }
        return List.of(List.of(new ReserveMapAction(selection.war().warId(), self.nationId(), 1)));
    }

    @Override
    public double expectedDelta(SimWorld world, SimNation self, DecisionContext ctx) {
        Reservation selection = selectReservation(world, self, ctx);
        return selection == null ? Double.NEGATIVE_INFINITY : selection.score();
    }

    private Reservation selectReservation(SimWorld world, SimNation self, DecisionContext ctx) {
        java.util.List<SimWar> activeWars = world.activeWarsForNation(self.nationId());
        int offensiveWars = 0;
        for (SimWar war : activeWars) {
            if (war.attackerNationId() == self.nationId()) {
                offensiveWars++;
            }
        }
        boolean resetWindow = StrategyTiming.isResetWindow(self);
        if (offensiveWars <= 1 && !resetWindow) {
            return null;
        }

        Reservation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SimWar war : activeWars) {
            if (war.attackerNationId() != self.nationId()) {
                continue;
            }
            int spendableMaps = war.attackerSpendableMaps();
            if (spendableMaps <= 1) {
                continue;
            }

            double score = (spendableMaps - 1)
                    + (offensiveWars > 1 ? 2.0 : 0.0)
                    + (resetWindow ? 3.0 : 0.0);
            if (score > bestScore) {
                bestScore = score;
                best = new Reservation(war, score);
            }
        }

        return best;
    }

    private record Reservation(SimWar war, double score) {
    }
}
package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.actions.SimAction;

import java.util.List;

/**
 * Preserve MAP on pressured offensive wars near reset or when enemy rebuild windows make a
 * reaction turn more valuable than immediate spending.
 */
public class MapReserveForReaction implements StrategyPrimitive {

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
        boolean resetWindow = StrategyTiming.isResetWindow(self);

        Reservation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SimWar war : activeWars) {
            if (war.attackerNationId() != self.nationId()) {
                continue;
            }

            int spendableMaps = war.attackerSpendableMaps();
            if (spendableMaps <= 0) {
                continue;
            }

            SimNation defender = world.requireNation(war.defenderNationId());
            boolean reactionPressure = hasReactionPressure(self, defender, war);
            if (!resetWindow && !reactionPressure) {
                continue;
            }

            double score = (spendableMaps - 1)
                    + (resetWindow ? 4.0 : 0.0)
                    + (reactionPressure ? 3.0 : 0.0)
                    + defender.totalInfra() / 100.0
                    + defender.units(MilitaryUnit.AIRCRAFT) / 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = new Reservation(war, score);
            }
        }

        return best;
    }

    private static boolean hasReactionPressure(SimNation self, SimNation defender, SimWar war) {
        return war.airSuperiorityOwner() != SimSide.ATTACKER
                || defender.units(MilitaryUnit.AIRCRAFT) >= self.units(MilitaryUnit.AIRCRAFT)
                || defender.units(MilitaryUnit.SHIP) > self.units(MilitaryUnit.SHIP);
    }

    private record Reservation(SimWar war, double score) {
    }
}

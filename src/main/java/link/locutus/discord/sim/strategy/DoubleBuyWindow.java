package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.combat.UnitEconomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exploit the reset boundary to queue a second buy before the next turn materializes.
 */
public class DoubleBuyWindow implements StrategyPrimitive {

    @Override
    public boolean isActive(SimWorld world, SimNation self, DecisionContext ctx) {
        return selectPurchase(world, self, ctx) != null;
    }

    @Override
    public List<List<SimAction>> nominate(SimWorld world, SimNation self, DecisionContext ctx) {
        PurchaseSelection selection = selectPurchase(world, self, ctx);
        if (selection == null) {
            return List.of();
        }
        return List.of(List.of(new BuyUnitsAction(self.nationId(), Map.of(selection.unit(), selection.amount()))));
    }

    @Override
    public double expectedDelta(SimWorld world, SimNation self, DecisionContext ctx) {
        PurchaseSelection selection = selectPurchase(world, self, ctx);
        return selection == null ? Double.NEGATIVE_INFINITY : selection.score();
    }

    private PurchaseSelection selectPurchase(SimWorld world, SimNation self, DecisionContext ctx) {
        if (!StrategyTiming.isResetWindow(self)) {
            return null;
        }

        java.util.List<SimWar> activeWars = world.activeWarsForNation(self.nationId());
        if (activeWars.isEmpty()) {
            return null;
        }

        boolean needsAir = false;
        boolean groundWindow = false;
        boolean navalWindow = false;
        for (SimWar war : activeWars) {
            if (war.attackerNationId() != self.nationId()) {
                continue;
            }
            if (war.airSuperiorityOwner() != SimSide.ATTACKER) {
                needsAir = true;
            }
            if (war.airSuperiorityOwner() == SimSide.ATTACKER) {
                groundWindow = true;
            }
            SimNation defender = world.requireNation(war.defenderNationId());
            if (self.units(MilitaryUnit.SHIP) > defender.units(MilitaryUnit.SHIP)) {
                navalWindow = true;
            }
        }

        List<MilitaryUnit> priority = new ArrayList<>(4);
        if (needsAir) {
            priority.add(MilitaryUnit.AIRCRAFT);
            priority.add(MilitaryUnit.TANK);
            priority.add(MilitaryUnit.SOLDIER);
            priority.add(MilitaryUnit.SHIP);
        } else if (groundWindow) {
            priority.add(MilitaryUnit.TANK);
            priority.add(MilitaryUnit.SOLDIER);
            priority.add(MilitaryUnit.AIRCRAFT);
            priority.add(MilitaryUnit.SHIP);
        } else if (navalWindow) {
            priority.add(MilitaryUnit.SHIP);
            priority.add(MilitaryUnit.AIRCRAFT);
            priority.add(MilitaryUnit.TANK);
            priority.add(MilitaryUnit.SOLDIER);
        } else {
            priority.add(MilitaryUnit.SOLDIER);
            priority.add(MilitaryUnit.TANK);
            priority.add(MilitaryUnit.AIRCRAFT);
            priority.add(MilitaryUnit.SHIP);
        }

        for (MilitaryUnit unit : priority) {
            int amount = maxAffordableQuantity(self, unit);
            if (amount <= 0) {
                continue;
            }
            double score = amount + 5.0;
            return new PurchaseSelection(unit, amount, score);
        }

        return null;
    }

    private static int maxAffordableQuantity(SimNation self, MilitaryUnit unit) {
        int remainingDailyCap = self.dailyBuyCap(unit) - self.unitsBoughtToday(unit);
        int remainingCapacity = self.unitCap(unit) - self.units(unit) - self.pendingBuys(unit);
        int remaining = Math.min(remainingDailyCap, remainingCapacity);
        if (remaining <= 0) {
            return 0;
        }

        double[] resources = self.resources();
        double[] costPerUnit = UnitEconomy.unitCostResources(self, unit, 1);
        int affordable = remaining;
        for (ResourceType type : ResourceType.values) {
            double cost = costPerUnit[type.ordinal()];
            if (cost <= 0.0) {
                continue;
            }
            affordable = Math.min(affordable, (int) Math.floor(resources[type.ordinal()] / cost));
        }
        return Math.max(0, Math.min(remaining, affordable));
    }

    private record PurchaseSelection(MilitaryUnit unit, int amount, double score) {
    }
}
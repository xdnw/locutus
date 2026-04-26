package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;

/**
 * Provides economic transactions and constraints during simulation.
 * 
 * Responsibilities:
 * - Unit purchase cost vectors (per-resource breakdown)
 * - Post-victory callbacks after shared nation-loot execution
 * - Blockade state changes (affecting unit restock availability)
 */
public interface EconomyProvider {
    EconomyProvider NO_OP = new EconomyProvider() {
        @Override
        public void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney) {
            // Intentionally no-op.
        }

        @Override
        public void onControlFlagChange(SimWar war) {
            // Intentionally no-op.
        }
    };

    /**
    * Observe a concluded victory after shared nation-loot execution has already run.
     * 
     * @param winner the side that achieved victory
     * @param loser the side that was defeated
     * @param war the concluded war
    * @param transferredMoney the amount of money moved by the shared victory-loot owner
     */
    void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney);

    /**
     * Handle a change in war control flags (ground control, air superiority, blockade).
     * 
     * This is called after any attack that may change control flags.
     * Implementations may use this to update blockade-dependent state like {@code canRestock}.
     * 
     * @param war the war whose control flags changed
     */
    void onControlFlagChange(SimWar war);

    /**
     * Return the per-resource cost vector for purchasing a single unit.
     * 
     * @param nation the nation making the purchase
     * @param unit the unit type being purchased
     * @return a resource cost vector indexed by {@link ResourceType#ordinal()},
     *         or null if the unit cannot be purchased
     */
    default double[] unitBuyCostPerUnit(SimNation nation, MilitaryUnit unit) {
        double[] cost = ResourceType.getBuffer();
        unit.addCost(cost, 1, 0);
        return cost;
    }
}

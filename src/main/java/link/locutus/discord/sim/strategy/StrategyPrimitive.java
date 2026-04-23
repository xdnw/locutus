package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.SimAction;

import java.util.List;

/**
 * A single strategy primitive that nominates candidate actions.
 * 
 * Primitives are composable building blocks for decision-making.
 * Each primitive represents a specific tactical concept
 * (e.g., gain air superiority, press ground offense, prepare for loss).
 * 
 * Contracts:
 * - Activation: a predicate that gates whether the primitive applies
 * - Nominate: returns zero or more candidate action sequences
 * - ExpectedDelta: returns a score estimate under the current objective
 */
public interface StrategyPrimitive {
    /**
     * Check whether this primitive can apply given the current world state.
     * Precondition for calling nominate().
     * 
     * @param world world state
     * @param self the acting nation
     * @param ctx decision context
     * @return true if this primitive is active
     */
    boolean isActive(SimWorld world, SimNation self, DecisionContext ctx);

    /**
     * Nominate one or more candidate action sequences.
     * 
     * Each element of the returned list is a sequence of actions
     * (e.g., a list [declare, attack, buy units] for a single coordinated move,
     * or a simple singleton [buy units] for a discrete action).
     * 
     * Called only if isActive() returns true.
     * 
     * @param world world state
     * @param self the acting nation
     * @param ctx decision context
     * @return a list of action sequences (possibly empty); never null
     */
    List<List<SimAction>> nominate(SimWorld world, SimNation self, DecisionContext ctx);

    /**
     * Estimate the expected objective score for the first nominated candidate.
     * Used for rapid comparison when choosing among primitives.
     * 
     * @param world world state
     * @param self the acting nation
     * @param ctx decision context
     * @return an estimated delta under the current objective
     */
    default double expectedDelta(SimWorld world, SimNation self, DecisionContext ctx) {
        return 0.0;
    }
}

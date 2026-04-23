package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

import java.util.List;

/**
 * Stateless decision policy for a nation during a simulation turn.
 * 
 * An actor is asked to produce a list of actions for the current nation
 * within the current pass of the current turn. The actor has read-only
 * access to the world state and cannot mutate it; all mutations happen
 * after the action list is returned and {@link SimWorld#apply} is called.
 * 
 * Deterministic mode:
 * - Actor.decide is called once per pass; it may return a list of actions
 *   knowing that outcomes are deterministic (MOST_LIKELY only).
 * 
 * Stochastic mode (M5+):
 * - Actor.decide is called after each applied action so realised outcomes
 *   can influence subsequent decisions.
 */
public interface Actor {
    /**
     * Produce a list of actions for this nation within the current turn and pass.
     * 
     * The returned list may be empty (nation passes) or contain multiple actions
     * (e.g., declare multiple wars, then buy units in a single pass if all are
     * PRE_ACTION / DECLARE / ATTACK within the same phase order).
     * 
     * Actions are applied in order by {@link SimWorld}; subsequent actions
     * see the mutations of prior actions.
     * 
     * @param world read-only view of the world
     * @param self the nation controlled by this actor
     * @param ctx decision context (cached neighbors, objective, turn number)
     * @return a list of actions (possibly empty); never null
     */
    List<SimAction> decide(SimWorld world, SimNation self, DecisionContext ctx);
}

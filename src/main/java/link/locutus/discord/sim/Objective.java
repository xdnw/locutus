package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/**
 * Provides a scoring metric for simulation outcomes.
 * 
 * Objectives drive decision-making in {@link Actor} implementations
 * and are used to rank candidate assignments in planners.
 * 
 * Two scoring surfaces:
 * - Terminal scoring: evaluate a final sim state (end of turn or end of sim)
 * - Step scoring: evaluate the immediate impact of a single action
 * 
 * Implementations should be deterministic and fast (called frequently during search).
 */
public interface Objective {
    /**
     * Damage objective: net damage dealt minus damage taken (in equivalent unit$).
     * Positive = winning, negative = losing.
     */
    Objective DAMAGE = new DamageObjective();

    /**
     * Score the final state of the world at the end of a simulation horizon.
     * Used for planner candidate ranking and terminal state evaluation.
     * 
     * @param world the world state
     * @param teamId the team to score (positive score = win for this team)
     * @return a score value; higher is better for the team
     */
    double scoreTerminal(SimWorld world, int teamId);

    /**
     * Score the immediate impact of a single action.
     * Used by {@link Actor} implementations to make per-turn decisions.
     * 
     * @param world the world state before action
     * @param action the action being scored
     * @param teamId the team to score
     * @return a score value; higher is better for the team
     */
    double scoreAction(SimWorld world, SimAction action, int teamId);
}

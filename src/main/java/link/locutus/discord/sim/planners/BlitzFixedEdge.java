package link.locutus.discord.sim.planners;

/**
 * Directed declaration that must survive blitz assignment.
 */
public record BlitzFixedEdge(
        int attackerNationId,
        int defenderNationId
) {
}

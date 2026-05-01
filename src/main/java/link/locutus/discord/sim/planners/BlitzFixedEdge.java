package link.locutus.discord.sim.planners;

/**
 * Directed declaration that must survive blitz assignment.
 */
public record BlitzFixedEdge(
        int attackerNationId,
                int defenderNationId,
                int warTypeOrdinal
) {
        public BlitzFixedEdge(int attackerNationId, int defenderNationId) {
                this(attackerNationId, defenderNationId, link.locutus.discord.apiv1.enums.WarType.ORD.ordinal());
        }
}

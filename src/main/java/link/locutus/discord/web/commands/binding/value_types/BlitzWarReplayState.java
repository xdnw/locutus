package link.locutus.discord.web.commands.binding.value_types;

public record BlitzWarReplayState(
        int declarerNationId,
        int targetNationId,
        int warTypeOrdinal,
        int startTurn,
        int statusOrdinal,
        int attackerMaps,
        int defenderMaps,
        int attackerResistance,
        int defenderResistance,
        int groundControlOwnerOrdinal,
        int airSuperiorityOwnerOrdinal,
        int blockadeOwnerOrdinal,
        boolean attackerFortified,
        boolean defenderFortified
) {
}
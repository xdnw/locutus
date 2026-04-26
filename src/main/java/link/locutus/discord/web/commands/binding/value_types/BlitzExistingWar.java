package link.locutus.discord.web.commands.binding.value_types;

public record BlitzExistingWar(
        int warId,
        int attackerNationId,
        int defenderNationId,
        int warTypeOrdinal,
        int warStatusOrdinal,
        int attackerMap,
        int defenderMap,
        int attackerResistance,
        int defenderResistance,
        int turnsLeft
) {
}

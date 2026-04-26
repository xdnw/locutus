package link.locutus.discord.web.commands.binding.value_types;

public record BlitzAssignedWar(
        int declarerNationId,
        int targetNationId,
        int warTypeOrdinal,
        int sourceOrdinal
) {
}

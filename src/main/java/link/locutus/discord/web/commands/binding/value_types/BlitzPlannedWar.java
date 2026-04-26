package link.locutus.discord.web.commands.binding.value_types;

public record BlitzPlannedWar(
        int declarerNationId,
        int targetNationId,
        int warTypeOrdinal,
        boolean userPinned
) {
}

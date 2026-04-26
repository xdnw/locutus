package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayConcludedWar(
        int declarerNationId,
        int targetNationId,
        int endStatusOrdinal
) {
}
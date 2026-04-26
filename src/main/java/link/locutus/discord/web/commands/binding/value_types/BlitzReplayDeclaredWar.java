package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayDeclaredWar(
        int declarerNationId,
        int targetNationId,
        int warTypeOrdinal,
        int startTurn
) {
}
package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayFrame(
        int currentTurn,
        BlitzNationReplayState[] nations,
        BlitzWarReplayState[] wars
) {
}
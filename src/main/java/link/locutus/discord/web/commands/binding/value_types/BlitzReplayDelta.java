package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayDelta(
        int turn,
        BlitzNationReplayState[] nations,
        BlitzWarReplayState[] wars,
        BlitzReplayDeclaredWar[] declaredWars,
        BlitzReplayConcludedWar[] concludedWars
) {
}
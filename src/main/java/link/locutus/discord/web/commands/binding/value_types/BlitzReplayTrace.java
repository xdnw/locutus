package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayTrace(
	BlitzReplayFrame initialFrame,
	BlitzReplayDelta[] deltas,
	BlitzPlanWarning[] warnings
) {
}

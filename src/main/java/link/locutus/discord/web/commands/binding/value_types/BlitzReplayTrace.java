package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.util.battle.BlitzWarning;

public record BlitzReplayTrace(
	BlitzReplayFrame initialFrame,
	BlitzReplayDelta[] deltas,
	BlitzWarning[] warnings
) {
}

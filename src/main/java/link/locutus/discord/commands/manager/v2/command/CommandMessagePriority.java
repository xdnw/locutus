package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.RateLimitedSources;

public final class CommandMessagePriority {
    public static final RateLimitedSource RESULT = RateLimitedSources.COMMAND_RESULT;
    public static final RateLimitedSource STATUS = RateLimitedSources.COMMAND_STATUS;
    public static final RateLimitedSource PROGRESS = RateLimitedSources.COMMAND_PROGRESS;

    private CommandMessagePriority() {
    }
}


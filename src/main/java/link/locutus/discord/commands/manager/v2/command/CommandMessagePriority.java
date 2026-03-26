package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.DeferredPriority;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.SendPolicy;

public enum CommandMessagePriority implements RateLimitedSource {
    RESULT(SendPolicy.IMMEDIATE, DeferredPriority.COMMAND_RESULT),
    STATUS(SendPolicy.DROP, DeferredPriority.COMMAND_STATUS),
    PROGRESS(SendPolicy.DROP, DeferredPriority.COMMAND_PROGRESS);

    private final SendPolicy sendPolicy;
    private final DeferredPriority deferredPriority;

    CommandMessagePriority(SendPolicy sendPolicy, DeferredPriority deferredPriority) {
        this.sendPolicy = sendPolicy;
        this.deferredPriority = deferredPriority;
    }

    @Override
    public SendPolicy sendPolicy() {
        return sendPolicy;
    }

    @Override
    public DeferredPriority deferredPriority() {
        return deferredPriority;
    }
}


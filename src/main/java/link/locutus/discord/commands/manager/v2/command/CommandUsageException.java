package link.locutus.discord.commands.manager.v2.command;

public class CommandUsageException extends IllegalArgumentException {
    private final CommandCallable cmd;

    public CommandUsageException(CommandCallable cmd, String message) {
        super(message);
        this.cmd = cmd;
    }

    public CommandCallable getCommand() {
        return cmd;
    }
}

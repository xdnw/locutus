package com.boydti.discord.commands.manager.v2.command;

public class CommandUsageException extends IllegalArgumentException {
    private final CommandCallable cmd;
    private final String desc;
    private final String help;

    public CommandUsageException(CommandCallable cmd, String message, String help, String desc) {
        super(message);
        this.help = help;
        this.desc = desc;
        this.cmd = cmd;
    }

    public CommandCallable getCommand() {
        return cmd;
    }

    public String getHelp() {
        return help;
    }

    public String getDescription() {
        return desc;
    }
}

package link.locutus.discord.commands.manager.v2.command;

public enum CommandBehavior {
    DELETE_MESSAGE(""), // delete message
    UNPRESS("~"),
    DELETE_BUTTONS("_"),
    DELETE_PRESSED_BUTTON("."),
    EPHEMERAL("="),
    ;

    private final String value;

    CommandBehavior(String value) {
        this.value = value;
    }

    public static CommandBehavior getOrNull(String prefix) {
        for (CommandBehavior cmd : values()) {
            if (cmd.value.equalsIgnoreCase(prefix)) return cmd;
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}

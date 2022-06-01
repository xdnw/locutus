package link.locutus.discord.commands.manager;

public enum CommandCategory {
    ECON,

    MILCOM,

    INTERNAL_AFFAIRS,

    FOREIGN_AFFAIRS,

    GAME_INFO_AND_TOOLS,

    GENERAL_INFO_AND_TOOLS,

    USER_COMMANDS,
    USER_SETTINGS,
    USER_INFO,

    GUILD_MANAGEMENT,

    MEMBER,
    GOV,
    ADMIN,
    LOCUTUS_ADMIN,

    DEBUG,
    FUN,

    UNCATEGORIZED

    ;

    private final CommandCategory[] children;

    CommandCategory() {
        this.children = new CommandCategory[0];
    }

    CommandCategory(CommandCategory... children) {
        this.children = children;
    }
}
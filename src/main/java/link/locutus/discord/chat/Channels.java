package link.locutus.discord.chat;

import java.util.Locale;

public enum Channels {
    GLOBAL("global"),
    ALLIANCE_ALL("alliance_all"),
    ALLIANCE_LEADERSHIP("alliance_leadership"),

    ;
    private final String name;
    Channels(String name) {
        this.name = name;
    }

    public static Channels parse(String arg) {
        if (arg.equalsIgnoreCase("all") || arg.equalsIgnoreCase("public")) {
            return GLOBAL;
        }
        if (arg.equalsIgnoreCase("alliance") || arg.equalsIgnoreCase("aa") || arg.equalsIgnoreCase("member")) {
            return Channels.ALLIANCE_ALL;
        }
        if (arg.equalsIgnoreCase("leader") || arg.equalsIgnoreCase("leadership")) {
            return Channels.ALLIANCE_LEADERSHIP;
        }
        return Channels.valueOf(arg.toUpperCase(Locale.ROOT));
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}

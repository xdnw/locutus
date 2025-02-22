package link.locutus.discord.commands.manager.v2.binding.annotation;

public enum Kw {
    // "war", "room", "sync", "channel", "update", "warcat", "category"
    WAR,
    ROOM,
    SYNC,
    CHANNEL,
    UPDATE,
    WARCAT,
    CATEGORY,
    //
    ACTIVITY,
    SHEET,
    SPREADSHEET(SHEET),
    LOGIN,
    TIMES,
    USER,
    SESSION,
    ACCESS,
    HISTORY,
    TRACK,
    RECORD,
    TIMESTAMP,
    NATION,

    ;

    private final Kw[] aliases;

    Kw(Kw... aliases) {
        this.aliases = aliases;
    }
}

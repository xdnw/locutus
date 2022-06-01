package link.locutus.discord.db.entities;

public enum CoalitionWarStatus {
    ACTIVE,
    COL1_VICTORY,
    COL1_DEFEAT,
    PEACE,
    EXPIRED,
    ;

    public static CoalitionWarStatus[] values = values();

    public static CoalitionWarStatus parse(String input) {
        return valueOf(input.toUpperCase().replace(" ", "_"));
    }
}

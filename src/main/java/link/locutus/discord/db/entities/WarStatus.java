package link.locutus.discord.db.entities;

public enum WarStatus {
    ACTIVE,
    DEFENDER_VICTORY,
    ATTACKER_VICTORY,
    PEACE,
    DEFENDER_OFFERED_PEACE,
    ATTACKER_OFFERED_PEACE,
    EXPIRED,
    ;

    public static WarStatus[] values = values();

    public static WarStatus parse(String input) {
        return valueOf(input.toUpperCase().replace(" ", "_"));
    }
}

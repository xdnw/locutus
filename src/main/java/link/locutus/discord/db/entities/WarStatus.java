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

    public static boolean[] toArray(WarStatus... statuses) {
        boolean[] warStatuses = new boolean[WarStatus.values.length];
        for (WarStatus status : statuses) {
            warStatuses[status.ordinal()] = true;
        }
        return warStatuses;
    }

    public boolean isActive() {
        return this == ACTIVE || this == DEFENDER_OFFERED_PEACE || this == ATTACKER_OFFERED_PEACE;
    }
}

package link.locutus.discord.db.entities;

/**
 * Actions that represent a treaty lifecycle change, stored as ordinal in TREATY_CHANGES.
 */
public enum TreatyChangeAction {
    SIGNED,
    EXTENDED,
    CANCELLED,
    EXPIRED,
    ;

    public static final TreatyChangeAction[] values = values();
}


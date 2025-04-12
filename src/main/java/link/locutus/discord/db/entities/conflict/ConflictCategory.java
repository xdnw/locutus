package link.locutus.discord.db.entities.conflict;

public enum ConflictCategory {
    GENERATED,
    UNVERIFIED,
    MICRO,
    NON_MICRO,
    GREAT,
    MAJOR,
    SKIRMISH
    ;

    public static final ConflictCategory[] values = values();
}

package link.locutus.discord.db.conflict;

public enum ConflictField {
    ID,
    NAME,
    CREATOR,
    WIKI,
    COL1,
    COL2,
    CATEGORY,
    CB,
    STATUS,
    START,
    END

    ;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

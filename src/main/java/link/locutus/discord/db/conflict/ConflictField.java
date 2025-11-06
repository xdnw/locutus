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
    END,

    PUSHED_PAGE,
    PUSHED_INDEX,
    PUSHED_GRAPH,

    RECALC_GRAPH,

    ;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}

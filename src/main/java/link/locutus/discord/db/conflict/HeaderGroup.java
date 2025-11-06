package link.locutus.discord.db.conflict;

public enum HeaderGroup {
    INDEX_META,
    INDEX_STATS,

    PAGE_META,
    PAGE_STATS,
    GRAPH

    ;

    public static final HeaderGroup[] values = values();

    public abstract long getHash();

}

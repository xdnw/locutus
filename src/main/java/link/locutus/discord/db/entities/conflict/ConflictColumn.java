package link.locutus.discord.db.entities.conflict;

public class ConflictColumn {
    private final String name;
    private final boolean ranking;
    private final boolean isCount;
    private final String description;
    private final ColumnType type;

    public ConflictColumn(String name, ColumnType type, boolean allowRanking, boolean isCount, String description) {
        this.name = name;
        this.type = type;
        this.ranking = allowRanking;
        this.isCount = isCount;
        this.description = description;
    }

    public ColumnType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCount() {
        return isCount;
    }

    public String getName() {
        return name;
    }

    public boolean isRanking() {
        return ranking;
    }

    public static ConflictColumn header(String name, ColumnType type, String desc, boolean isCount) {
        return new ConflictColumn(name, type, false, isCount, desc);
    }

    public static ConflictColumn ranking(String name, ColumnType type, String desc, boolean isCount) {
        return new ConflictColumn(name, type, true, isCount, desc);
    }
}

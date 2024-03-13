package link.locutus.discord.db.entities.conflict;

public class ConflictColumn {
    private final String name;
    private final boolean ranking;
    private final boolean isCount;
    private final String description;

    public ConflictColumn(String name, boolean allowRanking, boolean isCount, String description) {
        this.name = name;
        this.ranking = allowRanking;
        this.isCount = isCount;
        this.description = description;
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

    public static ConflictColumn header(String name, String desc, boolean isCount) {
        return new ConflictColumn(name, false, isCount, desc);
    }

    public static ConflictColumn ranking(String name, String desc, boolean isCount) {
        return new ConflictColumn(name, true, isCount, desc);
    }
}

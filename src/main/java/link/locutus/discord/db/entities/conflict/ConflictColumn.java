package link.locutus.discord.db.entities.conflict;

public class ConflictColumn {
    private final String name;
    private final boolean ranking;
    private final boolean isCount;

    public ConflictColumn(String name, boolean allowRanking, boolean isCount) {
        this.name = name;
        this.ranking = allowRanking;
        this.isCount = isCount;
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

    public static ConflictColumn header(String name, boolean isCount) {
        return new ConflictColumn(name, false, isCount);
    }

    public static ConflictColumn ranking(String name, boolean isCount) {
        return new ConflictColumn(name, true, isCount);
    }
}

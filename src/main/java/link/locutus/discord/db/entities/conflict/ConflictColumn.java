package link.locutus.discord.db.entities.conflict;

public class ConflictColumn {
    private final String name;
    private final boolean ranking;

    public ConflictColumn(String name, boolean allowRanking) {
        this.name = name;
        this.ranking = allowRanking;
    }

    public String getName() {
        return name;
    }

    public boolean isRanking() {
        return ranking;
    }

    public static ConflictColumn header(String name) {
        return new ConflictColumn(name, false);
    }

    public static ConflictColumn ranking(String name) {
        return new ConflictColumn(name, true);
    }
}

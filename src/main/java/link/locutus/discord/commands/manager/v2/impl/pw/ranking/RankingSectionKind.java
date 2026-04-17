package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

public enum RankingSectionKind {
    ALLIANCES,
    NATIONS,
    VICTORIES,
    LOSSES,
    EXPIRED,
    PEACE;

    public static RankingSectionKind forEntityType(RankingEntityType entityType) {
        return switch (entityType) {
            case ALLIANCE -> ALLIANCES;
            case NATION -> NATIONS;
        };
    }
}

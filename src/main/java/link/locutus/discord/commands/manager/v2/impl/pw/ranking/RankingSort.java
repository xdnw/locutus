package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

public record RankingSort(String metricKey, RankingSortDirection direction, RankingTieBreaker tieBreaker) {
}

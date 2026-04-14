package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

public record RankingMetricDescriptor(String metricKey, String label, RankingNumericType numericType, RankingValueFormat valueFormat) {
}

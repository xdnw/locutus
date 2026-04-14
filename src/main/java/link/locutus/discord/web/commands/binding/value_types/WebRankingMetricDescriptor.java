package link.locutus.discord.web.commands.binding.value_types;

public record WebRankingMetricDescriptor(
        String metricKey,
        String label,
        String numericType,
        String valueFormat
) {
}

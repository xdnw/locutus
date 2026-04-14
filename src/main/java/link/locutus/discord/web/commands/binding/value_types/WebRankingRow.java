package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public record WebRankingRow(
        WebRankingEntityRef entity,
        List<WebRankingMetricValue> metricValues,
        WebRankingNumericValue sortValue,
        boolean highlighted,
        String annotation
) {
}

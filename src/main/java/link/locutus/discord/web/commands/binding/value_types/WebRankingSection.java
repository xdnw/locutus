package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public record WebRankingSection(
        String sectionKey,
        String title,
        String entityType,
        List<WebRankingMetricDescriptor> metrics,
        WebRankingSort sort,
        List<WebRankingQueryField> metadata,
        List<String> notes,
        List<WebRankingRow> rows,
        int rowCount
) {
}

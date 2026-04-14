package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.List;
import java.util.Objects;

public record RankingSection(
        String sectionKey,
        String title,
        RankingEntityType entityType,
        List<RankingMetricDescriptor> metrics,
        RankingSort sort,
        List<RankingQueryField> metadata,
        List<String> notes,
        List<RankingRow> rows,
        int rowCount
) {
    public RankingSection {
        sectionKey = Objects.requireNonNull(sectionKey, "sectionKey");
        title = Objects.requireNonNull(title, "title");
        entityType = Objects.requireNonNull(entityType, "entityType");
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        sort = Objects.requireNonNull(sort, "sort");
        metadata = metadata == null ? List.of() : List.copyOf(metadata);
        notes = notes == null ? List.of() : List.copyOf(notes);
        rows = rows == null ? List.of() : List.copyOf(rows);
        rowCount = rows.size();
    }
}

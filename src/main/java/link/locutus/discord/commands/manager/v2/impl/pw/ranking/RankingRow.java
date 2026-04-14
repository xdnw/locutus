package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.List;
import java.util.Objects;

public record RankingRow(
        RankingEntityRef entity,
        List<RankingMetricValue> metricValues,
        RankingNumericValue sortValue,
        boolean highlighted,
        String annotation
) {
    public RankingRow {
        entity = Objects.requireNonNull(entity, "entity");
        metricValues = metricValues == null ? List.of() : List.copyOf(metricValues);
        sortValue = Objects.requireNonNull(sortValue, "sortValue");
    }
}

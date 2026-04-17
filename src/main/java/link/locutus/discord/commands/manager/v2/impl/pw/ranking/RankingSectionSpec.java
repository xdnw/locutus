package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RankingSectionSpec(
        String sectionKey,
        String sourceType,
        RankingAggregationMode aggregationMode,
        RankingSortDirection sortDirection,
        Map<Integer, BigDecimal> values,
        Map<String, Object> metadata
) {
    public RankingSectionSpec {
        sectionKey = Objects.requireNonNull(sectionKey, "sectionKey");
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        aggregationMode = Objects.requireNonNull(aggregationMode, "aggregationMode");
        sortDirection = Objects.requireNonNull(sortDirection, "sortDirection");
        values = values == null || values.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
        metadata = RankingSupport.immutableMetadata(metadata);
    }
}

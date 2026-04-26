package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RankingMultiMetricSectionSpec(
        RankingSectionKind sectionKind,
        RankingSortDirection sortDirection,
    Map<Integer, BigDecimal> sortValues,
        List<RankingColumnSpec> columns
) {
    public RankingMultiMetricSectionSpec {
        sectionKind = Objects.requireNonNull(sectionKind, "sectionKind");
        sortDirection = Objects.requireNonNull(sortDirection, "sortDirection");
        sortValues = sortValues == null ? null : Map.copyOf(sortValues);
        columns = columns == null ? List.of() : List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns cannot be empty");
        }

        Set<Integer> primaryKeys = columns.get(0).values().keySet();
        sortValues = sortValues == null ? columns.get(0).values() : sortValues;
        if (!sortValues.keySet().equals(primaryKeys)) {
            throw new IllegalArgumentException("sortValues must contain the same entity ids as the section columns");
        }
        for (RankingColumnSpec column : columns) {
            if (!column.values().keySet().equals(primaryKeys)) {
                throw new IllegalArgumentException("All columns in a section must contain the same entity ids");
            }
        }
    }
}

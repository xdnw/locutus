package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class RankingBuilders {
    private RankingBuilders() {
    }

    public static RankingSection singleMetricSection(
            String sectionKey,
            String title,
            RankingEntityType entityType,
            RankingMetricDescriptor metric,
            RankingSortDirection direction,
            Map<Integer, ? extends Number> values,
            Set<Integer> highlightedEntityIds,
            Function<Integer, String> displayHintResolver,
            List<RankingQueryField> metadata,
            List<String> notes
    ) {
        Set<Integer> highlights = highlightedEntityIds == null ? Set.of() : new IntOpenHashSet(highlightedEntityIds);
        List<RankingRow> rows = new ArrayList<>(values.size());
        for (Map.Entry<Integer, ? extends Number> entry : values.entrySet()) {
            Number value = entry.getValue();
            if (value == null || !isFinite(value)) {
                continue;
            }
            int entityId = entry.getKey();
            RankingNumericValue numericValue = RankingNumericValue.ofNumber(entry.getValue(), metric.numericType());
            RankingEntityRef entity = new RankingEntityRef(
                    entityType.key(entityId),
                    entityType,
                    entityId,
                    displayHintResolver == null ? null : displayHintResolver.apply(entityId)
            );
            rows.add(new RankingRow(
                    entity,
                    List.of(new RankingMetricValue(metric.metricKey(), numericValue)),
                    numericValue,
                    highlights.contains(entityId),
                    null
            ));
        }

        Comparator<RankingNumericValue> numericComparator = Comparator.comparing(RankingNumericValue::toBigDecimal);
        if (direction == RankingSortDirection.DESC) {
            numericComparator = numericComparator.reversed();
        }
        Comparator<RankingRow> comparator = Comparator.comparing(RankingRow::sortValue, numericComparator);
        comparator = comparator.thenComparingLong(row -> row.entity().entityId());
        rows.sort(comparator);

        return new RankingSection(
                sectionKey,
                title,
                entityType,
                List.of(metric),
                new RankingSort(metric.metricKey(), direction, RankingTieBreaker.ENTITY_ID_ASC),
                metadata == null ? List.of() : List.copyOf(metadata),
                notes == null ? List.of() : List.copyOf(notes),
                List.copyOf(rows),
                rows.size()
        );
    }

    public static int totalRowCount(List<RankingSection> sections) {
        return sections.stream().mapToInt(RankingSection::rowCount).sum();
    }

    private static boolean isFinite(Number value) {
        return switch (value) {
            case Double d -> Double.isFinite(d);
            case Float f -> Float.isFinite(f);
            default -> true;
        };
    }
}

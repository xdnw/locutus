package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RankingBuilders {
    private RankingBuilders() {
    }

    public static RankingSectionSpec singleMetricSection(
            RankingSectionKind sectionKind,
            RankingSortDirection direction,
            Map<Integer, ? extends Number> values
    ) {
        Map<Integer, BigDecimal> numericValues = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<Integer, ? extends Number> entry : values.entrySet()) {
                Number value = entry.getValue();
                if (value == null || !isFinite(value)) {
                    continue;
                }
                numericValues.put(entry.getKey(), RankingSupport.numericValue(value));
            }
        }
        return new RankingSectionSpec(sectionKind, direction, numericValues);
    }

    public static RankingColumnSpec metricColumn(
            RankingValueKind kind,
            RankingValueFormat format,
            Map<Integer, ? extends Number> values
    ) {
        Map<Integer, BigDecimal> numericValues = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<Integer, ? extends Number> entry : values.entrySet()) {
                Number value = entry.getValue();
                if (value == null || !isFinite(value)) {
                    continue;
                }
                numericValues.put(entry.getKey(), RankingSupport.numericValue(value));
            }
        }
        return new RankingColumnSpec(kind, format, numericValues);
    }

    public static RankingMultiMetricSectionSpec multiMetricSection(
            RankingSectionKind sectionKind,
            RankingSortDirection direction,
            List<RankingColumnSpec> columns
    ) {
        return new RankingMultiMetricSectionSpec(sectionKind, direction, null, columns);
    }

    public static RankingMultiMetricSectionSpec multiMetricSectionSortedBy(
            RankingSectionKind sectionKind,
            RankingSortDirection direction,
            Map<Integer, ? extends Number> sortValues,
            List<RankingColumnSpec> columns
    ) {
        Map<Integer, BigDecimal> numericSortValues = new LinkedHashMap<>();
        if (sortValues != null) {
            for (Map.Entry<Integer, ? extends Number> entry : sortValues.entrySet()) {
                Number value = entry.getValue();
                if (value == null || !isFinite(value)) {
                    continue;
                }
                numericSortValues.put(entry.getKey(), RankingSupport.numericValue(value));
            }
        }
        return new RankingMultiMetricSectionSpec(sectionKind, direction, numericSortValues, columns);
    }

    public static RankingResult singleMetricRanking(
            RankingKind kind,
            RankingEntityType keyType,
            RankingValueFormat valueFormat,
            List<RankingSectionSpec> sections,
            Set<Integer> highlightedIds,
            Long asOfMs
    ) {
        List<RankingMultiMetricSectionSpec> multiMetricSections = new ArrayList<>(sections == null ? 0 : sections.size());
        if (sections != null) {
            for (RankingSectionSpec section : sections) {
                multiMetricSections.add(multiMetricSection(
                        section.sectionKind(),
                        section.sortDirection(),
                        List.of(metricColumn(RankingValueKind.PRIMARY, valueFormat, section.values()))
                ));
            }
        }
        return multiMetricRanking(kind, keyType, multiMetricSections, highlightedIds, asOfMs);
    }

    public static RankingResult multiMetricRanking(
            RankingKind kind,
            RankingEntityType keyType,
            List<RankingMultiMetricSectionSpec> sections,
            Set<Integer> highlightedIds,
            Long asOfMs
    ) {
        List<RankingMultiMetricSectionSpec> normalizedSections = sections == null ? List.of() : List.copyOf(sections);
        Set<Integer> highlightIds = highlightedIds == null ? Set.of() : new IntOpenHashSet(highlightedIds);

        List<Long> keyIds = new ArrayList<>();
        List<List<BigDecimal>> valueColumnValues = new ArrayList<>();
        LinkedHashSet<Long> highlightedOrdered = new LinkedHashSet<>();
        List<RankingSectionRange> sectionRanges = new ArrayList<>(normalizedSections.size());

        Comparator<Map.Entry<Integer, BigDecimal>> comparator = Comparator.comparing(Map.Entry::getValue);

        List<RankingColumnSpec> referenceColumns = null;
        for (RankingMultiMetricSectionSpec section : normalizedSections) {
            int rowOffset = keyIds.size();
            Comparator<Map.Entry<Integer, BigDecimal>> sectionComparator = section.sortDirection() == RankingSortDirection.DESC
                    ? comparator.reversed()
                    : comparator;
            sectionComparator = sectionComparator.thenComparingInt(Map.Entry::getKey);

            List<Map.Entry<Integer, BigDecimal>> rows = new ArrayList<>(section.sortValues().entrySet());
            rows.sort(sectionComparator);
            sectionRanges.add(new RankingSectionRange(section.sectionKind(), rowOffset, rows.size()));

            if (referenceColumns == null) {
                referenceColumns = section.columns();
                for (RankingColumnSpec column : section.columns()) {
                    valueColumnValues.add(new ArrayList<>());
                }
            } else {
                validateColumnLayout(referenceColumns, section.columns());
            }

            for (Map.Entry<Integer, BigDecimal> row : rows) {
                long keyId = row.getKey();
                keyIds.add(keyId);
                for (int columnIndex = 0; columnIndex < section.columns().size(); columnIndex++) {
                    valueColumnValues.get(columnIndex).add(section.columns().get(columnIndex).values().get(row.getKey()));
                }
                if (highlightIds.contains(row.getKey())) {
                    highlightedOrdered.add(keyId);
                }
            }
        }

        List<RankingValueColumn> valueColumns = new ArrayList<>();
        if (referenceColumns == null) {
            valueColumns.add(new RankingValueColumn(RankingValueKind.PRIMARY, RankingValueFormat.COUNT, List.of()));
        } else {
            for (int i = 0; i < referenceColumns.size(); i++) {
                RankingColumnSpec column = referenceColumns.get(i);
                valueColumns.add(new RankingValueColumn(column.kind(), column.format(), valueColumnValues.get(i)));
            }
        }

        return new RankingResult(
                kind,
                keyType,
                keyIds,
                valueColumns,
                sectionRanges,
                new ArrayList<>(highlightedOrdered),
                asOfMs
        );
    }

    private static void validateColumnLayout(List<RankingColumnSpec> expected, List<RankingColumnSpec> actual) {
        if (expected.size() != actual.size()) {
            throw new IllegalArgumentException("All sections must expose the same number of columns");
        }
        for (int i = 0; i < expected.size(); i++) {
            RankingColumnSpec expectedColumn = expected.get(i);
            RankingColumnSpec actualColumn = actual.get(i);
            if (expectedColumn.kind() != actualColumn.kind() || expectedColumn.format() != actualColumn.format()) {
                throw new IllegalArgumentException("All sections must expose the same column kinds and formats");
            }
        }
    }

    private static boolean isFinite(Number value) {
        return switch (value) {
            case Double d -> Double.isFinite(d);
            case Float f -> Float.isFinite(f);
            default -> true;
        };
    }
}

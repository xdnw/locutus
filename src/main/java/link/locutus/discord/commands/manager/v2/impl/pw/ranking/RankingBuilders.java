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
            String sectionKey,
            String sourceType,
            RankingAggregationMode aggregationMode,
            RankingSortDirection direction,
            Map<Integer, ? extends Number> values,
            Map<String, Object> metadata
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
        return new RankingSectionSpec(sectionKey, sourceType, aggregationMode, direction, numericValues, metadata);
    }

    public static RankingResult singleMetricRanking(
            String responseKey,
            RankingEntityType key1Type,
            String valueKey,
            RankingValueFormat valueSemanticKind,
            RankingNumericType valueNumericKind,
            List<RankingSectionSpec> sections,
            Map<String, Object> queryMetadata,
            Set<Integer> highlightedKey1Ids,
            Long asOfMs,
            RankingEmptySectionPolicy emptySectionPolicy
    ) {
        List<RankingSectionSpec> normalizedSections = sections == null ? List.of() : List.copyOf(sections);
        Set<Integer> highlightIds = highlightedKey1Ids == null ? Set.of() : new IntOpenHashSet(highlightedKey1Ids);

        List<Long> key1Ids = new ArrayList<>();
        List<BigDecimal> valueColumn = new ArrayList<>();
        LinkedHashSet<Long> highlightedOrdered = new LinkedHashSet<>();
        List<String> sectionKeys = new ArrayList<>(normalizedSections.size());
        List<Integer> sectionRowOffsets = new ArrayList<>(normalizedSections.size());
        List<Integer> sectionRowCounts = new ArrayList<>(normalizedSections.size());
        List<String> sectionSourceTypes = new ArrayList<>(normalizedSections.size());
        List<RankingAggregationMode> sectionAggregationModes = new ArrayList<>(normalizedSections.size());
        List<String> sectionSortValueKeys = new ArrayList<>(normalizedSections.size());
        List<RankingSortDirection> sectionSortDirections = new ArrayList<>(normalizedSections.size());
        List<RankingTieBreaker> sectionSortTieBreakers = new ArrayList<>(normalizedSections.size());
        List<Map<String, Object>> sectionMetadataRows = new ArrayList<>(normalizedSections.size());

        Comparator<Map.Entry<Integer, BigDecimal>> comparator = Comparator
                .comparing(Map.Entry<Integer, BigDecimal>::getValue);

        for (RankingSectionSpec section : normalizedSections) {
            sectionKeys.add(section.sectionKey());
            sectionRowOffsets.add(key1Ids.size());
            sectionSourceTypes.add(section.sourceType());
            sectionAggregationModes.add(section.aggregationMode());
            sectionSortValueKeys.add(valueKey);
            sectionSortDirections.add(section.sortDirection());
            sectionSortTieBreakers.add(RankingTieBreaker.ENTITY_ID_ASC);
            sectionMetadataRows.add(section.metadata());

            Comparator<Map.Entry<Integer, BigDecimal>> sectionComparator = section.sortDirection() == RankingSortDirection.DESC
                    ? comparator.reversed()
                    : comparator;
            sectionComparator = sectionComparator.thenComparingInt(Map.Entry::getKey);

            List<Map.Entry<Integer, BigDecimal>> rows = new ArrayList<>(section.values().entrySet());
            rows.sort(sectionComparator);
            sectionRowCounts.add(rows.size());

            for (Map.Entry<Integer, BigDecimal> row : rows) {
                long key1Id = row.getKey();
                key1Ids.add(key1Id);
                valueColumn.add(row.getValue());
                if (highlightIds.contains(row.getKey())) {
                    highlightedOrdered.add(key1Id);
                }
            }
        }

        return new RankingResult(
                responseKey,
                key1Type,
                null,
                key1Ids,
                List.of(),
                List.of(valueKey),
                List.of(valueSemanticKind),
                List.of(valueNumericKind),
                List.of(valueColumn),
                sectionKeys,
                sectionRowOffsets,
                sectionRowCounts,
                sectionSourceTypes,
                sectionAggregationModes,
                sectionSortValueKeys,
                sectionSortDirections,
                sectionSortTieBreakers,
                transposeSectionMetadata(sectionMetadataRows),
                RankingSupport.immutableMetadata(queryMetadata),
                new ArrayList<>(highlightedOrdered),
                asOfMs,
                emptySectionPolicy,
                key1Ids.size()
        );
    }

    private static Map<String, List<Object>> transposeSectionMetadata(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Object>> columns = new LinkedHashMap<>();
        int rowCount = rows.size();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                columns.computeIfAbsent(key, ignored -> new ArrayList<>(java.util.Collections.nCopies(rowCount, null)));
            }
        }
        for (int i = 0; i < rowCount; i++) {
            for (Map.Entry<String, Object> entry : rows.get(i).entrySet()) {
                columns.get(entry.getKey()).set(i, entry.getValue());
            }
        }
        return columns;
    }

    private static boolean isFinite(Number value) {
        return switch (value) {
            case Double d -> Double.isFinite(d);
            case Float f -> Float.isFinite(f);
            default -> true;
        };
    }
}

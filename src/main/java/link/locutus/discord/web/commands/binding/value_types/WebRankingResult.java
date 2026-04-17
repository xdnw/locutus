package link.locutus.discord.web.commands.binding.value_types;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WebRankingResult(
        String responseKey,
        String key1Type,
        String key2Type,
        List<Long> key1Ids,
        List<Long> key2Ids,
        List<String> valueKeys,
        List<String> valueSemanticKinds,
        List<String> valueNumericKinds,
        List<List<BigDecimal>> valueColumns,
        List<String> sectionKeys,
        List<Integer> sectionRowOffsets,
        List<Integer> sectionRowCounts,
        List<String> sectionSourceTypes,
        List<String> sectionAggregationModes,
        List<String> sectionSortValueKeys,
        List<String> sectionSortDirections,
        List<String> sectionSortTieBreakers,
        Map<String, List<Object>> sectionMetadata,
        Map<String, Object> queryMetadata,
        List<Long> highlightedKey1Ids,
        Long asOfMs,
        String emptySectionPolicy,
        int rowCount
) {
    public WebRankingResult {
        responseKey = Objects.requireNonNull(responseKey, "responseKey");
        key1Type = Objects.requireNonNull(key1Type, "key1Type");
        emptySectionPolicy = Objects.requireNonNull(emptySectionPolicy, "emptySectionPolicy");
        key1Ids = immutableList(key1Ids);
        key2Ids = immutableList(key2Ids);
        valueKeys = immutableList(valueKeys);
        valueSemanticKinds = immutableList(valueSemanticKinds);
        valueNumericKinds = immutableList(valueNumericKinds);
        valueColumns = immutableValueColumns(valueColumns);
        sectionKeys = immutableList(sectionKeys);
        sectionRowOffsets = immutableList(sectionRowOffsets);
        sectionRowCounts = immutableList(sectionRowCounts);
        sectionSourceTypes = immutableList(sectionSourceTypes);
        sectionAggregationModes = immutableList(sectionAggregationModes);
        sectionSortValueKeys = immutableList(sectionSortValueKeys);
        sectionSortDirections = immutableList(sectionSortDirections);
        sectionSortTieBreakers = immutableList(sectionSortTieBreakers);
        sectionMetadata = immutableSectionMetadata(sectionMetadata);
        queryMetadata = immutableScalarMap(queryMetadata);
        highlightedKey1Ids = immutableList(highlightedKey1Ids);
        rowCount = key1Ids.size();

        if (!key2Ids.isEmpty() && key2Ids.size() != rowCount) {
            throw new IllegalArgumentException("key2Ids must be empty or match key1Ids size");
        }
        if (valueSemanticKinds.size() != valueKeys.size()) {
            throw new IllegalArgumentException("valueSemanticKinds must match valueKeys size");
        }
        if (valueNumericKinds.size() != valueKeys.size()) {
            throw new IllegalArgumentException("valueNumericKinds must match valueKeys size");
        }
        if (valueColumns.size() != valueKeys.size()) {
            throw new IllegalArgumentException("valueColumns must match valueKeys size");
        }
        for (List<BigDecimal> column : valueColumns) {
            if (column.size() != rowCount) {
                throw new IllegalArgumentException("Each value column must match key1Ids size");
            }
        }

        int sectionCount = sectionKeys.size();
        if (sectionRowOffsets.size() != sectionCount
                || sectionRowCounts.size() != sectionCount
                || sectionSourceTypes.size() != sectionCount
                || sectionAggregationModes.size() != sectionCount
                || sectionSortValueKeys.size() != sectionCount
                || sectionSortDirections.size() != sectionCount
                || sectionSortTieBreakers.size() != sectionCount) {
            throw new IllegalArgumentException("Section metadata lists must match sectionKeys size");
        }
        for (Map.Entry<String, List<Object>> entry : sectionMetadata.entrySet()) {
            if (entry.getValue().size() != sectionCount) {
                throw new IllegalArgumentException("Section metadata column `" + entry.getKey() + "` must match sectionKeys size");
            }
        }
        int totalRows = 0;
        for (Integer count : sectionRowCounts) {
            totalRows += count;
        }
        if (totalRows != rowCount) {
            throw new IllegalArgumentException("Section row counts must sum to rowCount");
        }
    }

    private static <T> List<T> immutableList(List<? extends T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<List<BigDecimal>> immutableValueColumns(List<List<BigDecimal>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<BigDecimal>> copy = new ArrayList<>(source.size());
        for (List<BigDecimal> column : source) {
            copy.add(immutableList(column));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, List<Object>> immutableSectionMetadata(Map<String, List<Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Object>> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, List<Object>> entry : source.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "sectionMetadata key"), immutableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> immutableScalarMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "queryMetadata key"), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}

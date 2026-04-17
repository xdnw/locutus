package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WebRankingAdapter {
    private WebRankingAdapter() {
    }

    public static WebRankingResult toWeb(RankingResult result) {
        Objects.requireNonNull(result, "result");
        String key1Type = resolveKey1Type(result.sections());
        List<MetricColumn> metricColumns = collectMetricColumns(result.sections());

        List<String> valueKeys = new ArrayList<>(metricColumns.size());
        List<String> valueSemanticKinds = new ArrayList<>(metricColumns.size());
        List<String> valueNumericKinds = new ArrayList<>(metricColumns.size());
        List<List<BigDecimal>> valueColumns = new ArrayList<>(metricColumns.size());
        for (MetricColumn column : metricColumns) {
            valueKeys.add(column.key());
            valueSemanticKinds.add(column.semanticKind());
            valueNumericKinds.add(column.numericKind());
            valueColumns.add(new ArrayList<>(result.rowCount()));
        }

        List<Long> key1Ids = new ArrayList<>(result.rowCount());
        LinkedHashSet<Long> highlightedKey1Ids = new LinkedHashSet<>();
        List<String> sectionKeys = new ArrayList<>(result.sections().size());
        List<Integer> sectionRowOffsets = new ArrayList<>(result.sections().size());
        List<Integer> sectionRowCounts = new ArrayList<>(result.sections().size());
        List<String> sectionSortValueKeys = new ArrayList<>(result.sections().size());
        List<String> sectionSortDirections = new ArrayList<>(result.sections().size());
        List<String> sectionSortTieBreakers = new ArrayList<>(result.sections().size());
        List<Map<String, Object>> sectionMetadataBySection = new ArrayList<>(result.sections().size());

        for (RankingSection section : result.sections()) {
            sectionKeys.add(section.sectionKey());
            sectionRowOffsets.add(key1Ids.size());
            sectionRowCounts.add(section.rowCount());
            sectionSortValueKeys.add(section.sort().metricKey());
            sectionSortDirections.add(section.sort().direction().name());
            sectionSortTieBreakers.add(section.sort().tieBreaker().name());
            sectionMetadataBySection.add(coerceMetadata(section.metadata()));

            for (RankingRow row : section.rows()) {
                key1Ids.add(row.entity().entityId());
                if (row.highlighted()) {
                    highlightedKey1Ids.add(row.entity().entityId());
                }

                Map<String, BigDecimal> valuesByKey = new HashMap<>(row.metricValues().size() + 1);
                for (RankingMetricValue metricValue : row.metricValues()) {
                    valuesByKey.put(metricValue.metricKey(), metricValue.value().toBigDecimal());
                }
                valuesByKey.putIfAbsent(section.sort().metricKey(), row.sortValue().toBigDecimal());

                for (int i = 0; i < metricColumns.size(); i++) {
                    valueColumns.get(i).add(valuesByKey.get(metricColumns.get(i).key()));
                }
            }
        }

        return new WebRankingResult(
                result.responseKey(),
                key1Type,
                null,
                key1Ids,
                List.of(),
                valueKeys,
                valueSemanticKinds,
                valueNumericKinds,
                valueColumns,
                sectionKeys,
                sectionRowOffsets,
                sectionRowCounts,
                sectionSortValueKeys,
                sectionSortDirections,
                sectionSortTieBreakers,
                transposeSectionMetadata(sectionMetadataBySection),
                coerceMetadata(result.querySummary()),
                new ArrayList<>(highlightedKey1Ids),
                result.asOfMs(),
                result.emptySectionPolicy().name(),
                result.rowCount()
        );
    }

    private static String resolveKey1Type(List<RankingSection> sections) {
        RankingEntityType type = null;
        for (RankingSection section : sections) {
            if (type == null) {
                type = section.entityType();
            } else if (type != section.entityType()) {
                throw new IllegalArgumentException("Flat ranking payload requires one key1Type per result");
            }
        }
        if (type == null) {
            throw new IllegalArgumentException("Flat ranking payload requires at least one section to resolve key1Type");
        }
        return type.name();
    }

    private static List<MetricColumn> collectMetricColumns(List<RankingSection> sections) {
        Map<String, MetricColumn> columns = new LinkedHashMap<>();
        for (RankingSection section : sections) {
            for (RankingMetricDescriptor metric : section.metrics()) {
                mergeColumn(
                        columns,
                        new MetricColumn(metric.metricKey(), metric.valueFormat().name(), metric.numericType().name())
                );
            }
            if (!columns.containsKey(section.sort().metricKey())) {
                MetricColumn sortColumn = sortColumn(section);
                if (sortColumn != null) {
                    mergeColumn(columns, sortColumn);
                }
            }
        }
        return List.copyOf(columns.values());
    }

    private static MetricColumn sortColumn(RankingSection section) {
        for (RankingMetricDescriptor metric : section.metrics()) {
            if (metric.metricKey().equals(section.sort().metricKey())) {
                return new MetricColumn(metric.metricKey(), metric.valueFormat().name(), metric.numericType().name());
            }
        }
        if (section.rows().isEmpty()) {
            return null;
        }
        return new MetricColumn(section.sort().metricKey(), RankingValueFormat.NUMBER.name(), section.rows().get(0).sortValue().numericType().name());
    }

    private static void mergeColumn(Map<String, MetricColumn> columns, MetricColumn candidate) {
        MetricColumn existing = columns.get(candidate.key());
        if (existing == null) {
            columns.put(candidate.key(), candidate);
            return;
        }
        if (!existing.equals(candidate)) {
            throw new IllegalArgumentException("Metric column `" + candidate.key() + "` has conflicting metadata across sections");
        }
    }

    private static Map<String, Object> coerceMetadata(List<RankingQueryField> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>(fields.size());
        for (RankingQueryField field : fields) {
            metadata.put(field.key(), coerceScalar(field.value()));
        }
        return Collections.unmodifiableMap(metadata);
    }

    private static Map<String, List<Object>> transposeSectionMetadata(List<Map<String, Object>> sectionMetadataBySection) {
        if (sectionMetadataBySection.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Object>> transposed = new LinkedHashMap<>();
        int sectionCount = sectionMetadataBySection.size();
        for (Map<String, Object> metadata : sectionMetadataBySection) {
            for (String key : metadata.keySet()) {
                transposed.computeIfAbsent(key, ignored -> new ArrayList<>(Collections.nCopies(sectionCount, null)));
            }
        }
        for (int i = 0; i < sectionCount; i++) {
            for (Map.Entry<String, Object> entry : sectionMetadataBySection.get(i).entrySet()) {
                transposed.get(entry.getKey()).set(i, entry.getValue());
            }
        }
        return transposed;
    }

    private static Object coerceScalar(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private record MetricColumn(String key, String semanticKind, String numericKind) {
    }
}

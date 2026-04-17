package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

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
        Map<Integer, Double> numericValues = new LinkedHashMap<>();
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

    public static RankingResult singleMetricRanking(
            RankingKind kind,
            RankingEntityType key1Type,
            RankingValueFormat valueFormat,
            List<RankingSectionSpec> sections,
            Set<Integer> highlightedKey1Ids,
            Long asOfMs
    ) {
        List<RankingSectionSpec> normalizedSections = sections == null ? List.of() : List.copyOf(sections);
        Set<Integer> highlightIds = highlightedKey1Ids == null ? Set.of() : new IntOpenHashSet(highlightedKey1Ids);

        List<Long> key1Ids = new ArrayList<>();
        List<Double> valueColumn = new ArrayList<>();
        LinkedHashSet<Long> highlightedOrdered = new LinkedHashSet<>();
        List<RankingSectionRange> sectionRanges = new ArrayList<>(normalizedSections.size());

        Comparator<Map.Entry<Integer, Double>> comparator = Comparator.comparingDouble(Map.Entry::getValue);

        for (RankingSectionSpec section : normalizedSections) {
            int rowOffset = key1Ids.size();
            Comparator<Map.Entry<Integer, Double>> sectionComparator = section.sortDirection() == RankingSortDirection.DESC
                    ? comparator.reversed()
                    : comparator;
            sectionComparator = sectionComparator.thenComparingInt(Map.Entry::getKey);

            List<Map.Entry<Integer, Double>> rows = new ArrayList<>(section.values().entrySet());
            rows.sort(sectionComparator);
            sectionRanges.add(new RankingSectionRange(section.sectionKind(), rowOffset, rows.size()));

            for (Map.Entry<Integer, Double> row : rows) {
                long key1Id = row.getKey();
                key1Ids.add(key1Id);
                valueColumn.add(row.getValue());
                if (highlightIds.contains(row.getKey())) {
                    highlightedOrdered.add(key1Id);
                }
            }
        }

        return new RankingResult(
                kind,
                key1Type,
                null,
                key1Ids,
                List.of(),
                List.of(new RankingValueColumn(valueFormat, valueColumn)),
                sectionRanges,
                new ArrayList<>(highlightedOrdered),
                asOfMs
        );
    }

    private static boolean isFinite(Number value) {
        return switch (value) {
            case Double d -> Double.isFinite(d);
            case Float f -> Float.isFinite(f);
            default -> true;
        };
    }
}

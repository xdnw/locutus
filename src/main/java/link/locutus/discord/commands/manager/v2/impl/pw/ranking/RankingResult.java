package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record RankingResult(
        RankingKind kind,
        RankingEntityType key1Type,
        RankingEntityType key2Type,
        List<Long> key1Ids,
        List<Long> key2Ids,
        List<RankingValueColumn> valueColumns,
        List<RankingSectionRange> sectionRanges,
        List<Long> highlightedKey1Ids,
        Long asOfMs
) {
    public RankingResult {
        kind = Objects.requireNonNull(kind, "kind");
        key1Type = Objects.requireNonNull(key1Type, "key1Type");
        key1Ids = immutableList(key1Ids);
        key2Ids = immutableList(key2Ids);
        valueColumns = immutableList(valueColumns);
        sectionRanges = immutableList(sectionRanges);
        highlightedKey1Ids = immutableList(highlightedKey1Ids);

        int rowCount = key1Ids.size();
        if (!key2Ids.isEmpty() && key2Ids.size() != rowCount) {
            throw new IllegalArgumentException("key2Ids must be empty or match key1Ids size");
        }
        if (valueColumns.isEmpty()) {
            throw new IllegalArgumentException("valueColumns cannot be empty");
        }
        for (RankingValueColumn column : valueColumns) {
            if (column.values().size() != rowCount) {
                throw new IllegalArgumentException("Each value column must match key1Ids size");
            }
        }

        int totalRows = 0;
        for (RankingSectionRange section : sectionRanges) {
            totalRows += section.rowCount();
            if (section.rowOffset() > rowCount) {
                throw new IllegalArgumentException("Section rowOffset cannot exceed rowCount");
            }
            if (section.rowOffset() + section.rowCount() > rowCount) {
                throw new IllegalArgumentException("Section row range must stay within rowCount");
            }
        }
        if (totalRows != rowCount) {
            throw new IllegalArgumentException("Section row counts must sum to rowCount");
        }
    }

    public int rowCount() {
        return key1Ids.size();
    }

    private static <T> List<T> immutableList(List<? extends T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}

package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RankingResultSupport {
    private RankingResultSupport() {
    }

    public static <T> List<T> immutableList(List<? extends T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public static void validate(
            List<Long> keyIds,
            List<RankingValueColumn> valueColumns,
            List<RankingSectionRange> sectionRanges,
            List<Long> highlightedIds,
            Long asOfMs
    ) {
        int rowCount = keyIds.size();
        if (valueColumns.isEmpty()) {
            throw new IllegalArgumentException("valueColumns cannot be empty");
        }
        for (RankingValueColumn column : valueColumns) {
            if (column.values().size() != rowCount) {
                throw new IllegalArgumentException("Each value column must match keyIds size");
            }
        }

        int expectedOffset = 0;
        for (RankingSectionRange section : sectionRanges) {
            if (section.rowOffset() != expectedOffset) {
                throw new IllegalArgumentException("Section ranges must be contiguous and ordered");
            }
            expectedOffset += section.rowCount();
        }
        if (expectedOffset != rowCount) {
            throw new IllegalArgumentException("Section row counts must sum to rowCount");
        }

        Set<Long> highlighted = new HashSet<>(highlightedIds);
        if (highlighted.size() != highlightedIds.size()) {
            throw new IllegalArgumentException("highlightedIds cannot contain duplicates");
        }
        if (!highlighted.isEmpty()) {
            Set<Long> available = new HashSet<>(keyIds);
            if (!available.containsAll(highlighted)) {
                throw new IllegalArgumentException("highlightedIds must exist in keyIds");
            }
        }
        if (asOfMs != null && asOfMs < 0L) {
            throw new IllegalArgumentException("asOfMs cannot be negative");
        }
    }
}

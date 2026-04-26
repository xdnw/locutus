package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.List;
import java.util.Objects;

public record RankingResult(
        RankingKind kind,
        RankingEntityType keyType,
        List<Long> keyIds,
        List<RankingValueColumn> valueColumns,
        List<RankingSectionRange> sectionRanges,
        List<Long> highlightedIds,
        Long asOfMs
) {
    public RankingResult {
        kind = Objects.requireNonNull(kind, "kind");
        keyType = Objects.requireNonNull(keyType, "keyType");
        keyIds = RankingResultSupport.immutableList(keyIds);
        valueColumns = RankingResultSupport.immutableList(valueColumns);
        sectionRanges = RankingResultSupport.immutableList(sectionRanges);
        highlightedIds = RankingResultSupport.immutableList(highlightedIds);
        RankingResultSupport.validate(keyIds, valueColumns, sectionRanges, highlightedIds, asOfMs);
    }

    public int rowCount() {
        return keyIds.size();
    }
}

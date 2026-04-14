package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.List;
import java.util.Objects;

public record RankingResult(
        String responseKey,
        String title,
        List<RankingQueryField> querySummary,
        int rowCount,
        Long asOfMs,
        RankingEmptySectionPolicy emptySectionPolicy,
        List<RankingSection> sections
) {
    public RankingResult {
        responseKey = Objects.requireNonNull(responseKey, "responseKey");
        title = Objects.requireNonNull(title, "title");
        querySummary = querySummary == null ? List.of() : List.copyOf(querySummary);
        emptySectionPolicy = Objects.requireNonNull(emptySectionPolicy, "emptySectionPolicy");
        sections = sections == null ? List.of() : List.copyOf(sections);
        rowCount = sections.stream().mapToInt(RankingSection::rowCount).sum();
    }
}

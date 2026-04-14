package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public record WebRankingResult(
        String responseKey,
        String title,
        List<WebRankingQueryField> querySummary,
        int rowCount,
        Long asOfMs,
        String emptySectionPolicy,
        List<WebRankingSection> sections
) {
}

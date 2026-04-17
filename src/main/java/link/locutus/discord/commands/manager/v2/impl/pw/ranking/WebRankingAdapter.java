package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;

public final class WebRankingAdapter {
    private WebRankingAdapter() {
    }

    public static WebRankingResult toWeb(RankingResult result) {
        return new WebRankingResult(
                result.kind(),
                result.key1Type(),
                result.key2Type(),
                result.key1Ids(),
                result.key2Ids(),
                result.valueColumns(),
                result.sectionRanges(),
                result.highlightedKey1Ids(),
                result.asOfMs()
        );
    }
}

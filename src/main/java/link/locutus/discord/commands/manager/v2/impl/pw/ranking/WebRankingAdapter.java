package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;

public final class WebRankingAdapter {
    private WebRankingAdapter() {
    }

    public static WebRankingResult toWeb(RankingResult result) {
        return new WebRankingResult(
                result.responseKey(),
                result.key1Type().name(),
                result.key2Type() == null ? null : result.key2Type().name(),
                result.key1Ids(),
                result.key2Ids(),
                result.valueKeys(),
                result.valueSemanticKinds().stream().map(Enum::name).toList(),
                result.valueNumericKinds().stream().map(Enum::name).toList(),
                result.valueColumns(),
                result.sectionKeys(),
                result.sectionRowOffsets(),
                result.sectionRowCounts(),
                result.sectionSourceTypes(),
                result.sectionAggregationModes().stream().map(Enum::name).toList(),
                result.sectionSortValueKeys(),
                result.sectionSortDirections().stream().map(Enum::name).toList(),
                result.sectionSortTieBreakers().stream().map(Enum::name).toList(),
                result.sectionMetadata(),
                result.queryMetadata(),
                result.highlightedKey1Ids(),
                result.asOfMs(),
                result.emptySectionPolicy().name(),
                result.rowCount()
        );
    }
}

package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingBuildersTest {
    @Test
    void singleMetricRankingSortsByExactValueThenEntityIdAndMarksHighlights() {
        Map<Integer, BigInteger> values = new LinkedHashMap<>();
        values.put(5, new BigInteger("9007199254740993"));
        values.put(2, new BigInteger("9007199254740993"));
        values.put(3, BigInteger.ONE);

        RankingResult result = RankingBuilders.singleMetricRanking(
                "fixture",
                RankingEntityType.ALLIANCE,
                "count",
                RankingValueFormat.COUNT,
                RankingNumericType.INTEGER,
                List.of(RankingBuilders.singleMetricSection(
                        "entities",
                        RankingEntityType.ALLIANCE.name(),
                        RankingAggregationMode.IDENTITY,
                        RankingSortDirection.DESC,
                        values,
                        Map.of()
                )),
                Map.of("metric", "count"),
                Set.of(5),
                1L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );

        assertEquals(List.of(2L, 5L, 3L), result.key1Ids());
        assertEquals(new BigDecimal("9007199254740993"), result.valueColumns().get(0).get(0));
        assertEquals(List.of(5L), result.highlightedKey1Ids());
        assertEquals(List.of("entities"), result.sectionKeys());
    }

    @Test
    void numericValuesAndRowCountsAreCanonicalizedAtTheSharedSeam() {
        RankingResult result = RankingBuilders.singleMetricRanking(
                "fixture",
                RankingEntityType.ALLIANCE,
                "count",
                RankingValueFormat.COUNT,
                RankingNumericType.INTEGER,
                List.of(RankingBuilders.singleMetricSection(
                        "entities",
                        RankingEntityType.ALLIANCE.name(),
                        RankingAggregationMode.IDENTITY,
                        RankingSortDirection.DESC,
                        Map.of(7, 1.0d),
                        Map.of()
                )),
                Map.of("sample", 1.0d),
                Set.of(),
                1L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );

        assertEquals(1, result.rowCount());
        assertEquals(new BigDecimal("1"), result.valueColumns().get(0).get(0));
        assertEquals(new BigDecimal("1"), result.queryMetadata().get("sample"));
    }
}

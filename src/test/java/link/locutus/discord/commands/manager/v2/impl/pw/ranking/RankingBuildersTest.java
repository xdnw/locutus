package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingBuildersTest {
    @Test
    void singleMetricRankingSortsByExactValueThenEntityIdAndMarksHighlights() {
        Map<Integer, BigInteger> values = new LinkedHashMap<>();
        values.put(5, new BigInteger("9007199254740993"));
        values.put(2, new BigInteger("9007199254740993"));
        values.put(3, BigInteger.ONE);

        RankingResult result = RankingBuilders.singleMetricRanking(
                RankingKind.RECRUITMENT,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.COUNT,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        values
                )),
                Set.of(5),
                1L
        );

        assertEquals(List.of(2L, 5L, 3L), result.key1Ids());
        assertEquals(new BigInteger("9007199254740993").doubleValue(), result.valueColumns().get(0).values().get(0));
        assertEquals(List.of(5L), result.highlightedKey1Ids());
        assertEquals(List.of(new RankingSectionRange(RankingSectionKind.ALLIANCES, 0, 3)), result.sectionRanges());
    }

    @Test
    void numericValuesAndRowCountsAreCanonicalizedAtTheSharedSeam() {
        RankingResult result = RankingBuilders.singleMetricRanking(
                RankingKind.WAR_COUNT,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.COUNT,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        Map.of(7, 1.0d)
                )),
                Set.of(),
                1L
        );

        assertEquals(1, result.rowCount());
        assertEquals(1d, result.valueColumns().get(0).values().get(0));
        assertEquals(RankingValueFormat.COUNT, result.valueColumns().get(0).format());
    }
}

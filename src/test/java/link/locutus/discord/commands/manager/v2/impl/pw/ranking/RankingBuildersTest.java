package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertEquals(List.of(2L, 5L, 3L), result.keyIds());
        assertEquals(RankingValueKind.PRIMARY, result.valueColumns().get(0).kind());
        assertEquals(new BigDecimal("9007199254740993"), result.valueColumns().get(0).values().get(0));
        assertEquals(List.of(5L), result.highlightedIds());
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
        assertEquals(new BigDecimal("1.0"), result.valueColumns().get(0).values().get(0));
        assertEquals(RankingValueKind.PRIMARY, result.valueColumns().get(0).kind());
        assertEquals(RankingValueFormat.COUNT, result.valueColumns().get(0).format());
    }

    @Test
    void multiMetricRankingAlignsSecondaryColumnsToPrimarySortOrder() {
        RankingResult result = RankingBuilders.multiMetricRanking(
                RankingKind.TRADE_FLOW,
                RankingEntityType.NATION,
                List.of(RankingBuilders.multiMetricSection(
                        RankingSectionKind.NATIONS,
                        RankingSortDirection.DESC,
                        List.of(
                                RankingBuilders.metricColumn(RankingValueKind.AMOUNT, RankingValueFormat.COUNT, Map.of(7, 20, 5, 10)),
                                RankingBuilders.metricColumn(RankingValueKind.PRICE_PER_UNIT, RankingValueFormat.MONEY, Map.of(7, 80, 5, 60))
                        )
                )),
                Set.of(),
                1L
        );

        assertEquals(List.of(7L, 5L), result.keyIds());
        assertEquals(RankingValueKind.AMOUNT, result.valueColumns().get(0).kind());
        assertEquals(List.of(new BigDecimal("20"), new BigDecimal("10")), result.valueColumns().get(0).values());
        assertEquals(RankingValueKind.PRICE_PER_UNIT, result.valueColumns().get(1).kind());
        assertEquals(List.of(new BigDecimal("80"), new BigDecimal("60")), result.valueColumns().get(1).values());
    }

    @Test
    void multiMetricRankingCanSortBySeparateBuilderOnlyMetric() {
        RankingResult result = RankingBuilders.multiMetricRanking(
                RankingKind.TRADE_FLOW,
                RankingEntityType.NATION,
                List.of(RankingBuilders.multiMetricSectionSortedBy(
                        RankingSectionKind.NATIONS,
                        RankingSortDirection.DESC,
                        Map.of(5, 999, 7, 100),
                        List.of(
                                RankingBuilders.metricColumn(RankingValueKind.AMOUNT, RankingValueFormat.COUNT, Map.of(7, 20, 5, 10)),
                                RankingBuilders.metricColumn(RankingValueKind.PRICE_PER_UNIT, RankingValueFormat.MONEY, Map.of(7, 80, 5, 60))
                        )
                )),
                Set.of(),
                1L
        );

        assertEquals(List.of(5L, 7L), result.keyIds());
        assertEquals(List.of(new BigDecimal("10"), new BigDecimal("20")), result.valueColumns().get(0).values());
        assertEquals(List.of(new BigDecimal("60"), new BigDecimal("80")), result.valueColumns().get(1).values());
    }

    @Test
    void resultRejectsHighlightedIdsThatDoNotExistInRows() {
        assertThrows(IllegalArgumentException.class, () -> new RankingResult(
                RankingKind.WAR_COUNT,
                RankingEntityType.ALLIANCE,
                List.of(7L),
                List.of(new RankingValueColumn(RankingValueKind.PRIMARY, RankingValueFormat.COUNT, List.of(new BigDecimal("1")))),
                List.of(new RankingSectionRange(RankingSectionKind.ALLIANCES, 0, 1)),
                List.of(9L),
                1L
        ));
    }

    @Test
    void resultRejectsNonContiguousSectionRanges() {
        assertThrows(IllegalArgumentException.class, () -> new RankingResult(
                RankingKind.WAR_STATUS,
                RankingEntityType.NATION,
                List.of(7L, 8L),
                List.of(new RankingValueColumn(RankingValueKind.PRIMARY, RankingValueFormat.COUNT, List.of(new BigDecimal("1"), new BigDecimal("2")))),
                List.of(
                        new RankingSectionRange(RankingSectionKind.VICTORIES, 0, 1),
                        new RankingSectionRange(RankingSectionKind.LOSSES, 2, 1)
                ),
                List.of(),
                1L
        ));
    }
}
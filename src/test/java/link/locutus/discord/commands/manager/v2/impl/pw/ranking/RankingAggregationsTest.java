package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingAggregationsTest {
    @Test
    void aggregateByGroupSupportsSumAndAverage() {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(11, 10);
        values.put(12, 20);
        values.put(21, 9);

        Map<Integer, Double> sums = RankingAggregations.aggregateByGroup(
                values,
                nationId -> nationId < 20 ? 1 : 2,
                RankingAggregationMode.SUM
        );
        Map<Integer, Double> averages = RankingAggregations.aggregateByGroup(
                values,
                nationId -> nationId < 20 ? 1 : 2,
                RankingAggregationMode.AVERAGE
        );

        assertEquals(Map.of(1, 30d, 2, 9d), sums);
        assertEquals(Map.of(1, 15d, 2, 9d), averages);
    }

    @Test
    void aggregateByGroupSkipsEntriesWithoutAGroup() {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(11, 10);
        values.put(12, 20);

        Map<Integer, Double> sums = RankingAggregations.aggregateByGroup(
                values,
                nationId -> nationId == 11 ? 1 : null,
                RankingAggregationMode.SUM
        );

        assertEquals(Map.of(1, 10d), sums);
    }
}

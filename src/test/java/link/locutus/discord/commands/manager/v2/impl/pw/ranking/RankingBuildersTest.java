package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingBuildersTest {
    @Test
    void singleMetricSectionSortsByExactValueThenEntityIdAndMarksHighlights() {
        RankingMetricDescriptor metric = new RankingMetricDescriptor("count", "Count", RankingNumericType.INTEGER, RankingValueFormat.COUNT);
        Map<Integer, BigInteger> values = new LinkedHashMap<>();
                values.put(5, new BigInteger("9007199254740993"));
                values.put(2, new BigInteger("9007199254740993"));
        values.put(3, BigInteger.ONE);

        RankingSection section = RankingBuilders.singleMetricSection(
                "entities",
                "Entities",
                RankingEntityType.ALLIANCE,
                metric,
                RankingSortDirection.DESC,
                values,
                Set.of(5),
                id -> "Entity " + id,
                List.of(),
                List.of()
        );

        assertEquals(List.of(2L, 5L, 3L), section.rows().stream().map(row -> row.entity().entityId()).toList());
        assertEquals("9007199254740993", section.rows().get(0).sortValue().exactValue());
                assertTrue(section.rows().get(1).highlighted());
        assertEquals("alliance:2", section.rows().get(0).entity().entityKey());
    }

    @Test
    void numericValuesAndRowCountsAreCanonicalizedAtTheSharedSeam() {
        RankingNumericValue numericValue = RankingNumericValue.ofNumber(1.0d, RankingNumericType.INTEGER);
        RankingRow row = new RankingRow(
                new RankingEntityRef("alliance:7", RankingEntityType.ALLIANCE, 7, "Seven"),
                List.of(new RankingMetricValue("count", numericValue)),
                numericValue,
                false,
                null
        );
        RankingSection section = new RankingSection(
                "entities",
                "Entities",
                RankingEntityType.ALLIANCE,
                List.of(new RankingMetricDescriptor("count", "Count", RankingNumericType.INTEGER, RankingValueFormat.COUNT)),
                new RankingSort("count", RankingSortDirection.DESC, RankingTieBreaker.ENTITY_ID_ASC),
                null,
                null,
                List.of(row),
                999
        );
        RankingResult result = new RankingResult(
                "fixture",
                "Fixture",
                null,
                999,
                1L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );

        assertEquals("1", numericValue.exactValue());
        assertEquals(1, section.rowCount());
        assertEquals(1, result.rowCount());
    }
}

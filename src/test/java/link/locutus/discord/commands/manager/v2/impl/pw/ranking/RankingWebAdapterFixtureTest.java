package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingWebAdapterFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void singleSectionFixtureMatchesExpectedContract() throws Exception {
        RankingMetricDescriptor metric = new RankingMetricDescriptor("score", "Score", RankingNumericType.INTEGER, RankingValueFormat.COUNT);
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(2, 10);
        values.put(1, 10);
        values.put(3, 8);

        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                RankingSortDirection.DESC,
                values,
                Set.of(3),
                id -> "Alliance " + id,
                RankingSupport.sectionMetadata(RankingEntityType.ALLIANCE.name(), RankingAggregationMode.IDENTITY),
                List.of("Current snapshot.")
        );
        RankingResult result = new RankingResult(
                "alliance_metric_fixture",
                "Top Score by alliance",
                List.of(new RankingQueryField("metric", "Metric", "score")),
                RankingBuilders.totalRowCount(List.of(section)),
                12345L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );

        assertFixture("single-section-ranking.json", WebRankingAdapter.toWeb(result));
    }

    @Test
    void multiSectionFixtureMatchesExpectedContract() throws Exception {
        RankingMetricDescriptor metric = new RankingMetricDescriptor("count", "Count", RankingNumericType.INTEGER, RankingValueFormat.COUNT);
        RankingSection victories = RankingBuilders.singleMetricSection(
                "victories",
                "Victories",
                RankingEntityType.NATION,
                metric,
                RankingSortDirection.DESC,
                Map.of(11, 3, 12, 1),
                Set.of(),
                id -> "Nation " + id,
                RankingSupport.sectionMetadata("WAR", RankingAggregationMode.COUNT),
                List.of()
        );
        RankingSection losses = RankingBuilders.singleMetricSection(
                "losses",
                "Losses",
                RankingEntityType.NATION,
                metric,
                RankingSortDirection.DESC,
                Map.of(),
                Set.of(),
                id -> "Nation " + id,
                RankingSupport.sectionMetadata("WAR", RankingAggregationMode.COUNT),
                List.of()
        );
        RankingSection peace = RankingBuilders.singleMetricSection(
                "peace",
                "Peace",
                RankingEntityType.NATION,
                metric,
                RankingSortDirection.DESC,
                Map.of(13, 2),
                Set.of(13),
                id -> "Nation " + id,
                RankingSupport.sectionMetadata("WAR", RankingAggregationMode.COUNT),
                List.of("Empty sections are retained.")
        );
        RankingResult result = new RankingResult(
                "war_status_fixture",
                "War Status by Nation",
                List.of(new RankingQueryField("start_ms", "Start", "1000")),
                RankingBuilders.totalRowCount(List.of(victories, losses, peace)),
                54321L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(victories, losses, peace)
        );

        assertFixture("multi-section-ranking.json", WebRankingAdapter.toWeb(result));
    }

    private void assertFixture(String fixtureName, WebRankingResult result) throws IOException {
        String expected = readFixture(fixtureName).trim();
        String actual = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result).replace("\r\n", "\n").trim();
        assertEquals(expected, actual);
    }

    private String readFixture(String fixtureName) throws IOException {
        try (var stream = getClass().getResourceAsStream("/link/locutus/discord/commands/manager/v2/impl/pw/ranking/" + fixtureName)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + fixtureName);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}

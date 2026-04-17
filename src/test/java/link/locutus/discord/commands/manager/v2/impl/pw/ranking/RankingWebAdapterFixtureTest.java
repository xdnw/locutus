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
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(2, 10);
        values.put(1, 10);
        values.put(3, 8);

        RankingResult result = RankingBuilders.singleMetricRanking(
                "alliance_metric_fixture",
                RankingEntityType.ALLIANCE,
                "score",
                RankingValueFormat.COUNT,
                RankingNumericType.INTEGER,
                List.of(RankingBuilders.singleMetricSection(
                        "alliances",
                        RankingEntityType.ALLIANCE.name(),
                        RankingAggregationMode.IDENTITY,
                        RankingSortDirection.DESC,
                        values,
                        Map.of()
                )),
                Map.of("metric", "score"),
                Set.of(3),
                12345L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );

        assertFixture("single-section-ranking.json", WebRankingAdapter.toWeb(result));
    }

    @Test
    void multiSectionFixtureMatchesExpectedContract() throws Exception {
        RankingResult result = RankingBuilders.singleMetricRanking(
                "war_status_fixture",
                RankingEntityType.NATION,
                "count",
                RankingValueFormat.COUNT,
                RankingNumericType.INTEGER,
                List.of(
                        RankingBuilders.singleMetricSection("victories", "WAR", RankingAggregationMode.COUNT, RankingSortDirection.DESC, Map.of(11, 3, 12, 1), Map.of()),
                        RankingBuilders.singleMetricSection("losses", "WAR", RankingAggregationMode.COUNT, RankingSortDirection.DESC, Map.of(), Map.of()),
                        RankingBuilders.singleMetricSection("peace", "WAR", RankingAggregationMode.COUNT, RankingSortDirection.DESC, Map.of(13, 2), Map.of())
                ),
                Map.of("start_ms", 1000),
                Set.of(13),
                54321L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
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

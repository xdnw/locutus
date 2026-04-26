package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                RankingKind.ALLIANCE_METRIC,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.COUNT,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        values
                )),
                Set.of(3),
                12345L
        );

        assertFixture("single-section-ranking.json", result);
    }

    @Test
    void multiSectionFixtureMatchesExpectedContract() throws Exception {
        RankingResult result = RankingBuilders.singleMetricRanking(
                RankingKind.WAR_STATUS,
                RankingEntityType.NATION,
                RankingValueFormat.COUNT,
                List.of(
                        RankingBuilders.singleMetricSection(RankingSectionKind.VICTORIES, RankingSortDirection.DESC, Map.of(11, 3, 12, 1)),
                        RankingBuilders.singleMetricSection(RankingSectionKind.LOSSES, RankingSortDirection.DESC, Map.of()),
                        RankingBuilders.singleMetricSection(RankingSectionKind.PEACE, RankingSortDirection.DESC, Map.of(13, 2))
                ),
                Set.of(13),
                54321L
        );

        assertFixture("multi-section-ranking.json", result);
    }

    private void assertFixture(String fixtureName, RankingResult result) throws IOException {
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

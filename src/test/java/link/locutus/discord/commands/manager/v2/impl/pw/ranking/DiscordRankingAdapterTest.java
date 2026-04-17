package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.commands.manager.v2.command.StringMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordRankingAdapterTest {
    @Test
    void adapterOwnsNumberingEllipsisAndFileUpload() {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(1, 3);
        values.put(2, 2);
        values.put(3, 1);

        RankingResult result = RankingBuilders.singleMetricRanking(
                "ranking_fixture",
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
                Map.of(),
                Set.of(3),
                12345L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );

        StringMessageIO io = new StringMessageIO(null, null);
        DiscordRankingAdapter.send(io, new JSONObject().put("metric", "count"), result, new DiscordRankingAdapter.RenderOptions(2, true, null));

        assertEquals(1, io.getMessages().size());
        StringMessageBuilder message = io.getMessages().get(0);
        String rendered = message.toString();

        assertTrue(rendered.contains("1. alliance:1: 3"));
        assertTrue(rendered.contains("2. alliance:2: 2"));
        assertTrue(rendered.contains("..."));
        assertTrue(rendered.contains("**3. alliance:3: 1**"));
        assertTrue(message.getFiles().containsKey("ranking_fixture.txt"));
        assertTrue(message.getButtons().containsValue("Refresh"));
    }
}

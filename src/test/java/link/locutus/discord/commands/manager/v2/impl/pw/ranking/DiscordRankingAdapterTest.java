package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.commands.manager.v2.command.StringMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordRankingAdapterTest {
    @Test
    void adapterOwnsNotesNumberingEllipsisAndFileUpload() {
        RankingMetricDescriptor metric = new RankingMetricDescriptor("count", "Count", RankingNumericType.INTEGER, RankingValueFormat.COUNT);
        Map<Integer, Integer> values = new LinkedHashMap<>();
        values.put(1, 3);
        values.put(2, 2);
        values.put(3, 1);
        RankingSection section = RankingBuilders.singleMetricSection(
                "entities",
                "Entities",
                RankingEntityType.ALLIANCE,
                metric,
                RankingSortDirection.DESC,
                values,
                Set.of(3),
                id -> switch (id) {
                    case 1 -> "One";
                    case 2 -> "Two";
                    default -> "Three";
                },
                List.of(),
                List.of("Values are deltas between the requested start and end turns.")
        );
        RankingResult result = new RankingResult(
                "ranking_fixture",
                "Alliance Ranking",
                List.of(),
                section.rowCount(),
                12345L,
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );

        StringMessageIO io = new StringMessageIO(null, null);
        DiscordRankingAdapter.send(io, new JSONObject().put("metric", "count"), result, new DiscordRankingAdapter.RenderOptions(2, true, null));

        assertEquals(1, io.getMessages().size());
        StringMessageBuilder message = io.getMessages().get(0);
        String rendered = message.toString();

        assertTrue(rendered.contains("Values are deltas between the requested start and end turns."));
        assertTrue(rendered.contains("1. One: 3"));
        assertTrue(rendered.contains("2. Two: 2"));
        assertTrue(rendered.contains("..."));
        assertTrue(rendered.contains("**3. Three: 1**"));
        assertTrue(message.getFiles().containsKey("ranking_fixture.txt"));
        assertTrue(message.getButtons().containsValue("Refresh"));
    }
}

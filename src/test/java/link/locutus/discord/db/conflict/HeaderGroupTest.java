package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.web.jooby.JteUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the {@link HeaderGroup#getBytesZip} payload shape.
 *
 * The frontend (lc_stats_svelte types.Conflict, types.GraphCoalitionData)
 * expects a single {@code coalitions} entry whose elements are the deep-merged
 * union of {@link CoalitionSide#toMetaMap} and {@link WarStatistics#toDataMap}
 * (page payload) or {@link WarStatistics#toGraphMap} (graph payload). Because
 * two header groups (PAGE_META + PAGE_STATS, GRAPH_META + GRAPH_DATA) both emit
 * a {@code coalitions} key, a flat msgpack map would let the second occurrence
 * overwrite the first and strip {@code alliance_ids}/{@code alliance_names}
 * from the frontend's view. {@link JteUtil#merge} is the owner of the
 * "combine groups" semantic.
 */
class HeaderGroupTest {

    @Test
    void jteMergeDeepMergesCollidingCoalitionsLists() throws Exception {
        Map<String, Object> pageMeta = new LinkedHashMap<>();
        pageMeta.put("name", "Test Conflict");
        pageMeta.put("coalitions", List.of(
                mutableMap(
                        "name", "Alpha",
                        "alliance_ids", List.of(1, 2),
                        "alliance_names", List.of("AAA", "BBB")
                ),
                mutableMap(
                        "name", "Bravo",
                        "alliance_ids", List.of(3),
                        "alliance_names", List.of("CCC")
                )));

        Map<String, Object> pageStats = new LinkedHashMap<>();
        pageStats.put("damage_header", List.of("loss_value", "wars"));
        pageStats.put("coalitions", List.of(
                mutableMap(
                        "counts", List.of(List.of(1, 2), List.of(3, 4)),
                        "damage", List.of(List.of(10.0, 20.0), List.of(30.0, 40.0))
                ),
                mutableMap(
                        "counts", List.of(List.of(5), List.of(6)),
                        "damage", List.of(List.of(50.0), List.of(60.0))
                )));

        Map<String, Object> combined = new LinkedHashMap<>();
        JteUtil.merge(combined, pageMeta);
        JteUtil.merge(combined, pageStats);
        combined.put("update_ms", 12345L);

        ObjectMapper mapper = JteUtil.getSerializer();
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = mapper.readValue(
                mapper.writeValueAsBytes(combined), Map.class);

        assertEquals("Test Conflict", roundTripped.get("name"));
        assertEquals(List.of("loss_value", "wars"), roundTripped.get("damage_header"));
        assertEquals(12345, ((Number) roundTripped.get("update_ms")).longValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coalitions =
                (List<Map<String, Object>>) roundTripped.get("coalitions");
        assertEquals(2, coalitions.size());

        Map<String, Object> alpha = coalitions.get(0);
        assertEquals("Alpha", alpha.get("name"));
        assertEquals(List.of(1, 2), alpha.get("alliance_ids"));
        assertEquals(List.of("AAA", "BBB"), alpha.get("alliance_names"));
        assertTrue(alpha.containsKey("counts"));
        assertTrue(alpha.containsKey("damage"));

        Map<String, Object> bravo = coalitions.get(1);
        assertEquals("Bravo", bravo.get("name"));
        assertEquals(List.of(3), bravo.get("alliance_ids"));
        assertEquals(List.of("CCC"), bravo.get("alliance_names"));
        assertTrue(bravo.containsKey("counts"));
        assertTrue(bravo.containsKey("damage"));
    }

    private static Map<String, Object> mutableMap(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}

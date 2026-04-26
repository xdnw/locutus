package link.locutus.discord.db.conflict;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConflictUtilTest {
    @Test
    void toGraphMapPartStopsAllianceTimelineAtEndOffset() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 1, 5L);
        put(data, 12L, 7, 101, (byte) 2, 9L);
        put(data, 13L, 7, 101, (byte) 1, 11L);

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                13L,
                List.of(101, 202),
                List.of((byte) 1, (byte) 2),
                List.of(1, -1)
        );

        List<List<Long>> alliance101Timeline = result.get(0).get(0);
        assertEquals(1, alliance101Timeline.size());
        assertEquals(Arrays.asList(5L, null), alliance101Timeline.get(0));

        List<List<Long>> alliance202Timeline = result.get(0).get(1);
        assertEquals(0, alliance202Timeline.size());
    }

    @Test
    void toGraphMapPartOmitsTrailingEmptyFramesWithoutEndOffsets() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 1, 5L);
        put(data, 12L, 7, 101, (byte) 2, 9L);

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                13L,
                List.of(101),
                List.of((byte) 1, (byte) 2),
                null
        );

        assertEquals(3, result.get(0).get(0).size());
        assertEquals(Arrays.asList(5L, null), result.get(0).get(0).get(0));
        assertEquals(List.of(), result.get(0).get(0).get(1));
        assertEquals(Arrays.asList(null, 9L), result.get(0).get(0).get(2));
    }

    @Test
    void toGraphMapPartPreservesLeadingEmptyFrames() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 12L, 7, 101, (byte) 2, 9L);

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                13L,
                List.of(101),
                List.of((byte) 1, (byte) 2),
                null
        );

        assertEquals(3, result.get(0).get(0).size());
        assertEquals(List.of(), result.get(0).get(0).get(0));
        assertEquals(List.of(), result.get(0).get(0).get(1));
        assertEquals(Arrays.asList(null, 9L), result.get(0).get(0).get(2));
    }

    @Test
    void toGraphMapPartStartsAllianceTimelineAtStartOffset() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 1, 5L);
        put(data, 12L, 7, 202, (byte) 2, 9L);

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                13L,
                List.of(101, 202),
                List.of((byte) 1, (byte) 2),
                List.of(0, 2),
                null
        );

        assertEquals(1, result.get(0).get(0).size());
        assertEquals(Arrays.asList(5L, null), result.get(0).get(0).get(0));

        assertEquals(1, result.get(0).get(1).size());
        assertEquals(Arrays.asList(null, 9L), result.get(0).get(1).get(0));
    }

    private static void put(
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data,
            long turn,
            int metric,
            int allianceId,
            byte city,
            long value
    ) {
        data.computeIfAbsent(turn, k -> new HashMap<>())
                .computeIfAbsent(metric, k -> new HashMap<>())
                .computeIfAbsent(allianceId, k -> new HashMap<>())
                .put(city, value);
    }
}

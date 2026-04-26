package link.locutus.discord.db.conflict;

import link.locutus.discord.util.IOUtil;
import link.locutus.discord.web.jooby.JteUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(Arrays.asList(0L, 0L, 5L), alliance101Timeline.get(0));

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

        assertEquals(2, result.get(0).get(0).size());
        assertEquals(Arrays.asList(0L, 0L, 5L), result.get(0).get(0).get(0));
        assertEquals(Arrays.asList(2L, 1L, 9L), result.get(0).get(0).get(1));
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

        assertEquals(1, result.get(0).get(0).size());
        assertEquals(Arrays.asList(2L, 1L, 9L), result.get(0).get(0).get(0));
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
        assertEquals(Arrays.asList(0L, 0L, 5L), result.get(0).get(0).get(0));

        assertEquals(1, result.get(0).get(1).size());
        assertEquals(Arrays.asList(2L, 1L, 9L), result.get(0).get(1).get(0));
    }

    @Test
    void toGraphMapPartUsesMaskedFramesWhenTheyAreMoreCompact() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 1, 5L);
        put(data, 10L, 7, 101, (byte) 3, 7L);
        put(data, 10L, 7, 101, (byte) 4, 9L);

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                10L,
                List.of(101),
                List.of((byte) 1, (byte) 2, (byte) 3, (byte) 4),
                null
        );

        assertEquals(Arrays.asList(0L, -1L, 13L, 5L, 7L, 9L), result.get(0).get(0).get(0));
    }

    @Test
    void toGraphMapPartKeepsIndexedFramesWhenMaskWordsWouldEncodeLarger() {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 28, 5L);
        put(data, 10L, 7, 101, (byte) 29, 7L);
        put(data, 10L, 7, 101, (byte) 30, 9L);

        List<Byte> cities = new java.util.ArrayList<>(30);
        for (byte city = 1; city <= 30; city++) {
            cities.add(city);
        }

        List<List<List<List<Long>>>> result = ConflictUtil.toGraphMapPart(
                List.of(7),
                data,
                10L,
                10L,
                List.of(101),
                cities,
                null
        );

        assertEquals(Arrays.asList(0L, 27L, 5L, 28L, 7L, 29L, 9L), result.get(0).get(0).get(0));
    }

    @Test
    void sparseGraphPayloadIsNoLargerThanLegacyDensePayloadForSparseTimelines() throws Exception {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data = new HashMap<>();
        put(data, 10L, 7, 101, (byte) 1, 5L);
        put(data, 13L, 7, 101, (byte) 3, 11L);
        put(data, 15L, 7, 202, (byte) 2, 9L);

        List<Integer> keys = List.of(7);
        List<Integer> allianceIds = List.of(101, 202);
        List<Byte> cities = List.of((byte) 1, (byte) 2, (byte) 3);

        List<List<List<List<Long>>>> sparse = ConflictUtil.toGraphMapPart(
                keys,
                data,
                10L,
                16L,
                allianceIds,
                cities,
                null
        );
        List<List<List<List<Long>>>> dense = toLegacyDenseGraphMapPart(
                keys,
                data,
                10L,
                16L,
                allianceIds,
                cities,
                null,
                null
        );

        byte[] sparseBytes = IOUtil.writeMsgpackBytes(JteUtil.getSerializer(), Map.of("data", sparse));
        byte[] denseBytes = IOUtil.writeMsgpackBytes(JteUtil.getSerializer(), Map.of("data", dense));

        assertTrue(sparseBytes.length < denseBytes.length,
                "Expected sparse payload to be smaller than dense payload, sparse="
                        + sparseBytes.length + " dense=" + denseBytes.length);
    }

    private static List<List<List<List<Long>>>> toLegacyDenseGraphMapPart(
            List<Integer> keys,
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data,
            long start,
            long end,
            List<Integer> aaIds,
            List<Byte> cities,
            List<Integer> startOffsets,
            List<Integer> endOffsets
    ) {
        List<List<List<List<Long>>>> turnMetricCitiesTables = new java.util.ArrayList<>();
        for (int metricOrdinal : keys) {
            List<List<List<Long>>> metricCitiesTableByAA = new java.util.ArrayList<>();
            for (int aaIndex = 0; aaIndex < aaIds.size(); aaIndex++) {
                int aaId = aaIds.get(aaIndex);
                long timelineStart = resolveTimelineStart(start, end, startOffsets, aaIndex);
                long timelineEnd = resolveTimelineEnd(start, end, endOffsets, aaIndex);
                List<List<Long>> metricCitiesTable = new java.util.ArrayList<>();
                for (long turnOrDay = timelineStart; turnOrDay <= timelineEnd; turnOrDay++) {
                    Map<Integer, Map<Integer, Map<Byte, Long>>> dataAtTime = data.get(turnOrDay);
                    if (dataAtTime == null) {
                        metricCitiesTable.add(List.of());
                        continue;
                    }
                    Map<Integer, Map<Byte, Long>> metricDataByAA = dataAtTime.get(metricOrdinal);
                    if (metricDataByAA == null) {
                        metricCitiesTable.add(List.of());
                        continue;
                    }
                    Map<Byte, Long> metricData = metricDataByAA.get(aaId);
                    if (metricData == null) {
                        metricCitiesTable.add(List.of());
                        continue;
                    }

                    List<Long> values = new java.util.ArrayList<>(cities.size());
                    for (byte city : cities) {
                        Long value = metricData.get(city);
                        values.add(value == null && !metricData.containsKey(city) ? null : value);
                    }
                    metricCitiesTable.add(values);
                }
                trimTrailingEmptyFrames(metricCitiesTable);
                metricCitiesTableByAA.add(metricCitiesTable);
            }
            turnMetricCitiesTables.add(metricCitiesTableByAA);
        }
        return turnMetricCitiesTables;
    }

    private static long resolveTimelineStart(long start, long end, List<Integer> startOffsets, int aaIndex) {
        if (startOffsets == null || aaIndex >= startOffsets.size()) {
            return start;
        }

        Integer startOffset = startOffsets.get(aaIndex);
        if (startOffset == null) {
            return start;
        }

        return Math.min(end + 1, start + Math.max(startOffset, 0));
    }

    private static long resolveTimelineEnd(long start, long end, List<Integer> endOffsets, int aaIndex) {
        if (endOffsets == null || aaIndex >= endOffsets.size()) {
            return end;
        }

        Integer endOffset = endOffsets.get(aaIndex);
        if (endOffset == null) {
            return end;
        }

        return Math.min(end, start + endOffset);
    }

    private static void trimTrailingEmptyFrames(List<List<Long>> timeline) {
        int lastIndex = timeline.size() - 1;
        while (lastIndex >= 0 && timeline.get(lastIndex).isEmpty()) {
            timeline.remove(lastIndex);
            lastIndex--;
        }
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

package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConflictUtil {
    private static final int PATCH_MASK_BITS = 30;
    private static final Pattern TEMP_CONFLICT_KEY = Pattern
            .compile("^conflicts/n/([0-9]+)/([0-9a-fA-F-]{36})\\.gzip$");
    private static final Pattern TEMP_CONFLICT_PATH = Pattern
            .compile("^n/([0-9]+)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    public static VirtualConflictId parseVirtualConflictWebId(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Virtual conflict path cannot be null or empty");
        }

        Matcher matcher = TEMP_CONFLICT_PATH.matcher(path.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid virtual conflict path `" + path + "`. Expected format: n/{nationId}/{uuid}");
        }

        int nationId;
        try {
            nationId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid nation id in virtual conflict path `" + path + "`", e);
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID in virtual conflict path `" + path + "`", e);
        }

        return new VirtualConflictId(nationId, uuid);
    }

    public static VirtualConflictId parseVirtualConflictObjectKey(String objectKey) {
        Matcher matcher = TEMP_CONFLICT_KEY.matcher(objectKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a virtual conflict object key: `" + objectKey + "`");
        }

        return parseVirtualConflictWebId("n/" + matcher.group(1) + "/" + matcher.group(2));
    }

    public record VirtualConflictId(int nationId, UUID uuid) {
        public String toWebId() {
            return "n/" + nationId + "/" + uuid;
        }

        @Override
        public String toString() {
            return toWebId();
        }

        public String toObjectKey() {
            return "conflicts/" + toWebId() + ".gzip";
        }
    }

    public static void trimTimeData(Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData) {
        if (turnData.size() < 2) {
            return;
        }

        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> trimmed = new Long2ObjectArrayMap<>(turnData.size());
        Int2ObjectMap<Int2ObjectMap<Byte2LongOpenHashMap>> previous = new Int2ObjectOpenHashMap<>();

        LongArrayList turns = new LongArrayList(turnData.keySet());
        turns.sort(null);

        for (long currentTurn : turns) {
            Map<Integer, Map<Integer, Map<Byte, Long>>> currentData = turnData.get(currentTurn);
            if (currentData == null || currentData.isEmpty()) {
                continue;
            }

            Map<Integer, Map<Integer, Map<Byte, Long>>> trimmedTurn = null;

            for (Map.Entry<Integer, Map<Integer, Map<Byte, Long>>> metricEntry : currentData.entrySet()) {
                int metricId = metricEntry.getKey();
                Map<Integer, Map<Byte, Long>> currentByMetric = metricEntry.getValue();

                Int2ObjectMap<Byte2LongOpenHashMap> previousByMetric = previous.get(metricId);
                if (previousByMetric == null) {
                    previousByMetric = new Int2ObjectOpenHashMap<>(currentByMetric.size());
                    previous.put(metricId, previousByMetric);

                    for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : currentByMetric.entrySet()) {
                        Byte2LongOpenHashMap primitiveMap = ensurePrimitiveCityMap(allianceEntry);
                        previousByMetric.put(
                                allianceEntry.getKey(),
                                new Byte2LongOpenHashMap(primitiveMap));
                    }

                    if (trimmedTurn == null) {
                        trimmedTurn = trimmed.computeIfAbsent(
                                currentTurn, k -> new Int2ObjectOpenHashMap<>());
                    }
                    trimmedTurn.put(metricId, currentByMetric);
                    continue;
                }

                Map<Integer, Map<Byte, Long>> trimmedMetric = null;

                for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : currentByMetric.entrySet()) {
                    int allianceId = allianceEntry.getKey();
                    Byte2LongOpenHashMap currentByAlliance = ensurePrimitiveCityMap(allianceEntry);

                    Byte2LongOpenHashMap previousByAlliance = previousByMetric.get(allianceId);
                    if (previousByAlliance == null) {
                        previousByAlliance = new Byte2LongOpenHashMap(currentByAlliance);
                        previousByMetric.put(allianceId, previousByAlliance);

                        if (trimmedTurn == null) {
                            trimmedTurn = trimmed.computeIfAbsent(
                                    currentTurn, k -> new Int2ObjectOpenHashMap<>());
                        }
                        if (trimmedMetric == null) {
                            trimmedMetric = trimmedTurn.computeIfAbsent(
                                    metricId, k -> new Int2ObjectOpenHashMap<>());
                        }
                        trimmedMetric.put(allianceId, currentByAlliance);
                        continue;
                    }

                    Byte2LongArrayMap delta = null;

                    ObjectIterator<Byte2LongMap.Entry> cityIterator = currentByAlliance.byte2LongEntrySet()
                            .fastIterator();
                    while (cityIterator.hasNext()) {
                        Byte2LongMap.Entry cityEntry = cityIterator.next();
                        byte cityId = cityEntry.getByteKey();
                        long currentValue = cityEntry.getLongValue();
                        boolean hadValue = previousByAlliance.containsKey(cityId);
                        long previousValue = hadValue ? previousByAlliance.get(cityId) : 0L;

                        if (!hadValue || currentValue != previousValue) {
                            previousByAlliance.put(cityId, currentValue);
                            if (delta == null) {
                                delta = new Byte2LongArrayMap();
                            }
                            delta.put(cityId, currentValue);
                        }
                    }

                    ObjectIterator<Byte2LongMap.Entry> previousIterator = previousByAlliance.byte2LongEntrySet()
                            .fastIterator();
                    while (previousIterator.hasNext()) {
                        Byte2LongMap.Entry prevEntry = previousIterator.next();
                        byte cityId = prevEntry.getByteKey();
                        if (!currentByAlliance.containsKey(cityId)
                                && prevEntry.getLongValue() != 0L) {
                            prevEntry.setValue(0L);
                            if (delta == null) {
                                delta = new Byte2LongArrayMap();
                            }
                            delta.put(cityId, 0L);
                        }
                    }

                    if (delta != null) {
                        if (trimmedTurn == null) {
                            trimmedTurn = trimmed.computeIfAbsent(
                                    currentTurn, k -> new Int2ObjectOpenHashMap<>());
                        }
                        if (trimmedMetric == null) {
                            trimmedMetric = trimmedTurn.computeIfAbsent(
                                    metricId, k -> new Int2ObjectOpenHashMap<>());
                        }
                        trimmedMetric.put(allianceId, delta);
                    }
                }

                ObjectIterator<Int2ObjectMap.Entry<Byte2LongOpenHashMap>> previousAllianceIterator = previousByMetric
                        .int2ObjectEntrySet().iterator();
                while (previousAllianceIterator.hasNext()) {
                    Int2ObjectMap.Entry<Byte2LongOpenHashMap> previousAllianceEntry = previousAllianceIterator.next();
                    int allianceId = previousAllianceEntry.getIntKey();

                    if (currentByMetric.containsKey(allianceId)) {
                        continue;
                    }

                    Byte2LongOpenHashMap previousByAlliance = previousAllianceEntry.getValue();
                    Byte2LongArrayMap delta = null;

                    ObjectIterator<Byte2LongMap.Entry> cityIterator = previousByAlliance.byte2LongEntrySet()
                            .fastIterator();
                    while (cityIterator.hasNext()) {
                        Byte2LongMap.Entry cityEntry = cityIterator.next();
                        if (cityEntry.getLongValue() != 0L) {
                            cityEntry.setValue(0L);
                            if (delta == null) {
                                delta = new Byte2LongArrayMap();
                            }
                            delta.put(cityEntry.getByteKey(), 0L);
                        }
                    }

                    if (delta != null) {
                        if (trimmedTurn == null) {
                            trimmedTurn = trimmed.computeIfAbsent(
                                    currentTurn, k -> new Int2ObjectOpenHashMap<>());
                        }
                        trimmedTurn.computeIfAbsent(
                                metricId, k -> new Int2ObjectOpenHashMap<>())
                                .put(allianceId, delta);
                    }
                }
            }
        }

        turnData.clear();
        turnData.putAll(trimmed);
    }

    private static Byte2LongOpenHashMap ensurePrimitiveCityMap(
            Map.Entry<Integer, Map<Byte, Long>> allianceEntry) {
        Map<Byte, Long> allianceData = allianceEntry.getValue();
        if (allianceData instanceof Byte2LongOpenHashMap) {
            return (Byte2LongOpenHashMap) allianceData;
        }

        Byte2LongOpenHashMap converted = new Byte2LongOpenHashMap(allianceData.size());
        for (Map.Entry<Byte, Long> cityEntry : allianceData.entrySet()) {
            Byte cityId = cityEntry.getKey();
            Long value = cityEntry.getValue();
            if (cityId != null && value != null) {
                converted.put(cityId, value);
            }
        }
        allianceEntry.setValue(converted);
        return converted;
    }

    public static long[] computeRange(Map<Long, ?> data) {
        if (data.isEmpty()) {
            return new long[] { 0L, 0L };
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : data.keySet()) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        return new long[] { min, max };
    }

    public static List<List<List<List<Long>>>> toGraphMapPart(List<Integer> keys,
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data,
            long start, long end,
            List<Integer> aaIds,
            List<Byte> cities,
            List<Integer> endOffsets) {
        return toGraphMapPart(keys, data, start, end, aaIds, cities, null, endOffsets);
    }

    public static List<List<List<List<Long>>>> toGraphMapPart(List<Integer> keys,
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data,
            long start, long end,
            List<Integer> aaIds,
            List<Byte> cities,
            List<Integer> startOffsets,
            List<Integer> endOffsets) {
        LongArrayList timeValues = new LongArrayList(data.keySet());
        timeValues.sort(null);

        Byte2IntOpenHashMap cityIndexById = new Byte2IntOpenHashMap(cities.size());
        cityIndexById.defaultReturnValue(-1);
        for (int cityIndex = 0; cityIndex < cities.size(); cityIndex++) {
            Byte cityId = cities.get(cityIndex);
            if (cityId != null) {
                cityIndexById.put(cityId.byteValue(), cityIndex);
            }
        }

        Int2IntOpenHashMap metricIndexByOrdinal = new Int2IntOpenHashMap(keys.size());
        metricIndexByOrdinal.defaultReturnValue(-1);
        for (int metricIndex = 0; metricIndex < keys.size(); metricIndex++) {
            Integer metricOrdinal = keys.get(metricIndex);
            if (metricOrdinal != null) {
                metricIndexByOrdinal.put(metricOrdinal.intValue(), metricIndex);
            }
        }

        Int2IntOpenHashMap allianceIndexById = new Int2IntOpenHashMap(aaIds.size());
        allianceIndexById.defaultReturnValue(-1);
        long[] timelineStarts = new long[aaIds.size()];
        long[] timelineEnds = new long[aaIds.size()];
        for (int aaIndex = 0; aaIndex < aaIds.size(); aaIndex++) {
            Integer aaId = aaIds.get(aaIndex);
            if (aaId != null) {
                allianceIndexById.put(aaId.intValue(), aaIndex);
            }
            timelineStarts[aaIndex] = getTimelineStart(start, end, startOffsets, aaIndex);
            timelineEnds[aaIndex] = getTimelineEnd(start, end, endOffsets, aaIndex);
        }

        List<List<List<List<Long>>>> turnMetricCitiesTables = initializeSparseGraphTables(keys.size(), aaIds.size());

        for (long turnOrDay : timeValues) {
            if (turnOrDay < start) {
                continue;
            }
            if (turnOrDay > end) {
                break;
            }

            Map<Integer, Map<Integer, Map<Byte, Long>>> dataAtTime = data.get(turnOrDay);
            if (dataAtTime == null || dataAtTime.isEmpty()) {
                continue;
            }

            for (Map.Entry<Integer, Map<Integer, Map<Byte, Long>>> metricEntry : dataAtTime.entrySet()) {
                int metricIndex = metricIndexByOrdinal.get(metricEntry.getKey());
                if (metricIndex < 0) {
                    continue;
                }

                List<List<List<Long>>> metricCitiesTableByAA = turnMetricCitiesTables.get(metricIndex);
                Map<Integer, Map<Byte, Long>> metricDataByAA = metricEntry.getValue();
                if (metricDataByAA == null || metricDataByAA.isEmpty()) {
                    continue;
                }

                for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : metricDataByAA.entrySet()) {
                    int aaIndex = allianceIndexById.get(allianceEntry.getKey());
                    if (aaIndex < 0) {
                        continue;
                    }
                    if (turnOrDay < timelineStarts[aaIndex] || turnOrDay > timelineEnds[aaIndex]) {
                        continue;
                    }

                    Map<Byte, Long> metricData = allianceEntry.getValue();
                    if (metricData == null || metricData.isEmpty()) {
                        continue;
                    }

                    List<Long> encodedFrame = encodeSparsePatchFrame(
                            turnOrDay - start,
                            metricData,
                            cityIndexById);
                    if (encodedFrame.isEmpty()) {
                        continue;
                    }

                    List<List<Long>> timeline = metricCitiesTableByAA.get(aaIndex);
                    if (timeline == null) {
                        timeline = new ObjectArrayList<>();
                        metricCitiesTableByAA.set(aaIndex, timeline);
                    }
                    timeline.add(encodedFrame);
                }
            }
        }

        finalizeSparseGraphTables(turnMetricCitiesTables);
        return turnMetricCitiesTables;
    }

    private static long getTimelineStart(long start, long end, List<Integer> startOffsets, int aaIndex) {
        if (startOffsets == null || aaIndex >= startOffsets.size()) {
            return start;
        }

        Integer startOffset = startOffsets.get(aaIndex);
        if (startOffset == null) {
            return start;
        }

        return Math.min(end + 1, start + Math.max(startOffset, 0));
    }

    private static long getTimelineEnd(long start, long end, List<Integer> endOffsets, int aaIndex) {
        if (endOffsets == null || aaIndex >= endOffsets.size()) {
            return end;
        }

        Integer endOffset = endOffsets.get(aaIndex);
        if (endOffset == null) {
            return end;
        }

        return Math.min(end, start + endOffset);
    }

    private static List<List<List<List<Long>>>> initializeSparseGraphTables(int metricCount, int aaCount) {
        List<List<List<List<Long>>>> turnMetricCitiesTables = new ObjectArrayList<>(metricCount);
        for (int metricIndex = 0; metricIndex < metricCount; metricIndex++) {
            List<List<List<Long>>> metricCitiesTableByAA = new ObjectArrayList<>(aaCount);
            for (int aaIndex = 0; aaIndex < aaCount; aaIndex++) {
                metricCitiesTableByAA.add(null);
            }
            turnMetricCitiesTables.add(metricCitiesTableByAA);
        }
        return turnMetricCitiesTables;
    }

    private static void finalizeSparseGraphTables(List<List<List<List<Long>>>> turnMetricCitiesTables) {
        for (List<List<List<Long>>> metricCitiesTableByAA : turnMetricCitiesTables) {
            for (int aaIndex = 0; aaIndex < metricCitiesTableByAA.size(); aaIndex++) {
                if (metricCitiesTableByAA.get(aaIndex) == null) {
                    metricCitiesTableByAA.set(aaIndex, Collections.emptyList());
                }
            }
        }
    }

    private static List<Long> encodeSparsePatchFrame(
            long timeOffset,
            Map<Byte, Long> metricData,
            Byte2IntOpenHashMap cityIndexById
    ) {
        int estimatedChanges = metricData.size();
        if (estimatedChanges <= 0) {
            return Collections.emptyList();
        }

        int[] cityIndexes = new int[estimatedChanges];
        long[] cityValues = new long[estimatedChanges];
        int changeCount = collectChangedCities(metricData, cityIndexById, cityIndexes, cityValues);
        if (changeCount == 0) {
            return Collections.emptyList();
        }

        sortChangedCities(cityIndexes, cityValues, changeCount);

        int highestCityIndex = cityIndexes[changeCount - 1];
        int maskWordCount = highestCityIndex / PATCH_MASK_BITS + 1;
        long[] maskWords = buildMaskWords(cityIndexes, changeCount, maskWordCount);
        int indexedEncodedSize = estimateIndexedPatchFrameSize(timeOffset, cityIndexes, cityValues, changeCount);
        int maskedEncodedSize = estimateMaskedPatchFrameSize(timeOffset, maskWords, cityValues, changeCount);

        if (indexedEncodedSize <= maskedEncodedSize) {
            return encodeIndexedPatchFrame(timeOffset, cityIndexes, cityValues, changeCount);
        }

        return encodeMaskedPatchFrame(timeOffset, maskWords, cityValues, changeCount);
    }

    private static long[] buildMaskWords(int[] cityIndexes, int changeCount, int maskWordCount) {
        long[] maskWords = new long[maskWordCount];
        for (int i = 0; i < changeCount; i++) {
            int cityIndex = cityIndexes[i];
            int wordIndex = cityIndex / PATCH_MASK_BITS;
            int bitIndex = cityIndex % PATCH_MASK_BITS;
            maskWords[wordIndex] |= 1L << bitIndex;
        }
        return maskWords;
    }

    private static int collectChangedCities(
            Map<Byte, Long> metricData,
            Byte2IntOpenHashMap cityIndexById,
            int[] cityIndexes,
            long[] cityValues
    ) {
        int changeCount = 0;

        if (metricData instanceof Byte2LongMap byteMetricData) {
            ObjectIterator<Byte2LongMap.Entry> iterator = byteMetricData.byte2LongEntrySet().iterator();
            while (iterator.hasNext()) {
                Byte2LongMap.Entry cityEntry = iterator.next();
                int cityIndex = cityIndexById.get(cityEntry.getByteKey());
                if (cityIndex < 0) {
                    continue;
                }

                cityIndexes[changeCount] = cityIndex;
                cityValues[changeCount] = cityEntry.getLongValue();
                changeCount++;
            }
        } else {
            for (Map.Entry<Byte, Long> cityEntry : metricData.entrySet()) {
                Byte cityId = cityEntry.getKey();
                Long value = cityEntry.getValue();
                if (cityId == null || value == null) {
                    continue;
                }

                int cityIndex = cityIndexById.get(cityId.byteValue());
                if (cityIndex < 0) {
                    continue;
                }

                cityIndexes[changeCount] = cityIndex;
                cityValues[changeCount] = value.longValue();
                changeCount++;
            }
        }

        return changeCount;
    }

    private static void sortChangedCities(int[] cityIndexes, long[] cityValues, int changeCount) {
        for (int i = 1; i < changeCount; i++) {
            int cityIndex = cityIndexes[i];
            long cityValue = cityValues[i];
            int insertIndex = i - 1;
            while (insertIndex >= 0 && cityIndexes[insertIndex] > cityIndex) {
                cityIndexes[insertIndex + 1] = cityIndexes[insertIndex];
                cityValues[insertIndex + 1] = cityValues[insertIndex];
                insertIndex--;
            }
            cityIndexes[insertIndex + 1] = cityIndex;
            cityValues[insertIndex + 1] = cityValue;
        }
    }

    private static List<Long> encodeIndexedPatchFrame(
            long timeOffset,
            int[] cityIndexes,
            long[] cityValues,
            int changeCount
    ) {
        LongArrayList encoded = new LongArrayList(1 + changeCount * 2);
        encoded.add(timeOffset);
        for (int i = 0; i < changeCount; i++) {
            encoded.add(cityIndexes[i]);
            encoded.add(cityValues[i]);
        }

        return encoded;
    }

    private static int estimateIndexedPatchFrameSize(
            long timeOffset,
            int[] cityIndexes,
            long[] cityValues,
            int changeCount
    ) {
        int size = estimateMsgpackArrayHeaderSize(1 + changeCount * 2);
        size += estimateMsgpackIntegerSize(timeOffset);
        for (int i = 0; i < changeCount; i++) {
            size += estimateMsgpackIntegerSize(cityIndexes[i]);
            size += estimateMsgpackIntegerSize(cityValues[i]);
        }
        return size;
    }

    private static int estimateMaskedPatchFrameSize(
            long timeOffset,
            long[] maskWords,
            long[] cityValues,
            int changeCount
    ) {
        int size = estimateMsgpackArrayHeaderSize(2 + maskWords.length + changeCount);
        size += estimateMsgpackIntegerSize(timeOffset);
        size += estimateMsgpackIntegerSize(-maskWords.length);
        for (long maskWord : maskWords) {
            size += estimateMsgpackIntegerSize(maskWord);
        }
        for (int i = 0; i < changeCount; i++) {
            size += estimateMsgpackIntegerSize(cityValues[i]);
        }
        return size;
    }

    private static int estimateMsgpackArrayHeaderSize(int elementCount) {
        if (elementCount <= 15) {
            return 1;
        }
        if (elementCount <= 0xFFFF) {
            return 3;
        }
        return 5;
    }

    private static int estimateMsgpackIntegerSize(long value) {
        if (value >= 0) {
            if (value <= 0x7FL) {
                return 1;
            }
            if (value <= 0xFFL) {
                return 2;
            }
            if (value <= 0xFFFFL) {
                return 3;
            }
            if (value <= 0xFFFF_FFFFL) {
                return 5;
            }
            return 9;
        }

        if (value >= -32L) {
            return 1;
        }
        if (value >= Byte.MIN_VALUE) {
            return 2;
        }
        if (value >= Short.MIN_VALUE) {
            return 3;
        }
        if (value >= Integer.MIN_VALUE) {
            return 5;
        }
        return 9;
    }

    private static List<Long> encodeMaskedPatchFrame(
            long timeOffset,
            long[] maskWords,
            long[] cityValues,
            int changeCount
    ) {
        LongArrayList encoded = new LongArrayList(2 + maskWords.length + changeCount);
        encoded.add(timeOffset);
        encoded.add(-maskWords.length);
        for (long maskWord : maskWords) {
            encoded.add(maskWord);
        }
        for (int i = 0; i < changeCount; i++) {
            encoded.add(cityValues[i]);
        }

        return encoded;
    }
}

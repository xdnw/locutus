package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2LongArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConflictUtil {
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
            List<Byte> cities) {
        List<List<List<List<Long>>>> turnMetricCitiesTables = new ObjectArrayList<>();
        for (int metricOrdinal : keys) {
            List<List<List<Long>>> metricCitiesTableByAA = new ObjectArrayList<>();
            for (int aaId : aaIds) {
                List<List<Long>> metricCitiesTable = new ObjectArrayList<>();
                for (long turnOrDay = start; turnOrDay <= end; turnOrDay++) {
                    Map<Integer, Map<Integer, Map<Byte, Long>>> dataAtTime = data.get(turnOrDay);
                    if (dataAtTime == null) {
                        metricCitiesTable.add(new ObjectArrayList<>());
                        continue;
                    }
                    Map<Integer, Map<Byte, Long>> metricDataByAA = dataAtTime.get(metricOrdinal);
                    if (metricDataByAA == null) {
                        metricCitiesTable.add(new ObjectArrayList<>());
                        continue;
                    }
                    Map<Byte, Long> metricData = metricDataByAA.get(aaId);
                    if (metricData == null) {
                        metricCitiesTable.add(new ObjectArrayList<>());
                        continue;
                    }
                    List<Long> values = new ObjectArrayList<>(cities.size());
                    for (byte city : cities) {
                        Long value = metricData.get(city);
                        if (value == null && !metricData.containsKey(city)) {
                            values.add(null);
                        } else {
                            values.add(value);
                        }
                    }
                    metricCitiesTable.add(values);
                }
                metricCitiesTableByAA.add(metricCitiesTable);
            }
            turnMetricCitiesTables.add(metricCitiesTableByAA);
        }
        return turnMetricCitiesTables;
    }
}

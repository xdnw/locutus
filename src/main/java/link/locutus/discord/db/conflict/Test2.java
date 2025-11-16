package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2LongArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class Test2 {
    public static void main(String[] args) {
        Test2 instance = new Test2();

        // Run deterministic test
        deterministicSimpleCase(instance);

        // Run randomized consistency test 100 times
        randomizedConsistency(instance);

        System.out.println("All tests passed.");

        runBenchmark(instance);
    }

    private static void runBenchmark(Test2 instance) {
        final int WARMUP = 20;
        final int ITER = 400;
        Random rnd = new Random(12345);

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> base = randomData(rnd);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> a = deepCopy(base);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> b = deepCopy(base);
            instance.trimTimeData(a);
            instance.trimTimeDataLegacy(b);
        }

        // Measured runs
        long totalNewNs = 0;
        long totalLegacyNs = 0;

        for (int i = 0; i < ITER; i++) {
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> base = randomData(rnd);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> a = deepCopy(base);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> b = deepCopy(base);

            long t0 = System.nanoTime();
            instance.trimTimeData(a);
            long t1 = System.nanoTime();
            instance.trimTimeDataLegacy(b);
            long t2 = System.nanoTime();

            totalNewNs += (t1 - t0);
            totalLegacyNs += (t2 - t1);

            if (!a.equals(b)) {
                throw new AssertionError("Benchmark mismatch on iteration " + i);
            }
        }

        System.out.println("Benchmark results:");
        printResults("trimTimeData (new)", totalNewNs, ITER);
        printResults("trimTimeDataLegacy (legacy)", totalLegacyNs, ITER);
    }

    private static void printResults(String name, long totalNs, int iterations) {
        double ms = totalNs / 1_000_000.0;
        double avgMs = ms / iterations;
        double opsPerSec = iterations / (totalNs / 1_000_000_000.0);
        System.out.printf("%s: total=%.3f ms, avg=%.6f ms, ops/sec=%.2f%n", name, ms, avgMs, opsPerSec);
    }

    // Simple replacement for JUnit's assertEquals
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * Small deterministic example: simple structure with one turn.
     */
    private static void deterministicSimpleCase(Test2 instance) {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> base =
                new Long2ObjectArrayMap<>();

        Map<Integer, Map<Integer, Map<Byte, Long>>> metrics = new Int2ObjectOpenHashMap<>();
        base.put(1L, metrics);

        Map<Integer, Map<Byte, Long>> metric1Alliances = new Int2ObjectOpenHashMap<>();
        metrics.put(100, metric1Alliances);

        Map<Byte, Long> alliance1Cities = new Byte2LongOpenHashMap();
        alliance1Cities.put((byte) 1, 10L);
        alliance1Cities.put((byte) 2, 0L);
        metric1Alliances.put(200, alliance1Cities);

        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dataNew    = deepCopy(base);
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dataLegacy = deepCopy(base);

        instance.trimTimeData(dataNew);
        instance.trimTimeDataLegacy(dataLegacy);

        assertEquals(dataLegacy, dataNew, "deterministicSimpleCase failed:");
    }

    /**
     * Randomized test to catch subtle differences.
     */
    private static void randomizedConsistency(Test2 instance) {
        Random rnd = new Random();

        for (int i = 0; i < 100; i++) {
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> base       = randomData(rnd);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dataNew    = deepCopy(base);
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dataLegacy = deepCopy(base);

            instance.trimTimeData(dataNew);
            instance.trimTimeDataLegacy(dataLegacy);

            // fastutil maps implement Map.equals correctly, so this is a deep structural comparison
            assertEquals(dataLegacy, dataNew, "randomizedConsistency iteration " + i + " failed:");
        }
    }

    /**
     * Deep copy of the nested map structure.
     */
    private static Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> deepCopy(
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> src) {

        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> copy = new Long2ObjectArrayMap<>();

        for (Map.Entry<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> e1 : src.entrySet()) {
            Long turn = e1.getKey();
            Map<Integer, Map<Integer, Map<Byte, Long>>> metrics = e1.getValue();

            Map<Integer, Map<Integer, Map<Byte, Long>>> metricsCopy = new Int2ObjectOpenHashMap<>();
            copy.put(turn, metricsCopy);

            for (Map.Entry<Integer, Map<Integer, Map<Byte, Long>>> e2 : metrics.entrySet()) {
                Integer metricId = e2.getKey();
                Map<Integer, Map<Byte, Long>> alliances = e2.getValue();

                Map<Integer, Map<Byte, Long>> alliancesCopy = new Int2ObjectOpenHashMap<>();
                metricsCopy.put(metricId, alliancesCopy);

                for (Map.Entry<Integer, Map<Byte, Long>> e3 : alliances.entrySet()) {
                    Integer allianceId = e3.getKey();
                    Map<Byte, Long> cities = e3.getValue();

                    Map<Byte, Long> citiesCopy = new Byte2LongOpenHashMap();
                    citiesCopy.putAll(cities);
                    alliancesCopy.put(allianceId, citiesCopy);
                }
            }
        }

        return copy;
    }

    /**
     * Generates a random nested structure, using fastutil implementations.
     */
    private static Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> randomData(Random rnd) {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> top = new Long2ObjectArrayMap<>();

        int turnCount = rnd.nextInt(1, 10);  // up to 9 turns
        for (int i = 0; i < turnCount; i++) {
            long turn = rnd.nextLong(1, 50); // small-ish turn numbers

            Map<Integer, Map<Integer, Map<Byte, Long>>> metrics = new Int2ObjectOpenHashMap<>();
            top.put(turn, metrics);

            int metricCount = rnd.nextInt(0, 5);
            for (int m = 0; m < metricCount; m++) {
                int metricId = rnd.nextInt(1, 5);

                Map<Integer, Map<Byte, Long>> alliances = new Int2ObjectOpenHashMap<>();
                metrics.put(metricId, alliances);

                int allianceCount = rnd.nextInt(0, 5);
                for (int a = 0; a < allianceCount; a++) {
                    int allianceId = rnd.nextInt(1, 5);

                    Map<Byte, Long> cities = new Byte2LongOpenHashMap();
                    alliances.put(allianceId, cities);

                    int cityCount = rnd.nextInt(0, 5);
                    for (int c = 0; c < cityCount; c++) {
                        byte cityId = (byte) rnd.nextInt(1, 10);
                        long value = rnd.nextBoolean() ? 0L : rnd.nextLong(0, 1000);
                        cities.put(cityId, value);
                    }
                }
            }
        }
        return top;
    }

private void trimTimeDataLegacy(Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData) {
    if (turnData.size() < 2) return;
    Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> trimmed = new Long2ObjectArrayMap<>();
    List<Long> turnsSorted = new LongArrayList(turnData.keySet());
    turnsSorted.sort(Long::compareTo);
    Map<Integer, Map<Integer, Map<Byte, Long>>> previous = new Int2ObjectOpenHashMap<>();
    for (Long currentTurn : turnsSorted) {
        Map<Integer, Map<Integer, Map<Byte, Long>>> currentData = turnData.get(currentTurn);
        if (currentData == null || currentData.isEmpty()) continue;

        for (Map.Entry<Integer, Map<Integer, Map<Byte, Long>>> entry : currentData.entrySet()) {
            Map<Integer, Map<Byte, Long>> currentByMetric = entry.getValue();
            Map<Integer, Map<Byte, Long>> previousByMetric = previous.get(entry.getKey());
            if (previousByMetric == null) {
                Map<Integer, Map<Byte, Long>> copy = new Int2ObjectOpenHashMap<>(currentByMetric.size());
                for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : currentByMetric.entrySet()) {
                    copy.put(allianceEntry.getKey(), new Byte2LongOpenHashMap(allianceEntry.getValue()));
                }
                previous.put(entry.getKey(), copy);
                trimmed.computeIfAbsent(currentTurn, k -> new Int2ObjectOpenHashMap<>()).put(entry.getKey(), currentByMetric);
                continue;
            }
            for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : currentByMetric.entrySet()) {
                Map<Byte, Long> currentByAlliance = allianceEntry.getValue();
                Map<Byte, Long> previousByAlliance = previousByMetric.get(allianceEntry.getKey());
                if (previousByAlliance == null) {
                    previousByMetric.put(allianceEntry.getKey(), new Byte2LongOpenHashMap(currentByAlliance));
                    trimmed.computeIfAbsent(currentTurn, k -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent(entry.getKey(), k -> new Int2ObjectOpenHashMap<>())
                            .put(allianceEntry.getKey(), currentByAlliance);
                    continue;
                }
                for (Map.Entry<Byte, Long> cityEntry : currentByAlliance.entrySet()) {
                    Long currentValue = cityEntry.getValue();
                    Long previousValue = previousByAlliance.get(cityEntry.getKey());
                    if (currentValue != null) {
                        if (!currentValue.equals(previousValue)) {
                            previousByAlliance.put(cityEntry.getKey(), currentValue);
                            trimmed.computeIfAbsent(currentTurn, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(entry.getKey(), k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(allianceEntry.getKey(), k -> new Byte2LongOpenHashMap())
                                    .put(cityEntry.getKey(), currentValue);
                        }
                    }
                }
                for (Map.Entry<Byte, Long> cityEntry : previousByAlliance.entrySet()) {
                    if (cityEntry.getValue() != 0) {
                        Long currentValue = currentByAlliance.get(cityEntry.getKey());
                        if (currentValue == null) {
                            previousByAlliance.put(cityEntry.getKey(), 0L);
                            trimmed.computeIfAbsent(currentTurn, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(entry.getKey(), k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(allianceEntry.getKey(), k -> new Byte2LongOpenHashMap())
                                    .put(cityEntry.getKey(), 0L);
                        }
                    }
                }
            }
            for (Map.Entry<Integer, Map<Byte, Long>> allianceEntry : previousByMetric.entrySet()) {
                Map<Byte, Long> previousByAlliance = allianceEntry.getValue();
                Map<Byte, Long> currentByAlliance = currentByMetric.get(allianceEntry.getKey());
                if (currentByAlliance == null) {
                    for (Map.Entry<Byte, Long> cityEntry : previousByAlliance.entrySet()) {
                        if (cityEntry.getValue() != 0) {
                            previousByAlliance.put(cityEntry.getKey(), 0L);
                            trimmed.computeIfAbsent(currentTurn, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(entry.getKey(), k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(allianceEntry.getKey(), k -> new Byte2LongOpenHashMap())
                                    .put(cityEntry.getKey(), 0L);
                        }
                    }
                }
            }
        }
    }

    // Replace the original map with the trimmed one
    turnData.clear();
    turnData.putAll(trimmed);
}

    private void trimTimeData(Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData) {
        if (turnData.size() < 2) {
            return;
        }

        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> trimmed =
                new Long2ObjectArrayMap<>(turnData.size());
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

                    ObjectIterator<Byte2LongMap.Entry> cityIterator =
                            currentByAlliance.byte2LongEntrySet().fastIterator();
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

                    ObjectIterator<Byte2LongMap.Entry> previousIterator =
                            previousByAlliance.byte2LongEntrySet().fastIterator();
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

                ObjectIterator<Int2ObjectMap.Entry<Byte2LongOpenHashMap>> previousAllianceIterator =
                        previousByMetric.int2ObjectEntrySet().iterator();
                while (previousAllianceIterator.hasNext()) {
                    Int2ObjectMap.Entry<Byte2LongOpenHashMap> previousAllianceEntry =
                            previousAllianceIterator.next();
                    int allianceId = previousAllianceEntry.getIntKey();

                    if (currentByMetric.containsKey(allianceId)) {
                        continue;
                    }

                    Byte2LongOpenHashMap previousByAlliance = previousAllianceEntry.getValue();
                    Byte2LongArrayMap delta = null;

                    ObjectIterator<Byte2LongMap.Entry> cityIterator =
                            previousByAlliance.byte2LongEntrySet().fastIterator();
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
}

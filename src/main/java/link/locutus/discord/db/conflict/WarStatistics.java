package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.conflict.DayTierGraphData;
import link.locutus.discord.db.entities.conflict.TurnTierGraphData;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.jooby.JteUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WarStatistics {
    private static final List<ConflictMetric> DAY_GRAPH_METRICS = List.of(
            ConflictMetric.NATION,
            ConflictMetric.BEIGE,
            ConflictMetric.INFRA,
            ConflictMetric.SOLDIER_CAPACITY,
            ConflictMetric.TANK_CAPACITY,
            ConflictMetric.AIRCRAFT_CAPACITY,
            ConflictMetric.SHIP_CAPACITY
    );

    public static final class DayTierMetrics {
        public int nationCount;
        public int infra;
        public int beigeCount;
        public int soldierCapacity;
        public int tankCapacity;
        public int aircraftCapacity;
        public int shipCapacity;
    }

    private final CoalitionSide parent;

    public volatile long latestGraphTurn = -1L;
    public final Map<Integer, Integer> allianceIdByNation = new Int2ObjectOpenHashMap<>();
    public final DamageStatGroup inflictedAndOffensiveStats = new DamageStatGroup();
    public final DamageStatGroup lossesAndDefensiveStats = new DamageStatGroup();
    public final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByAlliance = new Int2ObjectOpenHashMap<>();
    public final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByNation = new Int2ObjectOpenHashMap<>();
    public final Map<Long, TurnTierGraphData> graphDataByTurn = new Long2ObjectArrayMap<>();
    public final Map<Long, DayTierGraphData> graphDataByDay = new Long2ObjectArrayMap<>();
    public final Map<Long, Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>>> damageByDayByAllianceByCity = new Long2ObjectArrayMap<>();

    public WarStatistics(CoalitionSide parent) {
        this.parent = parent;
    }

    public CoalitionSide getParent() {
        return parent;
    }

    public long getLatestGraphTurn() {
        return latestGraphTurn;
    }

    private void markLatestGraphTurn(long turn) {
        if (turn > latestGraphTurn) latestGraphTurn = turn;
    }

    public Set<Integer> getNationIds() {
        return damageByNation.keySet();
    }

    public void updateTurnChange(ConflictManager manager, long turn, boolean save) {
        Set<DBNation> nations = new AllianceList(getParent().getAllianceIds()).getNations(Locutus.imp().getNationDB(), true, 0, true);

        long day = TimeUtil.getDayFromTurn(turn);
        if (day != TimeUtil.getDayFromTurn(turn - 1)) {
            updateDayTierGraph(manager, day, nations, true, save);
        }
        updateTurnTierGraph(manager, turn, nations, true, save);
    }

    public void addGraphData(ConflictMetric metric, int allianceId, long turnOrDay, int city, int value) {
        if (metric.isDay()) {
            graphDataByDay.computeIfAbsent(turnOrDay, k -> new DayTierGraphData()).getOrCreate(metric, allianceId).put((byte) city, value);
        } else {
            graphDataByTurn.computeIfAbsent(turnOrDay, k -> new TurnTierGraphData()).getOrCreate(metric, allianceId).put((byte) city, value);
            markLatestGraphTurn(turnOrDay);
        }
    }

    public void updateTurnTierGraph(ConflictManager manager, long turn, Map<Integer, Map<Byte, Map<MilitaryUnit, Integer>>> data, boolean force, boolean save) {
        if (!force) {
            if (graphDataByTurn.containsKey(turn)) return;
        } else {
            if (save && graphDataByTurn.containsKey(turn)) {
                manager.clearGraphData(List.of(
                                ConflictMetric.SOLDIER, ConflictMetric.TANK, ConflictMetric.AIRCRAFT, ConflictMetric.SHIP),
                        getParent().getId(), getParent().isPrimary(), turn);
            }
        }
        TurnTierGraphData graphData = new TurnTierGraphData();
        for (Map.Entry<Integer, Map<Byte, Map<MilitaryUnit, Integer>>> allianceEntry : data.entrySet()) {
            int allianceId = allianceEntry.getKey();
            for (Map.Entry<Byte, Map<MilitaryUnit, Integer>> entry : allianceEntry.getValue().entrySet()) {
                for (Map.Entry<MilitaryUnit, Integer> unitEntry : entry.getValue().entrySet()) {
                    graphData.getOrCreate(ConflictMetric.BY_UNIT.get(unitEntry.getKey()), allianceId).put(entry.getKey(), unitEntry.getValue());
                }
            }
        }
        graphDataByTurn.put(turn, graphData);
        markLatestGraphTurn(turn);
        if (save) {
            graphData.save(manager, getParent().getId(), getParent().isPrimary(), turn);
        }
    }

    public List<ConflictMetric.Entry> getGraphEntries() {
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        graphDataByTurn.forEach((k, v) -> entries.addAll(v.getEntries(parent.getId(), getParent().isPrimary(), k)));
        graphDataByDay.forEach((k, v) -> entries.addAll(v.getEntries(parent.getId(), getParent().isPrimary(), k)));
        return entries;
    }

    public void updateDayTierGraph(ConflictManager manager, long day, Set<DBNation> nations, boolean force, boolean save) {
        if (!force) {
            if (graphDataByDay.containsKey(day)) return;
        } else {
            if (save && graphDataByDay.containsKey(day)) {
                manager.clearGraphData(DAY_GRAPH_METRICS, getParent().getId(), getParent().isPrimary(), day);
            }
        }
        DayTierGraphData dayGraph = graphDataByDay.computeIfAbsent(day, k -> new DayTierGraphData());
        dayGraph.update(nations);
        if (save) {
            dayGraph.save(manager, getParent().getId(), getParent().isPrimary(), day);
        }
    }

    public void updateDayTierGraph(ConflictManager manager, long day, Map<Integer, Map<Byte, DayTierMetrics>> data, boolean force, boolean save) {
        if (!force) {
            if (graphDataByDay.containsKey(day)) return;
        } else {
            if (save && graphDataByDay.containsKey(day)) {
                manager.clearGraphData(DAY_GRAPH_METRICS, getParent().getId(), getParent().isPrimary(), day);
            }
        }

        DayTierGraphData graphData = new DayTierGraphData();
        for (Map.Entry<Integer, Map<Byte, DayTierMetrics>> allianceEntry : data.entrySet()) {
            int allianceId = allianceEntry.getKey();
            Map<Byte, Integer> nationByTier = graphData.getOrCreate(ConflictMetric.NATION, allianceId);
            Map<Byte, Integer> infraByTier = graphData.getOrCreate(ConflictMetric.INFRA, allianceId);
            Map<Byte, Integer> beigeByTier = graphData.getOrCreate(ConflictMetric.BEIGE, allianceId);
            Map<Byte, Integer> soldierCapacityByTier = graphData.getOrCreate(ConflictMetric.SOLDIER_CAPACITY, allianceId);
            Map<Byte, Integer> tankCapacityByTier = graphData.getOrCreate(ConflictMetric.TANK_CAPACITY, allianceId);
            Map<Byte, Integer> aircraftCapacityByTier = graphData.getOrCreate(ConflictMetric.AIRCRAFT_CAPACITY, allianceId);
            Map<Byte, Integer> shipCapacityByTier = graphData.getOrCreate(ConflictMetric.SHIP_CAPACITY, allianceId);
            for (Map.Entry<Byte, DayTierMetrics> tierEntry : allianceEntry.getValue().entrySet()) {
                byte cityTier = tierEntry.getKey();
                DayTierMetrics values = tierEntry.getValue();
                if (values.nationCount > 0) {
                    nationByTier.put(cityTier, values.nationCount);
                }
                if (values.infra > 0) {
                    infraByTier.put(cityTier, values.infra);
                }
                if (values.beigeCount > 0) {
                    beigeByTier.put(cityTier, values.beigeCount);
                }
                if (values.soldierCapacity > 0) {
                    soldierCapacityByTier.put(cityTier, values.soldierCapacity);
                }
                if (values.tankCapacity > 0) {
                    tankCapacityByTier.put(cityTier, values.tankCapacity);
                }
                if (values.aircraftCapacity > 0) {
                    aircraftCapacityByTier.put(cityTier, values.aircraftCapacity);
                }
                if (values.shipCapacity > 0) {
                    shipCapacityByTier.put(cityTier, values.shipCapacity);
                }
            }
        }
        graphDataByDay.put(day, graphData);
        if (save) {
            graphData.save(manager, getParent().getId(), getParent().isPrimary(), day);
        }
    }

    public void updateTurnTierGraph(ConflictManager manager, long turn, Set<DBNation> nations, boolean force, boolean save) {
        if (!force) {
            if (graphDataByTurn.containsKey(turn)) return;
        } else {
            if (save && graphDataByTurn.containsKey(turn)) {
                manager.clearGraphData(List.of(ConflictMetric.SOLDIER, ConflictMetric.TANK, ConflictMetric.AIRCRAFT, ConflictMetric.SHIP, ConflictMetric.SPIES), getParent().getId(), getParent().isPrimary(), turn);
            }
        }
        TurnTierGraphData turnGraph = graphDataByTurn.computeIfAbsent(turn, k -> new TurnTierGraphData());
        markLatestGraphTurn(turn);
        turnGraph.update(nations);
        if (save) {
            turnGraph.save(manager, getParent().getId(), getParent().isPrimary(), turn);
        }
    }

    public Map<String, Object> toDataMap() {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        List<Integer> allianceIds = new ArrayList<>(getParent().getAllianceIds());
        Collections.sort(allianceIds);
        List<Integer> nationIds = new IntArrayList(damageByNation.keySet());
        Collections.sort(nationIds);
        List<String> nationNames = new ObjectArrayList<>();
        for (int id : nationIds) {
            DBNation nation = DBNation.getById(id);
            String name = nation == null ? "" : nation.getName();
            nationNames.add(name);
        }
        List<Integer> nationAAs = nationIds.stream().map(allianceIdByNation::get).collect(Collectors.toList());
        root.put("nation_aa", nationAAs);
        root.put("nation_ids", nationIds);
        root.put("nation_names", nationNames);

        Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
        List<List<Object>> damageData = new ObjectArrayList<>();
        JteUtil.writeArray(damageData, damageHeader.values(), List.of(lossesAndDefensiveStats, inflictedAndOffensiveStats));
        JteUtil.writeArray(damageData, damageHeader.values(), allianceIds, damageByAlliance);
        JteUtil.writeArray(damageData, damageHeader.values(), nationIds, damageByNation);
        root.put("damage", damageData);
        return root;
    }

    public synchronized void clearGraphDataOutside(long turnStart, long turnEnd) {
        // Remove in-memory turn data outside the range
        graphDataByTurn.keySet().removeIf(turn -> turn < turnStart || turn > turnEnd);

        // Remove in-memory day data outside the range
        long dayStart = TimeUtil.getDayFromTurn(turnStart);
        long dayEnd = TimeUtil.getDayFromTurn(turnEnd);
        graphDataByDay.keySet().removeIf(day -> day < dayStart || day > dayEnd);

        // Also trim the per-day per-alliance-per-city damage cache
        damageByDayByAllianceByCity.keySet().removeIf(day -> day < dayStart || day > dayEnd);

        // Update latestGraphTurn
        latestGraphTurn = graphDataByTurn.keySet().stream()
                .max(Long::compareTo)
                .orElse(-1L);
    }

    private List<Integer> getSortedAllianceIds() {
        List<Integer> allianceIds = new ArrayList<>(getParent().getAllianceIds());
        Collections.sort(allianceIds);
        return allianceIds;
    }

    private List<Integer> buildTimelineEndOffsets(
            List<Integer> allianceIds,
            long rangeStart,
            long rangeEnd,
            boolean isDayTimeline
    ) {
        if (allianceIds.isEmpty() || rangeEnd < rangeStart) {
            return null;
        }

        Conflict conflict = parent.getParent();
        int defaultOffset = (int) Math.max(rangeEnd - rangeStart, 0L);
        List<Integer> offsets = new IntArrayList(allianceIds.size());
        boolean trimmed = false;

        for (int allianceId : allianceIds) {
            long endTurn = conflict.getEndTurn(allianceId);
            long endValue;
            if (endTurn == Long.MAX_VALUE) {
                endValue = rangeEnd;
            } else {
                endValue = isDayTimeline ? TimeUtil.getDayFromTurn(endTurn) : endTurn;
            }

            long clamped = Math.max(-1L, Math.min(endValue - rangeStart, defaultOffset));
            int offset = (int) clamped;
            offsets.add(offset);
            if (offset != defaultOffset) {
                trimmed = true;
            }
        }

        return trimmed ? offsets : null;
    }

    private List<Integer> buildTimelineStartOffsets(
            List<Integer> allianceIds,
            long rangeStart,
            long rangeEnd,
            boolean isDayTimeline
    ) {
        if (allianceIds.isEmpty() || rangeEnd < rangeStart) {
            return null;
        }

        Conflict conflict = parent.getParent();
        long maxOffset = Math.max(rangeEnd - rangeStart + 1, 0L);
        List<Integer> offsets = new IntArrayList(allianceIds.size());
        boolean trimmed = false;

        for (int allianceId : allianceIds) {
            long startTurn = conflict.getStartTurn(allianceId);
            long startValue = isDayTimeline ? TimeUtil.getDayFromTurn(startTurn) : startTurn;
            long clamped = Math.max(0L, Math.min(startValue - rangeStart, maxOffset));
            int offset = (int) clamped;
            offsets.add(offset);
            if (offset != 0) {
                trimmed = true;
            }
        }

        return trimmed ? offsets : null;
    }

    public Map<String, Object> toGraphMap(List<Integer> metricsTurn, List<Integer> metricsDay, List<Function<DamageStatGroup, Object>> damageHeaders, int columnMetricOffset) {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData = new Long2ObjectArrayMap<>();
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dayData = new Long2ObjectArrayMap<>();

        Set<Byte> cities = new ByteOpenHashSet();

        graphDataByTurn.forEach((turn, graphData) ->
                graphData.forEachEntry((metric, allianceId, city, value) -> {
                    turnData.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent(metric.ordinal(), k -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                            .put(city, (long) value);
                    cities.add(city);
                }));
        graphDataByDay.forEach((day, graphData) ->
                graphData.forEachEntry((metric, allianceId, city, value) -> {
                    dayData.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent(metric.ordinal(), k -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                            .put(city, (long) value);
                    cities.add(city);
                }));

        ConflictUtil.trimTimeData(turnData);
        ConflictUtil.trimTimeData(dayData);

        List<Long> days = new LongArrayList(damageByDayByAllianceByCity.keySet());
        days.sort(Long::compareTo);
        for (long day : days) {
            Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>> allianceMap = damageByDayByAllianceByCity.get(day);
            for (Map.Entry<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>> allianceEntry : allianceMap.entrySet()) {
                int allianceId = allianceEntry.getKey();
                Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>> cityMap = allianceEntry.getValue();

                for (Map.Entry<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>> cityEntry : cityMap.entrySet()) {
                    int city = cityEntry.getKey() & 0xFF;
                    Map.Entry<DamageStatGroup, DamageStatGroup> damage = cityEntry.getValue();
                    DamageStatGroup defDamage = damage.getKey();
                    DamageStatGroup offDamage = damage.getValue();

                    for (int j = 0; j < damageHeaders.size(); j++) {
                        Function<DamageStatGroup, Object> dmgHeader = damageHeaders.get(j);
                        int defI = columnMetricOffset + j * 2;
                        int offI = defI + 1;

                        long defValue = ((Number) dmgHeader.apply(defDamage)).longValue();
                        long offValue = ((Number) dmgHeader.apply(offDamage)).longValue();
                        if (defValue != 0) {
                            dayData.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(defI, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                                    .put((byte) city, defValue);
                            cities.add((byte) city);
                        }
                        if (offValue != 0) {
                            dayData.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(offI, k -> new Int2ObjectOpenHashMap<>())
                                    .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                                    .put((byte) city, offValue);
                            cities.add((byte) city);
                        }
                    }
                }
            }
        }


        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();

        List<Byte> citiesSorted = new ByteArrayList(cities);
        citiesSorted.sort(Byte::compareTo);
        root.put("cities", citiesSorted);

        List<Integer> allianceIds = getSortedAllianceIds();
        long[] turnRange = ConflictUtil.computeRange(turnData);
        long minTurn = turnRange[0];
        long maxTurn = turnRange[1];
        Map<String, Object> turnRoot = new LinkedHashMap<>();
        turnRoot.put("range", List.of(minTurn, maxTurn));
        turnRoot.put("encoding", "sparse_patch_v3");
        List<Integer> turnStartOffsets = turnData.isEmpty()
                ? null
                : buildTimelineStartOffsets(allianceIds, minTurn, maxTurn, false);
        List<Integer> turnEndOffsets = turnData.isEmpty()
                ? null
                : buildTimelineEndOffsets(allianceIds, minTurn, maxTurn, false);
        turnRoot.put("data", ConflictUtil.toGraphMapPart(metricsTurn, turnData, minTurn, maxTurn, allianceIds, citiesSorted, turnStartOffsets, turnEndOffsets));
        if (turnEndOffsets != null) {
            turnRoot.put("end_offsets", turnEndOffsets);
        }
        root.put("turn", turnRoot);

        long[] dayRange = ConflictUtil.computeRange(dayData);
        long minDay = dayRange[0];
        long maxDay = dayRange[1];
        Map<String, Object> dayRoot = new LinkedHashMap<>();
        dayRoot.put("range", List.of(minDay, maxDay));
        dayRoot.put("encoding", "sparse_patch_v3");
        List<Integer> dayStartOffsets = dayData.isEmpty()
                ? null
                : buildTimelineStartOffsets(allianceIds, minDay, maxDay, true);
        List<Integer> dayEndOffsets = dayData.isEmpty()
                ? null
                : buildTimelineEndOffsets(allianceIds, minDay, maxDay, true);
        dayRoot.put("data", ConflictUtil.toGraphMapPart(metricsDay, dayData, minDay, maxDay, allianceIds, citiesSorted, dayStartOffsets, dayEndOffsets));
        if (dayEndOffsets != null) {
            dayRoot.put("end_offsets", dayEndOffsets);
        }
        root.put("day", dayRoot);

        return root;
    }
}

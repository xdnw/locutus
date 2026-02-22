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
        Set<DBNation> nations = new AllianceList(getParent().getAllianceIds()).getNations(true, 0, true);

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
                manager.clearGraphData(List.of(ConflictMetric.INFRA, ConflictMetric.BEIGE, ConflictMetric.NATION), getParent().getId(), getParent().isPrimary(), day);
            }
        }
        DayTierGraphData dayGraph = graphDataByDay.computeIfAbsent(day, k -> new DayTierGraphData());
        dayGraph.update(nations);
        if (save) {
            dayGraph.save(manager, getParent().getId(), getParent().isPrimary(), day);
        }
    }

    public void updateDayTierGraph(ConflictManager manager, long day, Map<Integer, Map<Byte, int[]>> data, boolean force, boolean save) {
        if (!force) {
            if (graphDataByDay.containsKey(day)) return;
        } else {
            if (save && graphDataByDay.containsKey(day)) {
                manager.clearGraphData(List.of(ConflictMetric.INFRA, ConflictMetric.BEIGE, ConflictMetric.NATION), getParent().getId(), getParent().isPrimary(), day);
            }
        }

        DayTierGraphData graphData = new DayTierGraphData();
        for (Map.Entry<Integer, Map<Byte, int[]>> allianceEntry : data.entrySet()) {
            int allianceId = allianceEntry.getKey();
            Map<Byte, Integer> nationByTier = graphData.getOrCreate(ConflictMetric.NATION, allianceId);
            Map<Byte, Integer> infraByTier = graphData.getOrCreate(ConflictMetric.INFRA, allianceId);
            Map<Byte, Integer> beigeByTier = graphData.getOrCreate(ConflictMetric.BEIGE, allianceId);
            for (Map.Entry<Byte, int[]> tierEntry : allianceEntry.getValue().entrySet()) {
                byte cityTier = tierEntry.getKey();
                int[] values = tierEntry.getValue();
                if (values[0] > 0) {
                    nationByTier.put(cityTier, values[0]);
                }
                if (values[1] > 0) {
                    infraByTier.put(cityTier, values[1]);
                }
                if (values[2] > 0) {
                    beigeByTier.put(cityTier, values[2]);
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

    public Map<String, Object> toGraphMap(List<Integer> metricsTurn, List<Integer> metricsDay, List<Function<DamageStatGroup, Object>> damageHeaders, int columnMetricOffset) {
        List<ConflictMetric.Entry> entries = getGraphEntries();
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData = new Long2ObjectArrayMap<>();
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> dayData = new Long2ObjectArrayMap<>();

        Set<Byte> cities = new ByteOpenHashSet();

        for (ConflictMetric.Entry entry : entries) {
            Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> map = entry.metric().isDay() ? dayData : turnData;
            map.computeIfAbsent(entry.turnOrDay(), k -> new Int2ObjectOpenHashMap<>())
                    .computeIfAbsent(entry.metric().ordinal(), k -> new Int2ObjectOpenHashMap<>())
                    .computeIfAbsent(entry.allianceId(), k -> new Byte2LongOpenHashMap())
                    .put((byte) entry.city(), (long) entry.value());
            cities.add((byte) entry.city());
        }

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

        List<Integer> allianceIds = new ArrayList<>(getParent().getAllianceIds());
        Collections.sort(allianceIds);
        long[] turnRange = ConflictUtil.computeRange(turnData);
        long minTurn = turnRange[0];
        long maxTurn = turnRange[1];
        Map<String, Object> turnRoot = new LinkedHashMap<>();
        turnRoot.put("range", List.of(minTurn, maxTurn));
        turnRoot.put("data", ConflictUtil.toGraphMapPart(metricsTurn, turnData, minTurn, maxTurn, allianceIds, citiesSorted));
        root.put("turn", turnRoot);

        long[] dayRange = ConflictUtil.computeRange(dayData);
        long minDay = dayRange[0];
        long maxDay = dayRange[1];
        Map<String, Object> dayRoot = new LinkedHashMap<>();
        dayRoot.put("range", List.of(minDay, maxDay));
        dayRoot.put("data", ConflictUtil.toGraphMapPart(metricsDay, dayData, minDay, maxDay, allianceIds, citiesSorted));
        root.put("day", dayRoot);

        return root;
    }
}

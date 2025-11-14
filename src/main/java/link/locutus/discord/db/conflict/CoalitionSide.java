package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.conflict.DayTierGraphData;
import link.locutus.discord.db.entities.conflict.TurnTierGraphData;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.web.jooby.JteUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CoalitionSide {
    private final Conflict parent;
    private volatile boolean loaded;
    private String name;
    private final boolean isPrimary;
    private CoalitionSide otherSide;
    private final Set<Integer> coalition = new IntOpenHashSet();

    private volatile long latestGraphTurn = -1L;

    private final Map<Integer, Integer> allianceIdByNation = new Int2ObjectOpenHashMap<>();
    private final DamageStatGroup inflictedAndOffensiveStats = new DamageStatGroup();
    private final DamageStatGroup lossesAndDefensiveStats = new DamageStatGroup();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Long, TurnTierGraphData> graphDataByTurn = new Long2ObjectArrayMap<>();
    private final Map<Long, DayTierGraphData> graphDataByDay = new Long2ObjectArrayMap<>();
    private final Map<Long, Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>>> damageByDayByAllianceByCity = new Long2ObjectArrayMap<>();

    public synchronized void clearWarData() {
        inflictedAndOffensiveStats.clear();
        lossesAndDefensiveStats.clear();
        damageByAlliance.clear();
        damageByNation.clear();
        damageByDayByAllianceByCity.clear();
        graphDataByTurn.clear();
        graphDataByDay.clear();
        allianceIdByNation.clear();
        this.loaded = false;
        latestGraphTurn = -1L;
    }

    public long getLatestGraphTurn() {
        return latestGraphTurn;
    }

    private void markLatestGraphTurn(long turn) {
        if (turn > latestGraphTurn) latestGraphTurn = turn;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Set<Integer> getNationIds() {
        return damageByNation.keySet();
    }

    public void updateTurnChange(ConflictManager manager, long turn, boolean save) {
        Set<DBNation> nations = new AllianceList(coalition).getNations(true, 0, true);

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
                        getParent().getId(), isPrimary, turn);
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
            graphData.save(manager, getParent().getId(), isPrimary, turn);
        }
    }

    public List<ConflictMetric.Entry> getGraphEntries() {
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        graphDataByTurn.forEach((k, v) -> entries.addAll(v.getEntries(parent.getId(), isPrimary, k)));
        graphDataByDay.forEach((k, v) -> entries.addAll(v.getEntries(parent.getId(), isPrimary, k)));
        return entries;
    }

    public void updateDayTierGraph(ConflictManager manager, long day, Set<DBNation> nations, boolean force, boolean save) {
        if (!force) {
            if (graphDataByDay.containsKey(day)) return;
        } else {
            if (save && graphDataByDay.containsKey(day)) {
                manager.clearGraphData(List.of(ConflictMetric.INFRA, ConflictMetric.BEIGE, ConflictMetric.NATION), getParent().getId(), isPrimary, day);
            }
        }
        DayTierGraphData dayGraph = graphDataByDay.computeIfAbsent(day, k -> new DayTierGraphData());
        dayGraph.update(nations);
        if (save) {
            dayGraph.save(manager, getParent().getId(), isPrimary, day);
        }
    }

    public void updateTurnTierGraph(ConflictManager manager, long turn, Set<DBNation> nations, boolean force, boolean save) {
        if (!force) {
            if (graphDataByTurn.containsKey(turn)) return;
        } else {
            if (save && graphDataByTurn.containsKey(turn)) {
                manager.clearGraphData(List.of(ConflictMetric.SOLDIER, ConflictMetric.TANK, ConflictMetric.AIRCRAFT, ConflictMetric.SHIP, ConflictMetric.SPIES), getParent().getId(), isPrimary, turn);
            }
        }
        TurnTierGraphData turnGraph = graphDataByTurn.computeIfAbsent(turn, k -> new TurnTierGraphData());
        markLatestGraphTurn(turn);
        turnGraph.update(nations);
        if (save) {
            turnGraph.save(manager, getParent().getId(), isPrimary, turn);
        }
    }

    public List<Integer> getAllianceIdsSorted() {
        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);
        return allianceIds;
    }

    public Map<String, Object> toMap(ConflictManager manager, boolean meta, boolean data) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        List<Integer> allianceIds = new ArrayList<>(coalition);

        Collections.sort(allianceIds);
        if (meta) {
            root.put("name", getName());
            List<String> allianceNames = new ObjectArrayList<>();
            for (int id : allianceIds) {
                String aaName = manager.getAllianceNameOrNull(id);
                if (aaName == null) aaName = "";
                allianceNames.add(aaName);
            }
            root.put("alliance_ids", allianceIds);
            root.put("alliance_names", allianceNames);
        }

        if (data) {
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
        }
        return root;
    }

    public void add(int allianceId) {
        if (allianceId == 0) throw new IllegalArgumentException("Alliance ID cannot be 0. " + this.getName());
        coalition.add(allianceId);
    }

    public void remove(int allianceId) {
        coalition.remove(allianceId);
    }

    public Set<Integer> getAllianceIds() {
        return coalition;
    }

    public String getName() {
        return name;
    }

    public DamageStatGroup getOffensiveStats(Integer id, boolean isAlliance) {
        if (id == null) return inflictedAndOffensiveStats;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? damageByAlliance.get(id) : damageByNation.get(id);
        return pair == null ? null : pair.getValue();
    }
    public DamageStatGroup getDefensiveStats(Integer id, boolean isAlliance) {
        if (id == null) return lossesAndDefensiveStats;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? damageByAlliance.get(id) : damageByNation.get(id);
        return pair == null ? null : pair.getKey();
    }

    public DamageStatGroup getLosses() {
        return getDamageStats(true, null, false);
    }

    public DamageStatGroup getInflicted() {
        return getDamageStats(false, null, false);
    }

    public DamageStatGroup getDamageStats(boolean isLosses, Integer id, boolean isAlliance) {
        if (id == null) return isLosses ? lossesAndDefensiveStats : inflictedAndOffensiveStats;
        Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> map = isAlliance ? damageByAlliance : damageByNation;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = map.computeIfAbsent(id, k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
        return isLosses ? pair.getKey() : pair.getValue();
    }

    private Map.Entry<DamageStatGroup, DamageStatGroup> getAllianceDamageStatsByDayPair(int id, int cities, long day) {
        return damageByDayByAllianceByCity.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(id, k -> new Byte2ObjectOpenHashMap<>())
                .computeIfAbsent((byte) cities, k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
    }

    private DamageStatGroup getAllianceDamageStatsByDay(boolean isLosses, int id, int cities, long day) {
        Map.Entry<DamageStatGroup, DamageStatGroup> entry = getAllianceDamageStatsByDayPair(id, cities, day);
        return isLosses ? entry.getKey() : entry.getValue();
    }

    public CoalitionSide(Conflict parent, String name, boolean isPrimary, boolean loaded) {
        this.parent = parent;
        this.name = name;
        this.isPrimary = isPrimary;
        this.loaded = loaded;
    }

    public void setNameRaw(String name) {
        this.name = name;
    }

    public boolean hasAlliance(int aaId) {
        return coalition.contains(aaId);
    }

    public CoalitionSide getOther() {
        return otherSide;
    }

    public Conflict getParent() {
        return parent;
    }

    protected void setOther(CoalitionSide coalition) {
        this.otherSide = coalition;
    }

    private void applyAttackerStats(int allianceId, int nationId, int cities, long day, Consumer<DamageStatGroup> onEach) {
        Integer existing = allianceIdByNation.get(nationId);
        if (existing == null || existing > allianceId) {
            allianceIdByNation.put(nationId, allianceId);
        }
        onEach.accept(getDamageStats(false, allianceId, true));
        onEach.accept(getDamageStats(false, nationId, false));
        onEach.accept(getAllianceDamageStatsByDay(false, allianceId, cities, day));
    }

    private void applyDefenderStats(int allianceId, int nationId, int cities, long day, Consumer<DamageStatGroup> onEach) {
        Integer existing = allianceIdByNation.get(nationId);
        if (existing == null || existing > allianceId) {
            allianceIdByNation.put(nationId, allianceId);
        }
        onEach.accept(getDamageStats(true, allianceId, true));
        onEach.accept(getDamageStats(true, nationId, false));
        onEach.accept(getAllianceDamageStatsByDay(true, allianceId, cities, day));
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        int cities;
        long day = TimeUtil.getDay(current.getDate());
        int attackerAA, attackerId;
        if (isAttacker) {
            cities = current.getAttCities();
            attackerAA = current.getAttacker_aa();
            attackerId = current.getAttacker_id();
            if (previous == null) {
                inflictedAndOffensiveStats.newWar(current, true);
                applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.newWar(current, true));
            } else {
                inflictedAndOffensiveStats.updateWar(previous, current, true);
                applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.updateWar(previous, current, true));
            }
        } else {
            attackerAA = current.getDefender_aa();
            attackerId = current.getDefender_id();
            cities = current.getDefCities();
            if (previous == null) {
                lossesAndDefensiveStats.newWar(current, false);
                applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.newWar(current, false));
            } else {
                lossesAndDefensiveStats.updateWar(previous, current, false);
                applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.updateWar(previous, current, false));
            }
        }
    }

    public void updateAttack(DBWar war, AbstractCursor attack, int attackerAA, int nationId, int cities, long day, boolean isAttacker, AttackTypeSubCategory subCategory) {
        Map.Entry<DamageStatGroup, DamageStatGroup> aaDamage = damageByAlliance.computeIfAbsent(attackerAA,
                k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
        Map.Entry<DamageStatGroup, DamageStatGroup> nationDamage = damageByNation.computeIfAbsent(nationId,
                k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));

        Map.Entry<DamageStatGroup, DamageStatGroup> tierDamage = getAllianceDamageStatsByDayPair(attackerAA, cities, day);

        if (isAttacker) {
            inflictedAndOffensiveStats.newAttack(war, attack, subCategory);

            lossesAndDefensiveStats.apply(attack, war, true);
            inflictedAndOffensiveStats.apply(attack, war, false);

            aaDamage.getKey().apply(attack, war, true);
            aaDamage.getValue().apply(attack, war, false);

            nationDamage.getKey().apply(attack, war, true);
            nationDamage.getValue().apply(attack, war, false);

            tierDamage.getKey().apply(attack, war, true);
            tierDamage.getValue().apply(attack, war, false);

            applyAttackerStats(attackerAA, nationId, cities, day, p -> p.newAttack(war, attack, subCategory));
        } else {
            lossesAndDefensiveStats.newAttack(war, attack, subCategory);

            lossesAndDefensiveStats.apply(attack, war, false);
            inflictedAndOffensiveStats.apply(attack, war, true);

            aaDamage.getKey().apply(attack, war, false);
            aaDamage.getValue().apply(attack, war, true);

            nationDamage.getKey().apply(attack, war, false);
            nationDamage.getValue().apply(attack, war, true);

            tierDamage.getKey().apply(attack, war, false);
            tierDamage.getValue().apply(attack, war, true);

            applyDefenderStats(attackerAA, nationId, cities, day, p -> p.newAttack(war, attack, subCategory));
        }
    }

    private void trimTimeData(Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> turnData) {
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

    private Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> sortTimeMap(Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> data) {
        Map<Long, Map<Integer, Map<Integer, Map<Byte, Long>>>> copy = new Long2ObjectLinkedOpenHashMap<>();
        List<Long> turns = new LongArrayList(data.keySet());
        turns.sort(Long::compareTo);
        for (long turn : turns) {
            Map<Integer, Map<Integer, Map<Byte, Long>>> map = data.get(turn);
            Map<Integer, Map<Integer, Map<Byte, Long>>> sorted = new Int2ObjectLinkedOpenHashMap<>();
            IntArrayList keysSorted = new IntArrayList(map.keySet());
            keysSorted.sort(Integer::compareTo);
            for (int metricId : keysSorted) {
                Map<Integer, Map<Byte, Long>> allianceMap = map.get(metricId);
                Map<Integer, Map<Byte, Long>> allianceSorted = new Int2ObjectLinkedOpenHashMap<>();
                IntArrayList allianceKeySorted = new IntArrayList(allianceMap.keySet());
                allianceKeySorted.sort(Integer::compareTo);
                for (int allianceId : allianceKeySorted) {
                    Map<Byte, Long> cityMap = allianceMap.get(allianceId);
                    Map<Byte, Long> citySorted = new Byte2LongLinkedOpenHashMap();
                    ByteArrayList cityKeySorted = new ByteArrayList(cityMap.keySet());
                    cityKeySorted.sort(Byte::compareTo);
                    for (byte city : cityKeySorted) {
                        Long value = cityMap.get(city);
                        if (value != 0) {
                            citySorted.put(city, value);
                        }
                    }
                    allianceSorted.put(allianceId, citySorted);
                }
                sorted.put(metricId, allianceSorted);
            }
            copy.put(turn, sorted);
        }
        return copy;
    }

    public Map<String, Object> toGraphMap(ConflictManager manager, List<Integer> metricsTurn, List<Integer> metricsDay, List<Function<DamageStatGroup, Object>> damageHeaders, int columnMetricOffset) {
        List<ConflictMetric.Entry> entries = getGraphEntries();
        // day -> metric -> alliance -> city -> value
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

        trimTimeData(turnData);
        trimTimeData(dayData);

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

        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);
        long minTurn = turnData.keySet().stream().min(Long::compareTo).orElse(0L);
        long maxTurn = turnData.keySet().stream().max(Long::compareTo).orElse(0L);
        Map<String, Object> turnRoot = new LinkedHashMap<>();
        turnRoot.put("range", List.of(minTurn, maxTurn));
        turnRoot.put("data", toGraphMap(metricsTurn, turnData, minTurn, maxTurn, allianceIds, citiesSorted));
        root.put("turn", turnRoot);

        long minDay = dayData.keySet().stream().min(Long::compareTo).orElse(0L);
        long maxDay = dayData.keySet().stream().max(Long::compareTo).orElse(0L);
        Map<String, Object> dayRoot = new LinkedHashMap<>();
        dayRoot.put("range", List.of(minDay, maxDay));
        dayRoot.put("data", toGraphMap(metricsDay, dayData, minDay, maxDay, allianceIds, citiesSorted));
        root.put("day", dayRoot);

        return root;
    }

    private List<List<List<List<Long>>>> toGraphMap(List<Integer> keys,
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
                    List<Long> values = cities.stream().map(f -> metricData.getOrDefault(f, null)).collect(Collectors.toList());
                    metricCitiesTable.add(values);
                }
                metricCitiesTableByAA.add(metricCitiesTable);
            }
            turnMetricCitiesTables.add(metricCitiesTableByAA);
        }
        return turnMetricCitiesTables;
    }
}
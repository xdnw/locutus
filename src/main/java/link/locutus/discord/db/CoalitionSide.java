package link.locutus.discord.db;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
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
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.web.jooby.JteUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CoalitionSide {
    private final Conflict parent;
    private String name;
    private final boolean isPrimary;
    private CoalitionSide otherSide;
    private final Set<Integer> coalition = new IntOpenHashSet();
//    private final OffDefStatGroup offensiveStats = new OffDefStatGroup();
//    private final OffDefStatGroup defensiveStats = new OffDefStatGroup();
//    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByAlliance = new Int2ObjectOpenHashMap<>();
//    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Integer> allianceIdByNation = new Int2ObjectOpenHashMap<>();
    private final DamageStatGroup inflictedAndOffensiveStats = new DamageStatGroup();
    private final DamageStatGroup lossesAndDefensiveStats = new DamageStatGroup();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Long, TurnTierGraphData> graphDataByTurn = new Long2ObjectArrayMap<>();
    private final Map<Long, DayTierGraphData> graphDataByDay = new Long2ObjectArrayMap<>();
//    private final Map<Long, Map<Integer, Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>>>> statsByDayByAllianceByCity = new Long2ObjectArrayMap<>();
    private final Map<Long, Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>>> damageByDayByAllianceByCity = new Long2ObjectArrayMap<>();

    public Set<Integer> getNationIds() {
        return damageByNation.keySet();
    }

    public void addGraphData(ConflictMetric metric, int allianceId, long turnOrDay, int city, int value) {
        if (metric.isDay()) {
            graphDataByDay.computeIfAbsent(turnOrDay, k -> new DayTierGraphData()).getOrCreate(metric, allianceId).put((byte) city, value);
        } else {
            graphDataByTurn.computeIfAbsent(turnOrDay, k -> new TurnTierGraphData()).getOrCreate(metric, allianceId).put((byte) city, value);
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

    public List<Integer> getAllianceIdsSorted() {
        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);
        return allianceIds;
    }

    public Map<String, Object> toMap(ConflictManager manager) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        root.put("name", getName());
        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);
        List<String> allianceNames = new ObjectArrayList<>();
        for (int id : allianceIds) {
            String aaName = manager.getAllianceNameOrNull(id);
            if (aaName == null) aaName = "";
            allianceNames.add(aaName);
        }
        root.put("alliance_ids", allianceIds);
        root.put("alliance_names", allianceNames);

        List<Integer> nationIds = new IntArrayList(damageByNation.keySet());
        Collections.sort(nationIds);
        List<String> nationNames = new ObjectArrayList<>();
        for (int id : nationIds) {
            DBNation nation = DBNation.getById(id);
            String name = nation == null ? "" : nation.getName();
            nationNames.add(name);
        }
        List<Integer> nationAAs = nationIds.stream().map(allianceIdByNation::get).collect(Collectors.toList());
        root.put("nation_aa",nationAAs);
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
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? damageByAlliance.get(id) : damageByNation.get(id);
        return pair == null ? null : isLosses ? pair.getKey() : pair.getValue();
    }

    public DamageStatGroup getAllianceDamageStatsByDay(boolean isLosses, int id, int cities, long day) {
        Map.Entry<DamageStatGroup, DamageStatGroup> entry = damageByDayByAllianceByCity.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(id, k -> new Byte2ObjectOpenHashMap<>())
                .computeIfAbsent((byte) cities, k -> Map.entry(new DamageStatGroup(), new DamageStatGroup()));
        return isLosses ? entry.getKey() : entry.getValue();
    }

    public CoalitionSide(Conflict parent, String name, boolean isPrimary) {
        this.parent = parent;
        this.name = name;
        this.isPrimary = isPrimary;
    }

    public void setName(String name) {
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
        allianceIdByNation.putIfAbsent(nationId, allianceId);
        onEach.accept(getDamageStats(false, allianceId, true));
        onEach.accept(getDamageStats(false, nationId, true));
        onEach.accept(getAllianceDamageStatsByDay(false, allianceId, cities, day));
    }

    private void applyDefenderStats(int allianceId, int nationId, int cities, long day, Consumer<DamageStatGroup> onEach) {
        allianceIdByNation.putIfAbsent(nationId, allianceId);
        onEach.accept(getDamageStats(true, allianceId, true));
        onEach.accept(getDamageStats(true, nationId, true));
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

    public void updateAttack(DBWar war, AbstractCursor attack, boolean isAttacker) {
        AttackTypeSubCategory subCategory = attack.getSubCategory(true);
        int attackerAA, attackerId, cities;
        long day = TimeUtil.getDay(attack.getDate());
        if (isAttacker) {
            attackerId = attack.getAttacker_id();
            if (attack.getAttacker_id() == war.getAttacker_id()) {
                attackerAA = war.getAttacker_aa();
                cities = war.getAttCities();
            } else {
                attackerAA = war.getDefender_aa();
                cities = war.getDefCities();
            }
        } else {
            attackerId = attack.getDefender_id();
            if (attack.getDefender_id() == war.getDefender_id()) {
                attackerAA = war.getDefender_aa();
                cities = war.getDefCities();
            } else {
                attackerAA = war.getAttacker_aa();
                cities = war.getAttCities();
            }
        }
        allianceIdByNation.putIfAbsent(attackerId, attackerAA);
        {
            if (isAttacker) {
                inflictedAndOffensiveStats.newAttack(war, attack, subCategory);
                applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.newAttack(war, attack, subCategory));
            } else {
                lossesAndDefensiveStats.newAttack(war, attack, subCategory);
                applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.newAttack(war, attack, subCategory));
            }
        }

        Map.Entry<DamageStatGroup, DamageStatGroup> aaDamage = damageByAlliance.computeIfAbsent(attackerAA,
                k -> Map.entry(new DamageStatGroup(), new DamageStatGroup()));
        Map.Entry<DamageStatGroup, DamageStatGroup> nationDamage = damageByNation.computeIfAbsent(attackerId,
                k -> Map.entry(new DamageStatGroup(), new DamageStatGroup()));
        if (isAttacker) {
            lossesAndDefensiveStats.apply(attack, true);
            inflictedAndOffensiveStats.apply(attack, false);
            aaDamage.getKey().apply(attack, true);
            aaDamage.getValue().apply(attack, false);
            nationDamage.getKey().apply(attack, true);
            nationDamage.getValue().apply(attack, false);
            applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.newAttack(war, attack, subCategory));
        } else {
            lossesAndDefensiveStats.apply(attack, false);
            inflictedAndOffensiveStats.apply(attack, true);
            aaDamage.getKey().apply(attack, false);
            aaDamage.getValue().apply(attack, true);
            nationDamage.getKey().apply(attack, false);
            nationDamage.getValue().apply(attack, true);
            applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.newAttack(war, attack, subCategory));
        }
    }

    public Map<String, Object> toGraphMap(ConflictManager manager, long turnStart, long turnEnd, List<Integer> metricsTurn, List<Integer> metricsDay, List<Function<DamageStatGroup, Object>> damageHeaders) {
        long dayStart = TimeUtil.getDay(TimeUtil.getTimeFromTurn(turnStart));
        long dayEnd = TimeUtil.getDay(TimeUtil.getTimeFromTurn(turnEnd + 11));
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

        int damageOrdinalOffset = metricsDay.size() - damageHeaders.size() * 2;
        for (Map.Entry<Long, Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>>> dayEntry : damageByDayByAllianceByCity.entrySet()) {
            long day = dayEntry.getKey();
            Map<Integer, Map<Byte, Map.Entry<DamageStatGroup, DamageStatGroup>>> allianceMap = dayEntry.getValue();
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
                        Long defValue = ((Number) dmgHeader.apply(defDamage)).longValue();
                        int defI = damageOrdinalOffset + j * 2;
                        Long offValue = ((Number) dmgHeader.apply(offDamage)).longValue();
                        int offI = defI + 1;
                        dayData.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                                .computeIfAbsent(defI, k -> new Int2ObjectOpenHashMap<>())
                                .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                                .put((byte) city, defValue);
                        dayData.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                                .computeIfAbsent(offI, k -> new Int2ObjectOpenHashMap<>())
                                .computeIfAbsent(allianceId, k -> new Byte2LongOpenHashMap())
                                .put((byte) city, offValue);
                    }
                }
            }
        }


        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        root.put("name", getName());

        List<Byte> citiesSorted = new ByteArrayList(cities);
        citiesSorted.sort(Byte::compareTo);
        root.put("cities", citiesSorted);

        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);

        root.put("turn_data", toGraphMap(metricsTurn, turnData, turnStart, turnEnd, allianceIds, citiesSorted));

        root.put("day_data", toGraphMap(metricsDay, dayData, dayStart, dayEnd, allianceIds, citiesSorted));

        Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createRanking();

        List<String> allianceNames = new ObjectArrayList<>();
        for (int id : allianceIds) {
            String aaName = manager.getAllianceNameOrNull(id);
            if (aaName == null) aaName = "";
            allianceNames.add(aaName);
        }
        root.put("alliance_ids", allianceIds);
        root.put("alliance_names", allianceNames);

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
                    Map<Integer, Map<Byte, Long>> metricDataByAA = dataAtTime.get(metricOrdinal);
                    if (metricDataByAA == null) {
                        metricCitiesTable.add(new ArrayList<>());
                        continue;
                    }
                    Map<Byte, Long> metricData = metricDataByAA.get(aaId);
                    if (metricData == null) {
                        metricCitiesTable.add(new ArrayList<>());
                        continue;
                    }
                    List<Long> values = cities.stream().map(f -> metricData.getOrDefault(f, 0L)).collect(Collectors.toList());
                    metricCitiesTable.add(values);
                }
                metricCitiesTableByAA.add(metricCitiesTable);
            }
            turnMetricCitiesTables.add(metricCitiesTableByAA);
        }
        return turnMetricCitiesTables;
    }
}
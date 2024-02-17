package link.locutus.discord.db;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
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
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;
import link.locutus.discord.db.entities.conflict.TurnTierGraphData;
import link.locutus.discord.util.PnwUtil;
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
    private final OffDefStatGroup offensiveStats = new OffDefStatGroup();
    private final OffDefStatGroup defensiveStats = new OffDefStatGroup();
    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Integer> allianceIdByNation = new Int2ObjectOpenHashMap<>();
    private final DamageStatGroup lossesStats = new DamageStatGroup();
    private final DamageStatGroup inflictedStats = new DamageStatGroup();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Long, TurnTierGraphData> graphDataByTurn = new Long2ObjectArrayMap<>();
    private final Map<Long, DayTierGraphData> graphDataByDay = new Long2ObjectArrayMap<>();
    private final Map<Long, Map<Integer, Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>>>> statsByDayByAllianceByCity = new Long2ObjectArrayMap<>();
    private final Map<Long, Map<Integer, Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>>>> damageByDayByAllianceByCity = new Long2ObjectArrayMap<>();

    public Set<Integer> getNationIds() {
        return statsByNation.keySet();
    }

    public void addGraphData(ConflictMetric metric, long turnOrDay, int city, int value) {
        if (metric.isDay()) {
            graphDataByDay.computeIfAbsent(turnOrDay, k -> new DayTierGraphData()).getOrCreate(metric).put((byte) city, value);
        } else {
            graphDataByTurn.computeIfAbsent(turnOrDay, k -> new TurnTierGraphData()).getOrCreate(metric).put((byte) city, value);
        }
    }

    public void updateTurnTierGraph(ConflictManager manager, long turn, Map<Byte, Map<MilitaryUnit, Integer>> data, boolean force, boolean save) {
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
        for (Map.Entry<Byte, Map<MilitaryUnit, Integer>> entry : data.entrySet()) {
            for (Map.Entry<MilitaryUnit, Integer> unitEntry : entry.getValue().entrySet()) {
                graphData.getOrCreate(ConflictMetric.BY_UNIT.get(unitEntry.getKey())).put(entry.getKey(), unitEntry.getValue());
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

        List<Integer> nationIds = new IntArrayList(statsByNation.keySet());
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

        Map<ConflictColumn, Function<OffDefStatGroup, Object>> offDefHeader = OffDefStatGroup.createHeader();
        List<List<Object>> offDefData = new ObjectArrayList<>();
        JteUtil.writeArray(offDefData, offDefHeader.values(), List.of(offensiveStats, defensiveStats));
        JteUtil.writeArray(offDefData, offDefHeader.values(), allianceIds, statsByAlliance);
        JteUtil.writeArray(offDefData, offDefHeader.values(), nationIds, statsByNation);
        root.put("counts", offDefData);

        Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
        List<List<Object>> damageData = new ObjectArrayList<>();
        System.out.println("lossesStats " + ((long) PnwUtil.convertedTotal(lossesStats.totalCost)) + " | " + ((long) PnwUtil.convertedTotal(inflictedStats.totalCost)));
        JteUtil.writeArray(damageData, damageHeader.values(), List.of(lossesStats, inflictedStats));
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

    public OffDefStatGroup getOffensiveStats() {
        return getOffensiveStats(null, false);
    }

    public OffDefStatGroup getOffensiveStats(Integer id, boolean isAlliance) {
        if (id == null) return offensiveStats;
        Map.Entry<OffDefStatGroup, OffDefStatGroup> pair = isAlliance ? statsByAlliance.get(id) : statsByNation.get(id);
        return pair == null ? null : pair.getKey();
    }

    public OffDefStatGroup getDefensiveStats() {
        return getDefensiveStats(null, false);
    }
    public OffDefStatGroup getDefensiveStats(Integer id, boolean isAlliance) {
        if (id == null) return defensiveStats;
        Map.Entry<OffDefStatGroup, OffDefStatGroup> pair = isAlliance ? statsByAlliance.get(id) : statsByNation.get(id);
        return pair == null ? null : pair.getValue();
    }

    public DamageStatGroup getLosses() {
        return getDamageStats(true, null, false);
    }

    public DamageStatGroup getInflicted() {
        return getDamageStats(false, null, false);
    }

    public DamageStatGroup getDamageStats(boolean isLosses, Integer id, boolean isAlliance) {
        if (id == null) return isLosses ? lossesStats : inflictedStats;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? damageByAlliance.get(id) : damageByNation.get(id);
        return pair == null ? null : isLosses ? pair.getKey() : pair.getValue();
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

    private void applyAttackerStats(int allianceId, int nationId, int attCities, int defCities, Consumer<OffDefStatGroup> onEach) {
        allianceIdByNation.putIfAbsent(nationId, allianceId);
        onEach.accept(statsByAlliance.computeIfAbsent(allianceId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getKey());
        onEach.accept(statsByNation.computeIfAbsent(nationId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getKey());
    }

    private void applyDefenderStats(int allianceId, int nationId, int attCities, int defCities, Consumer<OffDefStatGroup> onEach) {
        allianceIdByNation.putIfAbsent(nationId, allianceId);
        onEach.accept(statsByAlliance.computeIfAbsent(allianceId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getValue());
        onEach.accept(statsByNation.computeIfAbsent(nationId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getValue());
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        int attCities;
        int defCities;
        int attackerAA, attackerId;
        if (isAttacker) {
            attCities = current.getAttCities();
            defCities = current.getDefCities();
            attackerAA = current.getAttacker_aa();
            attackerId = current.getAttacker_id();
            if (previous == null) {
                offensiveStats.newWar(current, true);
                applyAttackerStats(attackerAA, attackerId, attCities, defCities, p -> p.newWar(current, true));
            } else {
                offensiveStats.updateWar(previous, current, true);
                applyAttackerStats(attackerAA, attackerId, attCities, defCities, p -> p.updateWar(previous, current, true));
            }
        } else {
            attackerAA = current.getDefender_aa();
            attackerId = current.getDefender_id();
            attCities = current.getDefCities();
            defCities = current.getAttCities();
            if (previous == null) {
                defensiveStats.newWar(current, false);
                applyDefenderStats(attackerAA, attackerId, attCities, defCities, p -> p.newWar(current, false));
            } else {
                defensiveStats.updateWar(previous, current, false);
                applyDefenderStats(attackerAA, attackerId, attCities, defCities, p -> p.updateWar(previous, current, false));
            }
        }
    }

    public void updateAttack(DBWar war, AbstractCursor attack, boolean isAttacker) {
        AttackTypeSubCategory subCategory = attack.getSubCategory(true);
        int attackerAA, attackerId, attCities, defCities;
        if (isAttacker) {
            attackerId = attack.getAttacker_id();
            if (attack.getAttacker_id() == war.getAttacker_id()) {
                attackerAA = war.getAttacker_aa();
                attCities = war.getAttCities();
                defCities = war.getDefCities();
            } else {
                attackerAA = war.getDefender_aa();
                attCities = war.getDefCities();
                defCities = war.getAttCities();
            }
        } else {
            attackerId = attack.getDefender_id();
            if (attack.getDefender_id() == war.getDefender_id()) {
                attackerAA = war.getDefender_aa();
                attCities = war.getDefCities();
                defCities = war.getAttCities();
            } else {
                attackerAA = war.getAttacker_aa();
                attCities = war.getAttCities();
                defCities = war.getDefCities();
            }
        }
        allianceIdByNation.putIfAbsent(attackerId, attackerAA);
        {
            if (isAttacker) {
                offensiveStats.newAttack(war, attack, subCategory);
                applyAttackerStats(attackerAA, attackerId, attCities, defCities, p -> p.newAttack(war, attack, subCategory));
            } else {
                defensiveStats.newAttack(war, attack, subCategory);
                applyDefenderStats(attackerAA, attackerId, attCities, defCities, p -> p.newAttack(war, attack, subCategory));
            }
        }

        Map.Entry<DamageStatGroup, DamageStatGroup> aaDamage = damageByAlliance.computeIfAbsent(attackerAA,
                k -> Map.entry(new DamageStatGroup(), new DamageStatGroup()));
        Map.Entry<DamageStatGroup, DamageStatGroup> nationDamage = damageByNation.computeIfAbsent(attackerId,
                k -> Map.entry(new DamageStatGroup(), new DamageStatGroup()));
        if (isAttacker) {
            lossesStats.apply(attack, true);
            inflictedStats.apply(attack, false);
            aaDamage.getKey().apply(attack, true);
            aaDamage.getValue().apply(attack, false);
            nationDamage.getKey().apply(attack, true);
            nationDamage.getValue().apply(attack, false);
            applyAttackerStats(attackerAA, attackerId, attCities, defCities, p -> p.newAttack(war, attack, subCategory));
        } else {
            lossesStats.apply(attack, false);
            inflictedStats.apply(attack, true);
            aaDamage.getKey().apply(attack, false);
            aaDamage.getValue().apply(attack, true);
            nationDamage.getKey().apply(attack, false);
            nationDamage.getValue().apply(attack, true);
            applyDefenderStats(attackerAA, attackerId, attCities, defCities, p -> p.newAttack(war, attack, subCategory));
        }
    }

    public Map<String, Object> toGraphMap(ConflictManager manager) {
        List<ConflictMetric.Entry> entries = getGraphEntries();
        Map<Long, Map<ConflictMetric, Map<Byte, Integer>>> turnData = new Long2ObjectArrayMap<>();
        Map<Long, Map<ConflictMetric, Map<Byte, Integer>>> dayData = new Long2ObjectArrayMap<>();

        Set<Byte> cities = new ByteOpenHashSet();

        for (ConflictMetric.Entry entry : entries) {
            Map<Long, Map<ConflictMetric, Map<Byte, Integer>>> map = entry.metric().isDay() ? dayData : turnData;
            map.computeIfAbsent(entry.turnOrDay(), k -> new EnumMap<>(ConflictMetric.class))
                    .computeIfAbsent(entry.metric(), k -> new Byte2IntOpenHashMap())
                    .put((byte) entry.city(), entry.value());
            cities.add((byte) entry.city());
        }

        System.out.println("Turn data " + turnData);
        System.out.println("Day data " + dayData);

        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        root.put("name", getName());

        List<Long> turnsSorted = new LongArrayList(turnData.keySet());
        turnsSorted.sort(Long::compareTo);
        root.put("turns", turnsSorted);

        List<Long> daysSorted = new LongArrayList(dayData.keySet());
        daysSorted.sort(Long::compareTo);
        root.put("days", daysSorted);

        List<Byte> citiesSorted = new ByteArrayList(cities);
        citiesSorted.sort(Byte::compareTo);
        root.put("cities", citiesSorted);

        root.put("turn_data", toGraphMap(turnData, turnsSorted, citiesSorted,
                Arrays.stream(ConflictMetric.values).filter(f -> !f.isDay()).toList()));
        root.put("day_data", toGraphMap(dayData, daysSorted, citiesSorted,
                Arrays.stream(ConflictMetric.values).filter(ConflictMetric::isDay).toList()));
        return root;
    }

    private List<List<List<Integer>>> toGraphMap(Map<Long, Map<ConflictMetric, Map<Byte, Integer>>> data,
                                          List<Long> turnsOrDays,
                                          List<Byte> cities,
                                          Collection<ConflictMetric> metrics) {
        List<List<List<Integer>>> turnMetricCitiesTables = new ObjectArrayList<>();
        for (ConflictMetric metric : metrics) {
            List<List<Integer>> metricCitiesTable = new ObjectArrayList<>();
            for (long turnOrDay : turnsOrDays) {
                Map<ConflictMetric, Map<Byte, Integer>> dataAtTime = data.get(turnOrDay);
                Map<Byte, Integer> metricData = dataAtTime.get(metric);
                if (metricData == null) {
                    System.out.println("No data for " + metric + " at " + turnOrDay);
                    metricCitiesTable.add(new ArrayList<>());
                    continue;
                }
                List<Integer> values = cities.stream().map(f -> metricData.getOrDefault(f, 0)).collect(Collectors.toList());
                metricCitiesTable.add(values);
            }
            turnMetricCitiesTables.add(metricCitiesTable);
        }
        return turnMetricCitiesTables;
    }
}
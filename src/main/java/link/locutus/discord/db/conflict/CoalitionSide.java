package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.CachedSupplier;
import link.locutus.discord.util.scheduler.KeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CoalitionSide {
    private final Conflict parent;
    private String name;
    private final boolean isPrimary;
    private CoalitionSide otherSide;
    private final Set<Integer> coalition = new IntOpenHashSet();

    private volatile WarStatistics tmp;
    private final CachedSupplier<WarStatistics> warStatistics;

    public CoalitionSide(Conflict parent, String name, boolean isPrimary) {
        this.parent = parent;
        this.name = name;
        this.isPrimary = isPrimary;
        this.warStatistics = CachedSupplier.of(() -> {
            if (tmp != null) return tmp;
            WarStatistics stats = tmp = new WarStatistics(this);
            if (parent.getId() > 0) {
                ConflictManager.get().ensureLoadedFully(Set.of(parent));
            }
            tmp = null;
            return stats;
        });
        if (parent.getId() <= 0) {
            setLoaded();
        }
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public int getId() {
        return parent.getId();
    }

    public void setLoaded() {
        WarStatistics eager = (tmp != null) ? tmp : (tmp = new WarStatistics(this));
        this.warStatistics.setValue(eager);
    }

    public boolean isWarsLoaded() {
        return warStatistics.hasValue();
    }

    public void clearWarData() {
        tmp = null;
        if (warStatistics.unload()) {
                System.err.println("[conflict] Unloading(1) confict: " + parent.getName() + "/" + parent.getId() + "\n" +
                        StringMan.stacktraceToString(new Exception().getStackTrace()));
        }
    }

    public WarStatistics get() {
        return warStatistics.get();
    }

    public List<Integer> getAllianceIdsSorted() {
        List<Integer> allianceIds = new ArrayList<>(coalition);
        Collections.sort(allianceIds);
        return allianceIds;
    }

    public Map<String, Object> toMetaMap(ConflictManager manager) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        List<Integer> allianceIds = new ArrayList<>(coalition);

        Collections.sort(allianceIds);
        root.put("name", getName());
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

    public boolean add(int allianceId) {
        if (allianceId == 0) throw new IllegalArgumentException("Alliance ID cannot be 0. " + this.getName());
        return coalition.add(allianceId);
    }

    public boolean remove(int allianceId) {
        return coalition.remove(allianceId);
    }

    public Set<Integer> getAllianceIds() {
        return coalition;
    }

    public String getName() {
        return name;
    }

    public DamageStatGroup getOffensiveStats(Integer id, boolean isAlliance) {
        WarStatistics stats = get();
        if (id == null) return stats.inflictedAndOffensiveStats;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? stats.damageByAlliance.get(id) : stats.damageByNation.get(id);
        return pair == null ? null : pair.getValue();
    }
    public DamageStatGroup getDefensiveStats(Integer id, boolean isAlliance) {
        WarStatistics stats = get();
        if (id == null) return stats.lossesAndDefensiveStats;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = isAlliance ? stats.damageByAlliance.get(id) : stats.damageByNation.get(id);
        return pair == null ? null : pair.getKey();
    }

    public DamageStatGroup getLosses() {
        return getDamageStats(true, null, false);
    }

    public DamageStatGroup getInflicted() {
        return getDamageStats(false, null, false);
    }

    public DamageStatGroup getDamageStats(boolean isLosses, Integer id, boolean isAlliance) {
        WarStatistics stats = get();
        if (id == null) return isLosses ? stats.lossesAndDefensiveStats : stats.inflictedAndOffensiveStats;
        Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> map = isAlliance ? stats.damageByAlliance : stats.damageByNation;
        Map.Entry<DamageStatGroup, DamageStatGroup> pair = map.computeIfAbsent(id, k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
        return isLosses ? pair.getKey() : pair.getValue();
    }

    private Map.Entry<DamageStatGroup, DamageStatGroup> getAllianceDamageStatsByDayPair(int id, int cities, long day) {
        return get().damageByDayByAllianceByCity.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(id, k -> new Byte2ObjectOpenHashMap<>())
                .computeIfAbsent((byte) cities, k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
    }

    private DamageStatGroup getAllianceDamageStatsByDay(boolean isLosses, int id, int cities, long day) {
        Map.Entry<DamageStatGroup, DamageStatGroup> entry = getAllianceDamageStatsByDayPair(id, cities, day);
        return isLosses ? entry.getKey() : entry.getValue();
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
        WarStatistics stats = get();
        Integer existing = stats.allianceIdByNation.get(nationId);
        if (existing == null || existing > allianceId) {
            stats.allianceIdByNation.put(nationId, allianceId);
        }
        onEach.accept(getDamageStats(false, allianceId, true));
        onEach.accept(getDamageStats(false, nationId, false));
        onEach.accept(getAllianceDamageStatsByDay(false, allianceId, cities, day));
    }

    private void applyDefenderStats(int allianceId, int nationId, int cities, long day, Consumer<DamageStatGroup> onEach) {
        WarStatistics stats = get();
        Integer existing = stats.allianceIdByNation.get(nationId);
        if (existing == null || existing > allianceId) {
            stats.allianceIdByNation.put(nationId, allianceId);
        }
        onEach.accept(getDamageStats(true, allianceId, true));
        onEach.accept(getDamageStats(true, nationId, false));
        onEach.accept(getAllianceDamageStatsByDay(true, allianceId, cities, day));
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        WarStatistics wars = get();
        int cities;
        long day = TimeUtil.getDay(current.getDate());
        int attackerAA, attackerId;
        if (isAttacker) {
            cities = current.getAttCities();
            attackerAA = current.getAttacker_aa();
            attackerId = current.getAttacker_id();
            if (previous == null) {
                wars.inflictedAndOffensiveStats.newWar(current, true);
                applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.newWar(current, true));
            } else {
                wars.inflictedAndOffensiveStats.updateWar(previous, current, true);
                applyAttackerStats(attackerAA, attackerId, cities, day, p -> p.updateWar(previous, current, true));
            }
        } else {
            attackerAA = current.getDefender_aa();
            attackerId = current.getDefender_id();
            cities = current.getDefCities();
            if (previous == null) {
                wars.lossesAndDefensiveStats.newWar(current, false);
                applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.newWar(current, false));
            } else {
                wars.lossesAndDefensiveStats.updateWar(previous, current, false);
                applyDefenderStats(attackerAA, attackerId, cities, day, p -> p.updateWar(previous, current, false));
            }
        }
    }

    public void updateAttack(DBWar war, AbstractCursor attack, int attackerAA, int nationId, int cities, long day, boolean isAttacker, AttackTypeSubCategory subCategory) {
        WarStatistics wars = get();
        Map.Entry<DamageStatGroup, DamageStatGroup> aaDamage = wars.damageByAlliance.computeIfAbsent(attackerAA,
                k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));
        Map.Entry<DamageStatGroup, DamageStatGroup> nationDamage = wars.damageByNation.computeIfAbsent(nationId,
                k -> KeyValue.of(new DamageStatGroup(), new DamageStatGroup()));

        Map.Entry<DamageStatGroup, DamageStatGroup> tierDamage = getAllianceDamageStatsByDayPair(attackerAA, cities, day);

        if (isAttacker) {
            wars.inflictedAndOffensiveStats.newAttack(war, attack, subCategory);

            wars.lossesAndDefensiveStats.apply(attack, war, true);
            wars.inflictedAndOffensiveStats.apply(attack, war, false);

            aaDamage.getKey().apply(attack, war, true);
            aaDamage.getValue().apply(attack, war, false);

            nationDamage.getKey().apply(attack, war, true);
            nationDamage.getValue().apply(attack, war, false);

            tierDamage.getKey().apply(attack, war, true);
            tierDamage.getValue().apply(attack, war, false);

            applyAttackerStats(attackerAA, nationId, cities, day, p -> p.newAttack(war, attack, subCategory));
        } else {
            wars.lossesAndDefensiveStats.newAttack(war, attack, subCategory);

            wars.lossesAndDefensiveStats.apply(attack, war, false);
            wars.inflictedAndOffensiveStats.apply(attack, war, true);

            aaDamage.getKey().apply(attack, war, false);
            aaDamage.getValue().apply(attack, war, true);

            nationDamage.getKey().apply(attack, war, false);
            nationDamage.getValue().apply(attack, war, true);

            tierDamage.getKey().apply(attack, war, false);
            tierDamage.getValue().apply(attack, war, true);

            applyDefenderStats(attackerAA, nationId, cities, day, p -> p.newAttack(war, attack, subCategory));
        }
    }

    public WarStatistics getWarDataOrNull() {
        return warStatistics.getOrNull();
    }
}
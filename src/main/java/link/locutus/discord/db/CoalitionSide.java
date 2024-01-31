package link.locutus.discord.db;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;
import link.locutus.discord.web.jooby.JteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class CoalitionSide {
    private final Conflict parent;
    private final String name;
    private CoalitionSide otherSide;

    private final Set<Integer> coalition = new IntOpenHashSet();

    private final OffDefStatGroup offensiveStats = new OffDefStatGroup();
    private final OffDefStatGroup defensiveStats = new OffDefStatGroup();
    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<OffDefStatGroup, OffDefStatGroup>> statsByNation = new Int2ObjectOpenHashMap<>();

    private final DamageStatGroup lossesStats = new DamageStatGroup();
    private final DamageStatGroup inflictedStats = new DamageStatGroup();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map.Entry<DamageStatGroup, DamageStatGroup>> damageByNation = new Int2ObjectOpenHashMap<>();

    public JsonObject toJson(ConflictManager manager) {
        JsonObject root = new JsonObject();
        root.addProperty("name", getName());
        JsonArray allianceIds = new JsonArray();
        JsonArray allianceNames = new JsonArray();
        List<Integer> aaIds = new ArrayList<>(coalition);
        for (int id : aaIds) {
            String aaName = manager.getAllianceNameOrNull(id);
            if (aaName == null) aaName = "";
            allianceIds.add(id);
            allianceNames.add(aaName);
        }
        root.add("alliance_ids", allianceIds);
        root.add("alliance_names", allianceNames);

        JsonArray nationIds = new JsonArray();
        JsonArray nationNames = new JsonArray();
        List<Integer> nationIdsList = new ArrayList<>(statsByNation.keySet());
        for (int id : nationIdsList) {
            DBNation nation = DBNation.getById(id);
            String name = nation == null ? "" : nation.getName();
            nationIds.add(id);
            nationNames.add(name);
        }
        root.add("nation_ids", nationIds);
        root.add("nation_names", nationNames);

        Map<String, Function<OffDefStatGroup, Object>> offDefHeader = OffDefStatGroup.createHeader();
        JsonArray offDefData = new JsonArray();
        JteUtil.writeArray(offDefData, offDefHeader.values(), List.of(offensiveStats, defensiveStats));
        JteUtil.writeArray(offDefData, offDefHeader.values(), aaIds, statsByAlliance);
        JteUtil.writeArray(offDefData, offDefHeader.values(), nationIdsList, statsByNation);
        root.add("counts", offDefData);

        Map<String, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
        JsonArray damageData = new JsonArray();
        JteUtil.writeArray(damageData, damageHeader.values(), List.of(lossesStats, inflictedStats));
        JteUtil.writeArray(damageData, damageHeader.values(), aaIds, damageByAlliance);
        JteUtil.writeArray(damageData, damageHeader.values(), nationIdsList, damageByNation);
        root.add("damage", damageData);
        return root;
    }

    public void add(int allianceId) {
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

    public CoalitionSide(Conflict parent, String name) {
        this.parent = parent;
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

    private void applyAttackerStats(int allianceId, int nationId, Consumer<OffDefStatGroup> onEach) {
        onEach.accept(statsByAlliance.computeIfAbsent(allianceId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getKey());
        onEach.accept(statsByNation.computeIfAbsent(nationId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getKey());
    }

    private void applyDefenderStats(int allianceId, int nationId, Consumer<OffDefStatGroup> onEach) {
        onEach.accept(statsByAlliance.computeIfAbsent(allianceId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getValue());
        onEach.accept(statsByNation.computeIfAbsent(nationId,
                k -> Map.entry(new OffDefStatGroup(), new OffDefStatGroup())).getValue());
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        int attackerAA, attackerId;
        if (isAttacker) {
            attackerAA = current.getAttacker_aa();
            attackerId = current.getAttacker_id();
            if (previous == null) {
                offensiveStats.newWar(current, true);
                applyAttackerStats(attackerAA, attackerId, p -> p.newWar(current, true));
            } else {
                offensiveStats.updateWar(previous, current, true);
                applyAttackerStats(attackerAA, attackerId, p -> p.updateWar(previous, current, true));
            }
        } else {
            attackerAA = current.getDefender_aa();
            attackerId = current.getDefender_id();
            if (previous == null) {
                defensiveStats.newWar(current, false);
                applyDefenderStats(attackerAA, attackerId, p -> p.newWar(current, false));
            } else {
                defensiveStats.updateWar(previous, current, false);
                applyDefenderStats(attackerAA, attackerId, p -> p.updateWar(previous, current, false));
            }
        }
    }

    // getLosses(double[] buffer, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings);
    public void updateAttack(DBWar war, AbstractCursor attack, boolean isAttacker) {
        AttackTypeSubCategory subCategory = attack.getSubCategory();
        int attackerAA, attackerId;
        if (attack.getAttacker_id() == war.getAttacker_id()) {
            attackerAA = war.getAttacker_aa();
            attackerId = war.getAttacker_id();
        } else {
            attackerAA = war.getDefender_aa();
            attackerId = war.getDefender_id();
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
            applyAttackerStats(attackerAA, attackerId, p -> p.newAttack(war, attack, subCategory));
        } else {
            lossesStats.apply(attack, false);
            inflictedStats.apply(attack, true);
            aaDamage.getKey().apply(attack, false);
            aaDamage.getValue().apply(attack, true);
            nationDamage.getKey().apply(attack, false);
            nationDamage.getValue().apply(attack, true);
            applyDefenderStats(attackerAA, attackerId, p -> p.newAttack(war, attack, subCategory));
        }
    }
}
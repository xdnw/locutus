package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CoalitionSide {
    private final Conflict parent;
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


    private static class DamageStatGroup {
        public final double[] totalCost = ResourceType.getBuffer();
        public final double[] unitCost = ResourceType.getBuffer();
        public final double[] consumption = ResourceType.getBuffer();
        public final double[] loot = ResourceType.getBuffer();
        public final int[] units = new int[MilitaryUnit.values.length];
        public double infra = 0;

        public void apply(AbstractCursor attack, boolean isAttacker) {
            attack.getLosses(totalCost, isAttacker, true, true, true, true, true);
            attack.getLosses(unitCost, isAttacker, true, false, false, false, false);
            attack.getLosses(consumption, isAttacker, false, false, true, false, false);
            attack.getLosses(loot, isAttacker, false, false, false, true, false);
            attack.getUnitLosses(units, isAttacker);
            if (!isAttacker) {
                infra += attack.getInfra_destroyed_value();
            }
        }
    }

    private static class OffDefStatGroup {
        public int totalWars;
        public int activeWars;
        public int attacks = 0;
        public int warsWon;
        public int warsLost;
        public int warsExpired;
        public int warsPeaced;
        public final Map<AttackType, Integer> attackTypes = new EnumMap<>(AttackType.class);
        public final Map<AttackTypeSubCategory, Integer> attackSubTypes = new EnumMap<>(AttackTypeSubCategory.class);
        public final Map<SuccessType, Integer> successTypes = new EnumMap<>(SuccessType.class);
        public final Map<WarType, Integer> warTypes = new EnumMap<>(WarType.class);

        public void newWar(DBWar war, boolean isAttacker) {
            totalWars++;
            if (war.isActive()) activeWars++;
            else {
                addWarStatus(war.getStatus(), isAttacker);
            }
            warTypes.merge(war.getWarType(), 1, Integer::sum);
        }

        private void addWarStatus(WarStatus status, boolean isAttacker) {
            switch (status) {
                case DEFENDER_VICTORY -> {
                    if (isAttacker) warsLost++;
                    else warsWon++;
                }
                case ATTACKER_VICTORY -> {
                    if (isAttacker) warsWon++;
                    else warsLost++;
                }
                case PEACE -> {
                    warsPeaced++;
                }
                case EXPIRED -> {
                    warsExpired++;
                }
            }
        }

        public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
            addWarStatus(current.getStatus(), isAttacker);
            if (previous.isActive() && !current.isActive()) {
                activeWars--;
            }
        }

        public void newAttack(DBWar war, AbstractCursor attack, AttackTypeSubCategory subCategory) {
            attacks++;
            attackTypes.merge(attack.getAttack_type(), 1, Integer::sum);
            if (subCategory != null) {
                attackSubTypes.merge(subCategory, 1, Integer::sum);
            }
            successTypes.merge(attack.getSuccess(), 1, Integer::sum);
        }
    }

    public CoalitionSide(Conflict parent) {
        this.parent = parent;
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
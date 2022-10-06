package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import net.dv8tion.jda.api.entities.Guild;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WarParser {
    private final String nameA;
    private final String nameB;
    private final Function<DBWar, Boolean> isPrimary, isSecondary;
    private final long start;
    private final long end;
    private final Collection<Integer> coal1Alliances;
    private final Collection<Integer> coal1Nations;
    private final Collection<Integer> coal2Alliances;
    private final Collection<Integer> coal2Nations;

    private Map<Integer, DBWar> wars;
    private List<DBAttack> attacks;

    public static WarParser ofAANatobj(Collection<DBAlliance> coal1Alliances, Collection<DBNation> coal1Nations, Collection<DBAlliance> coal2Alliances, Collection<DBNation> coal2Nations, long start, long end) {
        return ofNatObj(coal1Alliances == null ? null : coal1Alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet()), coal1Nations, coal2Alliances == null ? null : coal2Alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet()), coal2Nations, start, end);
    }

    public static WarParser ofNatObj(Collection<Integer> coal1Alliances, Collection<DBNation> coal1Nations, Collection<Integer> coal2Alliances, Collection<DBNation> coal2Nations, long start, long end) {
        return of(coal1Alliances, coal1Nations == null ? null : coal1Nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()), coal2Alliances, coal2Nations == null ? null : coal2Nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()), start, end);
    }

    public static WarParser of(Collection<NationOrAlliance> coal1, Collection<NationOrAlliance> coal2, long start, long end) {
        Collection<Integer> coal1Alliances = coal1.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal1Nations = coal1.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal2Alliances = coal2.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal2Nations = coal2.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
        return of(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations,  start, end);
    }

    public static WarParser of(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end) {
        return new WarParser(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end);
    }

    private WarParser(Collection<DBWar> wars, Function<DBWar, Boolean> isPrimary) {
        coal1Alliances = Collections.emptySet();
        coal2Alliances = Collections.emptySet();
        coal1Nations = Collections.emptySet();
        coal2Nations = Collections.emptySet();
        this.start = 0;
        this.end = Long.MAX_VALUE;
        nameA = "A";
        nameB = "B";
        this.isPrimary = isPrimary;
        isSecondary = f -> !isPrimary.apply(f);
        this.wars = new HashMap<>(wars.size());
        for (DBWar war : wars) {
//            if (isPrimary.apply(war)) coal1Nations.add(war.attacker_id);
//            else coal2Nations.add(war.attacker_id);
            this.wars.put(war.warId, war);
        }
    }

    public static WarParser of(Collection<DBWar> wars, Function<DBWar, Boolean> isPrimary) {
        return new WarParser(wars, isPrimary);
    }

    private WarParser(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end) {
        if (coal1Alliances == null) coal1Alliances = new HashSet<>();
        if (coal1Nations == null) coal1Nations = new HashSet<>();
        if (coal2Alliances == null) coal2Alliances = new HashSet<>();
        if (coal2Nations == null) coal2Nations = new HashSet<>();
        this.coal1Alliances = coal1Alliances;
        this.coal1Nations = coal1Nations;
        this.coal2Alliances = coal2Alliances;
        this.coal2Nations = coal2Nations;
        List<String> coal1Names = new ArrayList<>();
        List<String> coal2Names = new ArrayList<>();
        for (Integer id : coal1Alliances) coal1Names.add("AA:" + PnwUtil.getName(id, true));
        for (Integer id : coal1Nations) coal1Names.add(PnwUtil.getName(id, false));
        for (Integer id : coal2Alliances) coal2Names.add("AA:" + PnwUtil.getName(id, true));
        for (Integer id : coal2Nations) coal2Names.add(PnwUtil.getName(id, false));
        this.nameA = coal1Names.isEmpty() ? "*" : StringMan.join(coal1Names, ",");
        this.nameB = coal1Names.isEmpty() ? "*" : StringMan.join(coal2Names, ",");

        this.isPrimary = w -> this.coal1Alliances.contains(w.attacker_aa) || this.coal1Nations.contains(w.attacker_id);
        this.isSecondary = w -> this.coal2Alliances.contains(w.attacker_aa) || this.coal2Nations.contains(w.attacker_id);
        this.start = start;
        this.end = end;
    }

    public static WarParser of(Guild guild, String attackers, String defenders, long start) {
        return of(guild, attackers, defenders, start, Long.MAX_VALUE);
    }

    public static WarParser of(Guild guild, String attackers, String defenders, long start, long end) {
        Set<Integer> coal1Alliances = DiscordUtil.parseAlliances(guild, attackers);
        Collection<DBNation> coal1Nations = coal1Alliances != null && !coal1Alliances.isEmpty() ? null : DiscordUtil.parseNations(guild, attackers);
        Set<Integer> coal2Alliances = DiscordUtil.parseAlliances(guild, defenders);
        Collection<DBNation> coal2Nations = coal2Alliances != null && !coal2Alliances.isEmpty() ? null : DiscordUtil.parseNations(guild, defenders);

        return ofNatObj(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end);
    }

    public WarParser allowedWarTypes(Set<WarType> allowedWarTypes) {
        if (allowedWarTypes != null) getWars().entrySet().removeIf(f -> !allowedWarTypes.contains(f.getValue()));
        return this;
    }

    public WarParser allowWarStatuses(Set<WarStatus> statuses) {
        if (statuses != null) getWars().entrySet().removeIf(f -> !statuses.contains(f.getValue().status));
        return this;
    }

    public WarParser allowedAttackTypes(Set<AttackType> attackTypes) {
        if (attackTypes != null) getAttacks().removeIf(f -> !attackTypes.contains(f));
        return this;
    }

    public Map<Integer, DBWar> getWars() {
        if (this.wars == null) {
            this.wars = Locutus.imp().getWarDb().getWars(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end);
        }
        return wars;
    }

    public List<DBAttack> getAttacks() {
        if (this.attacks == null) {
//            this.attacks = Locutus.imp().getWarDb().getAttacks(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end, true);
            this.attacks = Locutus.imp().getWarDb().getAttacksByWars(getWars().values(), start, end);
        }
        return this.attacks;
    }

    public Function<DBWar, Boolean> getIsPrimary() {
        return isPrimary;
    }

    public Function<DBWar, Boolean> getIsSecondary() {
        return isSecondary;
    }

    public Function<DBAttack, Boolean> getAttackPrimary() {
        return attack -> {
            DBWar war = getWars().get(attack.war_id);
            if (war == null) return false;
            boolean isWarPrimary = isPrimary.apply(war);
            return (isWarPrimary ? war.attacker_id : war.defender_id) == attack.attacker_nation_id;
        };
    }

    public Function<DBAttack, Boolean> getAttackSecondary() {
        return attack -> {
            DBWar war = getWars().get(attack.war_id);
            if (war == null) return false;
            boolean isWarSecondary = isSecondary.apply(war);
            return (isWarSecondary ? war.attacker_id : war.defender_id) == attack.defender_nation_id;
        };
    }

    public AttackTypeBreakdown toBreakdown() {
        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(nameA, nameB);
        breakdown.addAttacks(getAttacks(), getAttackPrimary(), getAttackSecondary());
        return breakdown;
    }

    public AttackCost toWarCost() {
        AttackCost cost = new AttackCost(nameA, nameB);
        cost.addCost(getAttacks(), getAttackPrimary(), getAttackSecondary());
        return cost;
    }

    public <T> Map<T, AttackTypeBreakdown> groupBreakdownByAttack(Function<DBAttack, T> groupFunc) {
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        Map<T, AttackTypeBreakdown> grouped = new LinkedHashMap<>();
        for (DBAttack attack : getAttacks()) {
            T key = groupFunc.apply(attack);
            AttackTypeBreakdown obj = grouped.computeIfAbsent(key, f -> new AttackTypeBreakdown(nameA, nameB));
            obj.addAttack(attack, attPrimary, attSecondary);
        }
        return grouped;
    }

    public <T> Map<T, AttackCost> groupWarCostByAttack(Function<DBAttack, T> groupFunc) {
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        Map<T, AttackCost> grouped = new LinkedHashMap<>();
        for (DBAttack attack : getAttacks()) {
            T key = groupFunc.apply(attack);
            AttackCost cost = grouped.computeIfAbsent(key, f -> new AttackCost(nameA, nameB));
            cost.addCost(attack, attPrimary, attSecondary);
        }
        return grouped;
    }

    public Map<Long, AttackCost> toWarCostByDay() {
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();
        for (DBAttack attack : getAttacks()) {
            if (attack.epoch > System.currentTimeMillis()) {
                System.out.println(attack.war_attack_id + " is in future");
            }
            long turn = TimeUtil.getTurn(attack.epoch);
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(nameA, nameB));
            cost.addCost(attack, attPrimary, attSecondary);
        }
        return warCostByDay;
    }

    public Map<Integer, AttackCost> toWarCostByNation() {
        Map<Integer, AttackCost> warCostByNation = new HashMap<>();
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        for (DBAttack attack : getAttacks()) {
            if (!attPrimary.apply(attack) && !attSecondary.apply(attack)) continue;
            {
                String other = attPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.attacker_nation_id, f -> new AttackCost(PnwUtil.getName(attack.attacker_nation_id, false), other));
                cost.addCost(attack, true);
            }
            {
                String other = attSecondary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.defender_nation_id, f -> new AttackCost(PnwUtil.getName(attack.defender_nation_id, false), other));
                cost.addCost(attack, false);
            }
        }
        return warCostByNation;
    }

    public Map<Integer, AttackCost> toWarCostByAlliance() {
        Map<Integer, AttackCost> warCostByAA = new HashMap<>();
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        for (DBAttack attack : getAttacks()) {
            DBWar war = getWars().get(attack.war_id);
            {
                String other = attPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByAA.computeIfAbsent(war.attacker_aa, f -> new AttackCost(PnwUtil.getName(war.attacker_aa, true), other));
                cost.addCost(attack, true);
            }

            {
                String other = attSecondary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByAA.computeIfAbsent(war.defender_aa, f -> new AttackCost(PnwUtil.getName(war.defender_aa, true), other));
                cost.addCost(attack, false);
            }
        }
        return warCostByAA;
    }

    public Map<Integer, AttackTypeBreakdown> toAttackTypeByNation() {
        Map<Integer, AttackTypeBreakdown> warCostByNation = new HashMap<>();
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        for (DBAttack attack : getAttacks()) {
            if (!attPrimary.apply(attack) && !attSecondary.apply(attack)) continue;
            {
                String other = attPrimary.apply(attack) ? nameB : nameA;
                AttackTypeBreakdown cost = warCostByNation.computeIfAbsent(attack.attacker_nation_id, f -> new AttackTypeBreakdown(PnwUtil.getName(attack.attacker_nation_id, false), other));
                cost.addAttack(attack, true);
            }
            {
                String other = attSecondary.apply(attack) ? nameA : nameB;
                AttackTypeBreakdown cost = warCostByNation.computeIfAbsent(attack.defender_nation_id, f -> new AttackTypeBreakdown(PnwUtil.getName(attack.defender_nation_id, false), other));
                cost.addAttack(attack, false);
            }
        }
        return warCostByNation;
    }

    public Map<Integer, AttackTypeBreakdown> toAttackTypeByAlliance() {
        Map<Integer, AttackTypeBreakdown> warCostByAA = new HashMap<>();
        Function<DBAttack, Boolean> attPrimary = getAttackPrimary();
        Function<DBAttack, Boolean> attSecondary = getAttackSecondary();
        for (DBAttack attack : getAttacks()) {
            DBWar war = getWars().get(attack.war_id);
            {
                String other = attPrimary.apply(attack) ? nameB : nameA;
                AttackTypeBreakdown cost = warCostByAA.computeIfAbsent(war.attacker_aa, f -> new AttackTypeBreakdown(PnwUtil.getName(war.attacker_aa, true), other));
                cost.addAttack(attack, true);
            }

            {
                String other = attSecondary.apply(attack) ? nameA : nameB;
                AttackTypeBreakdown cost = warCostByAA.computeIfAbsent(war.defender_aa, f -> new AttackTypeBreakdown(PnwUtil.getName(war.defender_aa, true), other));
                cost.addAttack(attack, false);
            }
        }
        return warCostByAA;
    }

    public String getNameA() {
        return nameA;
    }
    public String getNameB() {
        return nameB;
    }

    public DBNation getNation(int nationId, DBWar war) {
        return DBNation.byId(nationId);
    }
}

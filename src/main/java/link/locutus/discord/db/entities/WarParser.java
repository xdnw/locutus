package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
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
    private Consumer<BiConsumer<DBWar, AbstractCursor>> attacks;
    private BiPredicate<DBWar, AbstractCursor> filter;

    public static WarParser ofAANatobj(Collection<DBAlliance> coal1Alliances, Collection<DBNation> coal1Nations, Collection<DBAlliance> coal2Alliances, Collection<DBNation> coal2Nations, long start, long end) {
        return ofNatObj(coal1Alliances == null ? null : coal1Alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet()), coal1Nations, coal2Alliances == null ? null : coal2Alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet()), coal2Nations, start, end);
    }

    public static WarParser ofNatObj(Collection<Integer> coal1Alliances, Collection<DBNation> coal1Nations, Collection<Integer> coal2Alliances, Collection<DBNation> coal2Nations, long start, long end) {
        return of(coal1Alliances, coal1Nations == null ? null : coal1Nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()), coal2Alliances, coal2Nations == null ? null : coal2Nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet()), start, end);
    }

    public static WarParser of(Collection<NationOrAlliance> coal1, Collection<NationOrAlliance> coal2, long start, long end) {
        Collection<Integer> coal1Alliances = coal1 == null ? null : coal1.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal1Nations = coal1 == null ? null : coal1.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal2Alliances = coal2 == null ? null : coal2.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
        Collection<Integer> coal2Nations = coal2 == null ? null : coal2.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
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
        if (coal1Alliances == null && coal1Nations == null && coal2Alliances == null && coal2Nations == null) {
            throw new IllegalArgumentException("At least one coalition must be non-null");
        }
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
        for (Integer id : coal1Alliances) coal1Names.add("AA:" + PW.getName(id, true));
        for (Integer id : coal1Nations) coal1Names.add(PW.getName(id, false));
        for (Integer id : coal2Alliances) coal2Names.add("AA:" + PW.getName(id, true));
        for (Integer id : coal2Nations) coal2Names.add(PW.getName(id, false));
        this.nameA = coal1Names.isEmpty() ? "*" : coal1Names.size() > 10 ? "col1" : StringMan.join(coal1Names, ",");
        this.nameB = coal2Names.isEmpty() ? "*" : coal2Names.size() > 10 ? "col1" : StringMan.join(coal2Names, ",");

        Predicate<DBWar> isCol1Attacker;
        Predicate<DBWar> isCol2Attacker;
        Predicate<DBWar> isCol1Defender;
        Predicate<DBWar> isCol2Defender;
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty()) {
            isCol1Attacker = f -> !this.coal2Alliances.contains(f.getAttacker_aa()) && !this.coal2Nations.contains(f.getAttacker_id());
            isCol1Defender = f -> !this.coal2Alliances.contains(f.getDefender_aa()) && !this.coal2Nations.contains(f.getDefender_id());
        } else {
            isCol1Attacker = f -> this.coal1Alliances.contains(f.getAttacker_aa()) || this.coal1Nations.contains(f.getAttacker_id());
            isCol1Defender = f -> this.coal1Alliances.contains(f.getDefender_aa()) || this.coal1Nations.contains(f.getDefender_id());
        }
        if (coal2Alliances.isEmpty() && coal2Nations.isEmpty()) {
            isCol2Attacker = f -> !this.coal1Alliances.contains(f.getAttacker_aa()) && !this.coal1Nations.contains(f.getAttacker_id());
            isCol2Defender = f -> !this.coal1Alliances.contains(f.getDefender_aa()) && !this.coal1Nations.contains(f.getDefender_id());
        } else {
            isCol2Attacker = f -> this.coal2Alliances.contains(f.getAttacker_aa()) || this.coal2Nations.contains(f.getAttacker_id());
            isCol2Defender = f -> this.coal2Alliances.contains(f.getDefender_aa()) || this.coal2Nations.contains(f.getDefender_id());
        }
        isPrimary = f -> isCol1Attacker.test(f) && isCol2Defender.test(f);
        isSecondary = f -> isCol2Attacker.test(f) && isCol1Defender.test(f);
        this.start = start;
        this.end = end;
    }

    public static WarParser of(Guild guild, User author, DBNation me, String attackers, String defenders, long start) {
        return of(guild, author, me, attackers, defenders, start, Long.MAX_VALUE);
    }

    public static WarParser of(Guild guild, User author, DBNation me, String attackers, String defenders, long start, long end) {
        Set<Integer> coal1Alliances = DiscordUtil.parseAllianceIds(guild, attackers);
        Collection<DBNation> coal1Nations = coal1Alliances != null && !coal1Alliances.isEmpty() ? null : DiscordUtil.parseNations(guild, author, me, attackers, false, true);
        Set<Integer> coal2Alliances = DiscordUtil.parseAllianceIds(guild, defenders);
        Collection<DBNation> coal2Nations = coal2Alliances != null && !coal2Alliances.isEmpty() ? null : DiscordUtil.parseNations(guild, author, me, defenders, false, true);

        return ofNatObj(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end);
    }

    public WarParser allowedWarTypes(Set<WarType> allowedWarTypes) {
        if (allowedWarTypes != null) getWars().entrySet().removeIf(f -> !allowedWarTypes.contains(f.getValue().getWarType()));
        return this;
    }

    public WarParser allowWarStatuses(Set<WarStatus> statuses) {
        if (statuses != null) getWars().entrySet().removeIf(f -> !statuses.contains(f.getValue().getStatus()));
        return this;
    }

    public void addFilter(BiPredicate<DBWar, AbstractCursor> filter) {
        this.filter = this.filter == null ? filter : this.filter.and(filter);
    }

    public WarParser allowedAttackTypes(Set<AttackType> attackTypes) {
        if (attackTypes != null) {
            addFilter((war, attack) -> attackTypes.contains(attack.getAttack_type()));
        }
        return this;
    }

    public WarParser allowedSuccessTypes(Set<SuccessType> successTypes) {
        if (successTypes != null) {
            addFilter((war, attack) -> successTypes.contains(attack.getSuccess()));
        }
        return this;
    }

    public Map<Integer, DBWar> getWars() {
        if (this.wars == null) {
            this.wars = Locutus.imp().getWarDb().getWars(coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start - TimeUnit.DAYS.toMillis(5), end);
        }
        return wars;
    }

    public Consumer<BiConsumer<DBWar, AbstractCursor>> getAttacks() {
        if (attacks == null) {
            attacks = f -> Locutus.imp().getWarDb().iterateAttacksByWars(getWars().values(), start, end, f);
        }
        if (filter != null) {
            Consumer<BiConsumer<DBWar, AbstractCursor>> parent = attacks;
            this.attacks = consumer -> {
                parent.accept((war, attack) -> {
                    if (filter.test(war, attack)) consumer.accept(war, attack);
                });
            };
        }
        return attacks;
    }


    public Function<DBWar, Boolean> getIsPrimary() {
        return isPrimary;
    }

    public Function<DBWar, Boolean> getIsSecondary() {
        return isSecondary;
    }

    public BiFunction<DBWar, AbstractCursor, Boolean> getAttackPrimary() {
        return (war, attack) -> {
            if (war == null) return false;
            boolean isWarPrimary = isPrimary.apply(war);
            return (isWarPrimary ? war.getAttacker_id() : war.getDefender_id()) == attack.getAttacker_id();
        };
    }

    public BiFunction<DBWar, AbstractCursor, Boolean> getAttackSecondary() {
        return (war, attack) -> {
            if (war == null) return false;
            boolean isWarSecondary = isSecondary.apply(war);
            return (isWarSecondary ? war.getAttacker_id() : war.getDefender_id()) == attack.getAttacker_id();
        };
    }

    public AttackTypeBreakdown toBreakdown() {
        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(nameA, nameB);
        getAttacks().accept((war, attack) -> {
            breakdown.addAttack(war, attack, getAttackPrimary(), getAttackSecondary());
        });
        return breakdown;
    }

    public AttackCost toWarCost(boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        AttackCost cost = new AttackCost(nameA, nameB, buildings, ids, victories, wars, attacks);
        getAttacks().accept((war, attack) -> {
            cost.addCost(attack, war, getAttackPrimary(), getAttackSecondary());
        });
        return cost;
    }

    public <T> Map<T, AttackTypeBreakdown> groupBreakdownByAttack(Function<AbstractCursor, T> groupFunc) {
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        Map<T, AttackTypeBreakdown> grouped = new LinkedHashMap<>();
        getAttacks().accept((war, attack) -> {
            T key = groupFunc.apply(attack);
            AttackTypeBreakdown obj = grouped.computeIfAbsent(key, f -> new AttackTypeBreakdown(nameA, nameB));
            obj.addAttack(war, attack, attPrimary, attSecondary);
        });
        return grouped;
    }

    public <T> Map<T, AttackCost> groupWarCostByAttack(Function<AbstractCursor, T> groupFunc, boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        Map<T, AttackCost> grouped = new LinkedHashMap<>();
        getAttacks().accept((war, attack) -> {
            T key = groupFunc.apply(attack);
            AttackCost cost = grouped.computeIfAbsent(key, f -> new AttackCost(nameA, nameB, buildings, ids, victories, wars, attacks));
            cost.addCost(attack, war, attPrimary, attSecondary);
        });
        return grouped;
    }

    public Map<Long, AttackCost> toWarCostByDay(boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();
        getAttacks().accept((war, attack) -> {
            if (attack.getDate() > System.currentTimeMillis()) {
                System.out.println("attack id:" + attack.getWar_attack_id() + " is in future");
            }
            long turn = TimeUtil.getTurn(attack.getDate());
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(nameA, nameB, buildings, ids, victories, wars, attacks));
            cost.addCost(attack, war, attPrimary, attSecondary);
        });
        return warCostByDay;
    }

    public Map<Integer, AttackCost> toWarCostByNation(boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        Map<Integer, AttackCost> warCostByNation = new HashMap<>();
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        getAttacks().accept((war, attack) -> {
            if (!attPrimary.apply(war, attack) && !attSecondary.apply(war, attack)) return;
            {
                String other = attPrimary.apply(war, attack) ? nameB : nameA;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getAttacker_id(), f -> new AttackCost(PW.getName(attack.getAttacker_id(), false), other, buildings, ids, victories, wars, attacks));
                cost.addCost(attack, war, true);
            }
            {
                String other = attSecondary.apply(war, attack) ? nameA : nameB;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getDefender_id(), f -> new AttackCost(PW.getName(attack.getDefender_id(), false), other, buildings, ids, victories, wars, attacks));
                cost.addCost(attack, war, false);
            }
        });
        return warCostByNation;
    }

    public Map<Integer, AttackCost> toWarCostByAlliance(boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        Map<Integer, AttackCost> warCostByAA = new HashMap<>();
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        getAttacks().accept((war, attack) -> {
            {
                String other = attPrimary.apply(war, attack) ? nameB : nameA;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getAttacker_aa(), f -> new AttackCost(PW.getName(war.getAttacker_aa(), true), other, buildings, ids, victories, wars, attacks));
                cost.addCost(attack, war, true);
            }

            {
                String other = attSecondary.apply(war, attack) ? nameA : nameB;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getDefender_aa(), f -> new AttackCost(PW.getName(war.getDefender_aa(), true), other, buildings, ids, victories, wars, attacks));
                cost.addCost(attack, war, false);
            }
        });
        return warCostByAA;
    }

    public Map<Integer, AttackTypeBreakdown> toAttackTypeByNation() {
        Map<Integer, AttackTypeBreakdown> warCostByNation = new HashMap<>();
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        getAttacks().accept((war, attack) -> {
            if (!attPrimary.apply(war, attack) && !attSecondary.apply(war, attack)) return;
            {
                String other = attPrimary.apply(war, attack) ? nameB : nameA;
                AttackTypeBreakdown cost = warCostByNation.computeIfAbsent(attack.getAttacker_id(), f -> new AttackTypeBreakdown(PW.getName(attack.getAttacker_id(), false), other));
                cost.addAttack(war, attack, true);
            }
            {
                String other = attSecondary.apply(war, attack) ? nameA : nameB;
                AttackTypeBreakdown cost = warCostByNation.computeIfAbsent(attack.getDefender_id(), f -> new AttackTypeBreakdown(PW.getName(attack.getDefender_id(), false), other));
                cost.addAttack(war, attack, false);
            }
        });
        return warCostByNation;
    }

    public Map<Integer, AttackTypeBreakdown> toAttackTypeByAlliance() {
        Map<Integer, AttackTypeBreakdown> warCostByAA = new HashMap<>();
        BiFunction<DBWar, AbstractCursor, Boolean> attPrimary = getAttackPrimary();
        BiFunction<DBWar, AbstractCursor, Boolean> attSecondary = getAttackSecondary();
        getAttacks().accept((war, attack) -> {
            {
                String other = attPrimary.apply(war, attack) ? nameB : nameA;
                AttackTypeBreakdown cost = warCostByAA.computeIfAbsent(war.getAttacker_aa(), f -> new AttackTypeBreakdown(PW.getName(war.getAttacker_aa(), true), other));
                cost.addAttack(war, attack, true);
            }

            {
                String other = attSecondary.apply(war, attack) ? nameA : nameB;
                AttackTypeBreakdown cost = warCostByAA.computeIfAbsent(war.getDefender_aa(), f -> new AttackTypeBreakdown(PW.getName(war.getDefender_aa(), true), other));
                cost.addAttack(war, attack, false);
            }
        });
        return warCostByAA;
    }

    public String getNameA() {
        return nameA;
    }
    public String getNameB() {
        return nameB;
    }

    public DBNation getNation(int nationId, DBWar war) {
        return DBNation.getById(nationId);
    }
}

package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import net.dv8tion.jda.api.entities.Guild;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarAttackParser {
    private final List<DBAttack> attacks;
    private final String nameA, nameB;
    private final Function<DBAttack, Boolean> isPrimary, isSecondary;

    public WarAttackParser(DBWar war, boolean attacker) {
        nameA = PnwUtil.getName(war.attacker_id, false);
        nameB = PnwUtil.getName(war.defender_id, false);
        Function<DBAttack, Boolean> isPrimary = a -> a.attacker_nation_id == war.attacker_id;
        Function<DBAttack, Boolean> isSecondary = b -> b.attacker_nation_id == war.defender_id;
        this.isPrimary = attacker ? isPrimary : isSecondary;
        this.isSecondary = attacker ? isSecondary : isPrimary;
        attacks = war.getAttacks();
    }

    public WarAttackParser(GuildDB db, List<String> args, Set<Character> flags) {
        this(db == null ? null : db.getGuild(), args, flags);
    }

    public WarAttackParser(Guild guild, List<String> args, Set<Character> flags) {
        List<DBAttack> attacks = new LinkedList<>();
        Function<DBAttack, Boolean> isPrimary = null;
        Function<DBAttack, Boolean> isSecondary = null;
        String nameA = "Unknown";
        String nameB = "Unknown";

        DBWar warUrl = null;

        if (args.size() == 1) {
            String arg0 = args.get(0);
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                warUrl = Locutus.imp().getWarDb().getWar(warId);
                if (warUrl == null) throw new IllegalArgumentException("War not found (out of sync?)");

                attacks = Locutus.imp().getWarDb().getAttacksByWarId(warId);

                nameA = PnwUtil.getName(warUrl.attacker_id, false);
                nameB = PnwUtil.getName(warUrl.defender_id, false);
                DBWar finalWarUrl = warUrl;
                isPrimary = a -> a.attacker_nation_id == finalWarUrl.attacker_id;
                isSecondary = b -> b.attacker_nation_id == finalWarUrl.defender_id;
            }
        } else if (args.size() == 2) {
            args = new ArrayList<>(args);
            args.add("*");
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Map<Integer, DBWar> warMap = null;

        if (args.size() == 3) {
            long cutoffMs;
            if (!MathMan.isInteger(args.get(2))) {
                cutoffMs = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(2)) * 1000L;
            } else {
                int days = MathMan.parseInt(args.get(2));
                cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
            }

            Set<Integer> aaIdss1 = DiscordUtil.parseAlliances(guild, args.get(0));
            Set<Integer> aaIdss2 = DiscordUtil.parseAlliances(guild, args.get(1));
            if (aaIdss1 != null && aaIdss2 != null && !aaIdss1.isEmpty() && !aaIdss2.isEmpty()) {
                HashSet<Integer> alliances = new HashSet<>();
                alliances.addAll(aaIdss1);
                alliances.addAll(aaIdss2);
                List<DBWar> wars = Locutus.imp().getWarDb().getWars(alliances, cutoffMs);
                warMap = new HashMap<>();
                for (DBWar war : wars) warMap.put(war.warId, war);
                attacks = Locutus.imp().getWarDb().getAttacksByWars(wars, cutoffMs);
                Map<Integer, DBWar> finalWarMap = warMap;
                isPrimary = a -> {
                    DBWar war = finalWarMap.get(a.war_id);
                    int aa1 = war.attacker_id == a.attacker_nation_id ? war.attacker_aa : war.defender_aa;
                    int aa2 = war.attacker_id == a.attacker_nation_id ? war.defender_aa : war.attacker_aa;
                    return aaIdss1.contains(aa1) && aaIdss2.contains(aa2);
                };
                isSecondary = a -> {
                    DBWar war = finalWarMap.get(a.war_id);
                    int aa1 = war.attacker_id == a.attacker_nation_id ? war.attacker_aa : war.defender_aa;
                    int aa2 = war.attacker_id == a.attacker_nation_id ? war.defender_aa : war.attacker_aa;
                    return aaIdss2.contains(aa1) && aaIdss1.contains(aa2);
                };
                nameA = args.get(0);
                nameB = args.get(1);
            } else {
                Set<DBNation> alliances1 = DiscordUtil.parseNations(guild, args.get(0));
                Set<DBNation> alliances2 = DiscordUtil.parseNations(guild, args.get(1));
                Set<Integer> allIds = new HashSet<>();

                for (DBNation nation : alliances1) allIds.add(nation.getNation_id());
                for (DBNation nation : alliances2) allIds.add(nation.getNation_id());

                nameA = alliances1.size() == 1 ? alliances1.iterator().next().getNation() : args.get(0);
                nameB = alliances2.size() == 1 ? alliances2.iterator().next().getNation() : args.get(1);

                if (alliances1 == null || alliances1.isEmpty()) {
                    throw new IllegalArgumentException("Invalid alliance: `" + args.get(0) + "`");
                }
                if (alliances2 == null || alliances2.isEmpty()) {
                    throw new IllegalArgumentException("Invalid alliance: `" + args.get(1) + "`");
                }


                if (alliances1.size() == 1) {
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances1.iterator().next().getNation_id(), cutoffMs);
                } else if (alliances2.size() == 1) {
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances2.iterator().next().getNation_id(), cutoffMs);
                } else if (args.get(0).equalsIgnoreCase("*")) {
                    attacks = Locutus.imp().getWarDb().getAttacksAny(alliances2.stream().map(f -> f.getNation_id()).collect(Collectors.toSet()), cutoffMs);
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    attacks = Locutus.imp().getWarDb().getAttacksAny(alliances1.stream().map(f -> f.getNation_id()).collect(Collectors.toSet()), cutoffMs);
                } else {
                    attacks = Locutus.imp().getWarDb().getAttacks(allIds, cutoffMs);
                }

                if (args.get(0).equalsIgnoreCase("*")) {
                    isPrimary = a -> {
                        DBNation n2 = nations.get(a.attacker_nation_id);
                        return n2 != null && alliances2.contains(n2);
                    };
                    isSecondary = a -> {
                        DBNation n2 = nations.get(a.defender_nation_id);
                        return n2 != null && alliances2.contains(n2);
                    };
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    isPrimary = a -> {
                        DBNation n1 = nations.get(a.attacker_nation_id);
                        return n1 != null && alliances1.contains(n1);
                    };
                    isSecondary = a -> {
                        DBNation n1 = nations.get(a.defender_nation_id);
                        return n1 != null && alliances1.contains(n1);
                    };
                } else {
                    isPrimary = a -> {
                        DBNation n1 = nations.get(a.attacker_nation_id);
                        DBNation n2 = nations.get(a.defender_nation_id);
                        return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
                    };
                    isSecondary = a -> {
                        DBNation n1 = nations.get(a.attacker_nation_id);
                        DBNation n2 = nations.get(a.defender_nation_id);
                        return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
                    };
                }
            }
        }


        if (flags.contains('o') || flags.contains('d')) {
            if (warMap == null) {
                warMap = new HashMap<>();
                Set<Integer> warIds = new HashSet<>();
                for (DBAttack attack : attacks) warIds.add(attack.war_id);
                List<DBWar> wars = Locutus.imp().getWarDb().getWarsById(warIds);
                for (DBWar war : wars) warMap.put(war.warId, war);
            }
            Map<Integer, DBWar> finalWarMap1 = warMap;
            Function<DBAttack, Boolean> filter = flags.contains('o') ? isPrimary : isSecondary;
            attacks.removeIf(new Predicate<DBAttack>() {
                @Override
                public boolean test(DBAttack attack) {
                    DBWar war = finalWarMap1.get(attack.war_id);
                    if (war == null) return true;
                    DBAttack copy = new DBAttack();
                    copy.attacker_nation_id = war.attacker_id;
                    copy.defender_nation_id = war.defender_id;
                    return !filter.apply(attack);
                }
            });
        }

        this.attacks = attacks;
        this.nameA = nameA;
        this.nameB = nameB;
        this.isPrimary = isPrimary;
        this.isSecondary = isSecondary;
    }

    public List<DBAttack> getAttacks() {
        return attacks;
    }

    public Function<DBAttack, Boolean> getIsPrimary() {
        return isPrimary;
    }

    public Function<DBAttack, Boolean> getIsSecondary() {
        return isSecondary;
    }

    public String getNameA() {
        return nameA;
    }

    public String getNameB() {
        return nameB;
    }

    public AttackTypeBreakdown toBreakdown() {
        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(getNameA(), getNameB());
        breakdown.addAttacks(getAttacks(), getIsPrimary(), getIsSecondary());
        return breakdown;
    }

    public AttackCost toWarCost() {
        AttackCost cost = new AttackCost(nameA, nameB);
        cost.addCost(attacks, isPrimary, isSecondary);
        return cost;
    }

    public Map<Long, AttackCost> toWarCostByDay() {
        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();
        for (DBAttack attack : attacks) {
            long turn = TimeUtil.getTurn(attack.epoch);
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(nameA, nameB));
            cost.addCost(attack, isPrimary, isSecondary);
        }
        return warCostByDay;
    }

    public Map<Integer, AttackCost> toWarCostByNation() {
        Map<Integer, AttackCost> warCostByNation = new HashMap<>();
        for (DBAttack attack : attacks) {
            if (!isSecondary.apply(attack) && !isPrimary.apply(attack)) continue;

            {
                String other = isPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.attacker_nation_id, f -> new AttackCost(PnwUtil.getName(attack.attacker_nation_id, false), other));
                cost.addCost(attack, true);
            }

            {
                String other = isPrimary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.defender_nation_id, f -> new AttackCost(PnwUtil.getName(attack.defender_nation_id, false), other));
                cost.addCost(attack, false);
            }
        }
        return warCostByNation;
    }

    public Map<Integer, AttackCost> toWarCostByAlliance() {
        Map<Integer, DBWar> wars = getWarsById();
        Map<Integer, AttackCost> warCostByAA = new HashMap<>();
        for (DBAttack attack : attacks) {
            DBWar war = wars.get(attack.war_id);
            {
                String other = isPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByAA.computeIfAbsent(war.attacker_aa, f -> new AttackCost(PnwUtil.getName(war.attacker_aa, true), other));
                cost.addCost(attack, true);
            }

            {
                String other = isPrimary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByAA.computeIfAbsent(war.defender_aa, f -> new AttackCost(PnwUtil.getName(war.defender_aa, true), other));
                cost.addCost(attack, false);
            }
        }
        return warCostByAA;
    }

    public Map<Integer, DBWar> getWarsById() {
        Map<Integer, DBWar> map = new HashMap<>();
        for (DBWar war : getWars()) {
            map.put(war.warId, war);
        }
        return map;
    }

    public List<DBWar> getWars() {
        Set<Integer> wars = new HashSet<>();
        for (DBAttack attack : attacks) {
            wars.add(attack.war_id);
        }
        return Locutus.imp().getWarDb().getWarsById(wars);
    }
}

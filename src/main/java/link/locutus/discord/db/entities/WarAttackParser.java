package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarAttackParser {
    private final List<AbstractCursor> attacks;
    private final String nameA, nameB;
    private final Function<AbstractCursor, Boolean> isPrimary, isSecondary;

    public WarAttackParser(DBWar war, boolean attacker) {
        nameA = PnwUtil.getName(war.getAttacker_id(), false);
        nameB = PnwUtil.getName(war.getDefender_id(), false);
        Function<AbstractCursor, Boolean> isPrimary = a -> a.getAttacker_id() == war.getAttacker_id();
        Function<AbstractCursor, Boolean> isSecondary = b -> b.getAttacker_id() == war.getDefender_id();
        this.isPrimary = attacker ? isPrimary : isSecondary;
        this.isSecondary = attacker ? isSecondary : isPrimary;
        attacks = war.getAttacks2();
    }

    public WarAttackParser(GuildDB db, User author, DBNation me, List<String> args, Set<Character> flags) {
        this(db == null ? null : db.getGuild(), author, me, args, flags);
    }

    public WarAttackParser(Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) {
        List<AbstractCursor> attacks = new ArrayList<>();
        Function<AbstractCursor, Boolean> isPrimary = null;
        Function<AbstractCursor, Boolean> isSecondary = null;
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

                attacks = Locutus.imp().getWarDb().getAttacksByWarId2(warUrl, true);

                nameA = PnwUtil.getName(warUrl.getAttacker_id(), false);
                nameB = PnwUtil.getName(warUrl.getDefender_id(), false);
                DBWar finalWarUrl = warUrl;
                isPrimary = a -> a.getAttacker_id() == finalWarUrl.getAttacker_id();
                isSecondary = b -> b.getAttacker_id() == finalWarUrl.getDefender_id();
            }
        } else if (args.size() == 2) {
            args = new ArrayList<>(args);
            args.add("*");
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Map<Integer, DBWar> warMap = null;

        if (args.size() == 3 || args.size() == 4) {
            long start;
            long end;
            if (!MathMan.isInteger(args.get(2))) {
                start = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(2)) * 1000L;
            } else {
                int days = MathMan.parseInt(args.get(2));
                start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
            }
            if (args.size() > 3) {
                if (!MathMan.isInteger(args.get(3))) {
                    end = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(3)) * 1000L;
                } else {
                    int days = MathMan.parseInt(args.get(3));
                    end = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
                }
            } else {
                end = Long.MAX_VALUE;
            }
            if (end <= start) throw new IllegalArgumentException("End date must be greater than start date");

            Set<Integer> aaIdss1 = DiscordUtil.parseAllianceIds(guild, args.get(0));
            Set<Integer> aaIdss2 = DiscordUtil.parseAllianceIds(guild, args.get(1));
            if (aaIdss1 != null && aaIdss2 != null && !aaIdss1.isEmpty() && !aaIdss2.isEmpty()) {
                HashSet<Integer> alliances = new HashSet<>();
                alliances.addAll(aaIdss1);
                alliances.addAll(aaIdss2);
                Set<DBWar> wars = Locutus.imp().getWarDb().getWars(alliances, start - TimeUnit.DAYS.toMillis(6), end);
                attacks = Locutus.imp().getWarDb().getAttacksByWars(wars, start, end);
                Set<Integer> warIdsByAttacks = attacks.stream().map(AbstractCursor::getWar_id).collect(Collectors.toSet());
                wars.removeIf(w -> w.getDate() > start && !warIdsByAttacks.contains(w.warId));

                warMap = new HashMap<>();
                for (DBWar war : wars) warMap.put(war.warId, war);
                Map<Integer, DBWar> finalWarMap = warMap;
                isPrimary = a -> {
                    DBWar war = finalWarMap.get(a.getWar_id());
                    int aa1 = war.getAttacker_id() == a.getAttacker_id() ? war.getAttacker_aa() : war.getDefender_aa();
                    int aa2 = war.getAttacker_id() == a.getAttacker_id() ? war.getDefender_aa() : war.getAttacker_aa();
                    return aaIdss1.contains(aa1) && aaIdss2.contains(aa2);
                };
                isSecondary = a -> {
                    DBWar war = finalWarMap.get(a.getWar_id());
                    int aa1 = war.getAttacker_id() == a.getAttacker_id() ? war.getAttacker_aa() : war.getDefender_aa();
                    int aa2 = war.getAttacker_id() == a.getAttacker_id() ? war.getDefender_aa() : war.getAttacker_aa();
                    return aaIdss2.contains(aa1) && aaIdss1.contains(aa2);
                };
                nameA = args.get(0);
                nameB = args.get(1);
            } else {
                Set<DBNation> alliances1 = DiscordUtil.parseNations(guild, author, me, args.get(0), false, true);
                Set<DBNation> alliances2 = DiscordUtil.parseNations(guild, author, me, args.get(1), false, true);
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
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances1.iterator().next().getNation_id(), start, end);
                } else if (alliances2.size() == 1) {
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances2.iterator().next().getNation_id(), start, end);
                } else if (args.get(0).equalsIgnoreCase("*")) {
                    Set<Integer> attackerIds = alliances2.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
                    attacks = Locutus.imp().getWarDb().queryAttacks()
                            .withWars(f -> attackerIds.contains(f.getAttacker_id()) || attackerIds.contains(f.getDefender_id())).between(start, end).getList();
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    Set<Integer> attackerIds = alliances1.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
                    attacks = Locutus.imp().getWarDb().queryAttacks()
                            .withWars(f -> attackerIds.contains(f.getAttacker_id()) || attackerIds.contains(f.getDefender_id())).between(start, end).getList();
                } else {
                    attacks = Locutus.imp().getWarDb().queryAttacks()
                            .withWarsForNationOrAlliance(allIds::contains, null, f -> f.getDate() <= end && f.possibleEndDate() >= start)
                            .between(start, end).getList();
                }

                if (args.get(0).equalsIgnoreCase("*")) {
                    isPrimary = a -> {
                        DBNation n2 = nations.get(a.getAttacker_id());
                        return n2 != null && alliances2.contains(n2);
                    };
                    isSecondary = a -> {
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n2 != null && alliances2.contains(n2);
                    };
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    isPrimary = a -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        return n1 != null && alliances1.contains(n1);
                    };
                    isSecondary = a -> {
                        DBNation n1 = nations.get(a.getDefender_id());
                        return n1 != null && alliances1.contains(n1);
                    };
                } else {
                    isPrimary = a -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
                    };
                    isSecondary = a -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
                    };
                }
            }
        }


        if (flags.contains('o') || flags.contains('d')) {
            if (warMap == null) {
                warMap = new HashMap<>();
                Set<Integer> warIds = new HashSet<>();
                for (AbstractCursor attack : attacks) warIds.add(attack.getWar_id());
                Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(warIds);
                for (DBWar war : wars) warMap.put(war.warId, war);
            }
            Map<Integer, DBWar> finalWarMap1 = warMap;
            Function<AbstractCursor, Boolean> filter = flags.contains('o') ? isPrimary : isSecondary;
            attacks.removeIf(new Predicate<AbstractCursor>() {
                @Override
                public boolean test(AbstractCursor attack) {
                    DBWar war = finalWarMap1.get(attack.getWar_id());
                    if (war == null) return true;
                    boolean flip = war.getAttacker_id() != attack.getAttacker_id();
                    return filter.apply(attack) == flip;
                }
            });
        }

        this.attacks = attacks;
        this.nameA = nameA;
        this.nameB = nameB;
        this.isPrimary = isPrimary;
        this.isSecondary = isSecondary;
    }

    public List<AbstractCursor> getAttacks() {
        return attacks;
    }

    public Function<AbstractCursor, Boolean> getIsPrimary() {
        return isPrimary;
    }

    public Function<AbstractCursor, Boolean> getIsSecondary() {
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

    public AttackCost toWarCost(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        AttackCost cost = new AttackCost(nameA, nameB, buildings, ids, victories, wars, inclAttacks);
        cost.addCost(attacks, isPrimary, isSecondary);
        return cost;
    }

    public Map<Long, AttackCost> toWarCostByDay(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();
        for (AbstractCursor attack : attacks) {
            long turn = TimeUtil.getTurn(attack.getDate());
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(nameA, nameB, buildings, ids, victories, wars, inclAttacks));
            cost.addCost(attack, isPrimary, isSecondary);
        }
        return warCostByDay;
    }

    public Map<Integer, AttackCost> toWarCostByNation(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        Map<Integer, AttackCost> warCostByNation = new HashMap<>();
        for (AbstractCursor attack : attacks) {
            if (!isSecondary.apply(attack) && !isPrimary.apply(attack)) continue;

            {
                String other = isPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getAttacker_id(), f -> new AttackCost(PnwUtil.getName(attack.getAttacker_id(), false), other, buildings, ids, victories, wars, inclAttacks));
                cost.addCost(attack, true);
            }

            {
                String other = isPrimary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getDefender_id(), f -> new AttackCost(PnwUtil.getName(attack.getDefender_id(), false), other, buildings, ids, victories, wars, inclAttacks));
                cost.addCost(attack, false);
            }
        }
        return warCostByNation;
    }

    public Map<Integer, AttackCost> toWarCostByAlliance(boolean buildings, boolean ids, boolean victories, boolean inclWars, boolean inclAttacks) {
        Map<Integer, DBWar> wars = getWarsById();
        Map<Integer, AttackCost> warCostByAA = new HashMap<>();
        for (AbstractCursor attack : attacks) {
            DBWar war = wars.get(attack.getWar_id());
            {
                String other = isPrimary.apply(attack) ? nameB : nameA;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getAttacker_aa(), f -> new AttackCost(PnwUtil.getName(war.getAttacker_aa(), true), other, buildings, ids, victories, inclWars, inclAttacks));
                cost.addCost(attack, true);
            }

            {
                String other = isPrimary.apply(attack) ? nameA : nameB;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getDefender_aa(), f -> new AttackCost(PnwUtil.getName(war.getDefender_aa(), true), other, buildings, ids, victories, inclWars, inclAttacks));
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

    public Set<DBWar> getWars() {
        Set<Integer> warIds = new IntOpenHashSet();
        for (AbstractCursor attack : attacks) {
            warIds.add(attack.getWar_id());
        }
        return Locutus.imp().getWarDb().getWarsById(warIds);
    }
}

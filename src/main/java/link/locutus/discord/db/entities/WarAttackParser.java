package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;

public class WarAttackParser {
    private final String nameA, nameB;
    private final BiFunction<DBWar, AbstractCursor, Boolean> isPrimary, isSecondary;
    private Consumer<BiConsumer<DBWar, AbstractCursor>> forAttacks;
    private BiPredicate<DBWar, AbstractCursor> filter;

    public WarAttackParser(DBWar war, boolean attacker) {
        nameA = PW.getName(war.getAttacker_id(), false);
        nameB = PW.getName(war.getDefender_id(), false);
        BiFunction<DBWar, AbstractCursor, Boolean> isPrimary = (w, a) -> a.getAttacker_id() == w.getAttacker_id();
        BiFunction<DBWar, AbstractCursor, Boolean> isSecondary = (w, b) -> b.getAttacker_id() == w.getDefender_id();
        this.isPrimary = attacker ? isPrimary : isSecondary;
        this.isSecondary = attacker ? isSecondary : isPrimary;
        forAttacks = f -> Locutus.imp().getWarDb().iterateAttacksByWarId(war, true, f);
    }

    public WarAttackParser(GuildDB db, User author, DBNation me, List<String> args, Set<Character> flags) {
        this(db == null ? null : db.getGuild(), author, me, args, flags);
    }

    public WarAttackParser(Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) {
        BiFunction<DBWar, AbstractCursor, Boolean> isPrimary = null;
        BiFunction<DBWar, AbstractCursor, Boolean> isSecondary = null;
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
                DBWar finalWarUrl = warUrl;
                forAttacks = f -> Locutus.imp().getWarDb().iterateAttacksByWarId(finalWarUrl, true, f);
                nameA = PW.getName(warUrl.getAttacker_id(), false);
                nameB = PW.getName(warUrl.getDefender_id(), false);
                isPrimary = (war, a) -> a.getAttacker_id() == finalWarUrl.getAttacker_id();
                isSecondary = (war, b) -> b.getAttacker_id() == finalWarUrl.getDefender_id();
            }
        } else if (args.size() == 2) {
            args = new ArrayList<>(args);
            args.add("*");
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();

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
                forAttacks = f -> Locutus.imp().getWarDb().iterateAttacksByWars(wars, start, end, f);

                isPrimary = (war, a) -> {
                    int aa1 = war.getAttacker_id() == a.getAttacker_id() ? war.getAttacker_aa() : war.getDefender_aa();
                    int aa2 = war.getAttacker_id() == a.getAttacker_id() ? war.getDefender_aa() : war.getAttacker_aa();
                    return aaIdss1.contains(aa1) && aaIdss2.contains(aa2);
                };
                isSecondary = (war, a) -> {
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
                    forAttacks = f -> Locutus.imp().getWarDb().iterateAttacks(alliances1.iterator().next().getNation_id(), start, end, f);
                } else if (alliances2.size() == 1) {
                    forAttacks = f -> Locutus.imp().getWarDb().iterateAttacks(alliances2.iterator().next().getNation_id(), start, end, f);
                } else if (args.get(0).equalsIgnoreCase("*")) {
                    Set<Integer> attackerIds = alliances2.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
                    forAttacks = f -> Locutus.imp().getWarDb().queryAttacks()
                            .withWars(war -> attackerIds.contains(war.getAttacker_id()) || attackerIds.contains(war.getDefender_id())).between(start, end).iterateAttacks(f);
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    Set<Integer> attackerIds = alliances1.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
                    forAttacks = f -> Locutus.imp().getWarDb().queryAttacks()
                            .withWars(war -> attackerIds.contains(war.getAttacker_id()) || attackerIds.contains(war.getDefender_id())).between(start, end).iterateAttacks(f);
                } else {
                    forAttacks = f -> Locutus.imp().getWarDb().queryAttacks()
                            .withWarsForNationOrAlliance(allIds::contains, null, war -> war.getDate() <= end && war.possibleEndDate() >= start)
                            .between(start, end).iterateAttacks(f);
                }

                if (args.get(0).equalsIgnoreCase("*")) {
                    isPrimary = (war, a) -> {
                        DBNation n2 = nations.get(a.getAttacker_id());
                        return n2 != null && alliances2.contains(n2);
                    };
                    isSecondary = (war, a) -> {
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n2 != null && alliances2.contains(n2);
                    };
                } else if (args.get(1).equalsIgnoreCase("*")) {
                    isPrimary = (war, a) -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        return n1 != null && alliances1.contains(n1);
                    };
                    isSecondary = (war, a) -> {
                        DBNation n1 = nations.get(a.getDefender_id());
                        return n1 != null && alliances1.contains(n1);
                    };
                } else {
                    isPrimary = (war, a) -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
                    };
                    isSecondary = (war, a) -> {
                        DBNation n1 = nations.get(a.getAttacker_id());
                        DBNation n2 = nations.get(a.getDefender_id());
                        return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
                    };
                }
            }
        }


        if (flags.contains('o') || flags.contains('d')) {
            BiFunction<DBWar, AbstractCursor, Boolean> filter = flags.contains('o') ? isPrimary : isSecondary;
            this.filter = (war, attack) -> {
                boolean flip = war.getAttacker_id() != attack.getAttacker_id();
                return filter.apply(war, attack) != flip;
            };
        }

        this.nameA = nameA;
        this.nameB = nameB;
        this.isPrimary = isPrimary;
        this.isSecondary = isSecondary;
    }

    public void iterateAttacks(BiConsumer<DBWar, AbstractCursor> forEach) {
        if (filter != null) {
            BiConsumer<DBWar, AbstractCursor> finalForEach = forEach;
            forEach = forEach.andThen((war, attack) -> {
                if (filter.test(war, attack)) {
                    finalForEach.accept(war, attack);
                }
            });
        }
        forAttacks.accept(forEach);
    }

    public BiFunction<DBWar, AbstractCursor, Boolean> getIsPrimary() {
        return isPrimary;
    }

    public BiFunction<DBWar, AbstractCursor, Boolean> getIsSecondary() {
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
        iterateAttacks((war, attack) -> {
            breakdown.addAttack(war, attack, getIsPrimary(), getIsSecondary());
        });
        return breakdown;
    }

    public AttackCost toWarCost(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        AttackCost cost = new AttackCost(nameA, nameB, buildings, ids, victories, wars, inclAttacks);
        iterateAttacks((w, attack) -> {
            cost.addCost(attack, w, isPrimary, isSecondary);
        });
        return cost;
    }

    public Map<Long, AttackCost> toWarCostByDay(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();
        iterateAttacks((war, attack) -> {
            long turn = TimeUtil.getTurn(attack.getDate());
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(nameA, nameB, buildings, ids, victories, wars, inclAttacks));
            cost.addCost(attack, attack.getWar(), isPrimary, isSecondary);
        });
        return warCostByDay;
    }

    public Map<Integer, AttackCost> toWarCostByNation(boolean buildings, boolean ids, boolean victories, boolean wars, boolean inclAttacks) {
        Map<Integer, AttackCost> warCostByNation = new HashMap<>();
        iterateAttacks((war, attack) -> {
            if (!isSecondary.apply(war, attack) && !isPrimary.apply(war, attack)) return;

            {
                String other = isPrimary.apply(war, attack) ? nameB : nameA;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getAttacker_id(), f -> new AttackCost(PW.getName(attack.getAttacker_id(), false), other, buildings, ids, victories, wars, inclAttacks));
                cost.addCost(attack, attack.getWar(), true);
            }

            {
                String other = isPrimary.apply(war, attack) ? nameA : nameB;
                AttackCost cost = warCostByNation.computeIfAbsent(attack.getDefender_id(), f -> new AttackCost(PW.getName(attack.getDefender_id(), false), other, buildings, ids, victories, wars, inclAttacks));
                cost.addCost(attack, attack.getWar(), false);
            }
        });
        return warCostByNation;
    }

    public Map<Integer, AttackCost> toWarCostByAlliance(boolean buildings, boolean ids, boolean victories, boolean inclWars, boolean inclAttacks) {
        Map<Integer, AttackCost> warCostByAA = new HashMap<>();
        iterateAttacks((war, attack) -> {
            {
                String other = isPrimary.apply(war, attack) ? nameB : nameA;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getAttacker_aa(), f -> new AttackCost(PW.getName(war.getAttacker_aa(), true), other, buildings, ids, victories, inclWars, inclAttacks));
                cost.addCost(attack, attack.getWar(), true);
            }

            {
                String other = isPrimary.apply(war, attack) ? nameA : nameB;
                AttackCost cost = warCostByAA.computeIfAbsent(war.getDefender_aa(), f -> new AttackCost(PW.getName(war.getDefender_aa(), true), other, buildings, ids, victories, inclWars, inclAttacks));
                cost.addCost(attack, attack.getWar(), false);
            }
        });
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
        Set<DBWar> wars = new ObjectOpenHashSet<>();
        iterateAttacks((war, attack) -> {
            wars.add(war);
        });
        return wars;
    }
}

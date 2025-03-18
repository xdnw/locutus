package link.locutus.discord.commands.rankings;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.data.Row;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeDualNumericTable;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static link.locutus.discord.commands.rankings.WarCostRankingByDay.processTotal;

public class WarCostByDay extends Command {
    public WarCostByDay() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.by_day.warcost_versus.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days>` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public String desc() {
        return """
                Get a war breakdown by day
                Add `-b` to show breakdown by attack type
                Add `-f` to show Full cost
                Add `-l` to show loot
                Add `-c` to show consumption
                Add `-a` to show ammunition usage
                Add `-g` to show gasoline usage
                Add `-u` to show unit losses
                Add `-h` to show H-Bomb (nuke) losses
                Add `-m` to show Missile losses
                Add `-p` to show Plane losses
                Add `-t` to show Tank losses
                Add `-s` to show Soldier losses
                Add `-i` to show Infra losses
                Add `-o` to graph a running total,   
                Add `-j` to attach json/csv""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3) {
            return usage(args.size(), 1, 3, channel);
        }
        if ((args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage("Please specify two different coalitions", channel);
        }
        if (flags.isEmpty()) {
            return usage("Please specify a breakdown type flag", channel);
        }

        boolean attachJson = flags.contains('j');
        boolean attachCsv = flags.contains('j');
        String arg0 = args.get(0);

        Consumer<BiConsumer<DBWar, AbstractCursor>> attacks = null;
        BiFunction<DBWar, AbstractCursor, Boolean> isPrimary = null;
        BiFunction<DBWar, AbstractCursor, Boolean> isSecondary = null;
        String nameA = "Unknown";
        String nameB = "Unknown";

        if (args.size() == 1) {
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                DBWar war = Locutus.imp().getWarDb().getWar(warId);
                if (war == null) return "War not found (out of sync?)";

                attacks = f -> Locutus.imp().getWarDb().iterateAttacksByWarId(war, true, f);

                nameA = PW.getName(war.getAttacker_id(), false);
                nameB = PW.getName(war.getDefender_id(), false);
                isPrimary = (w, a) -> a.getAttacker_id() == w.getAttacker_id();
                isSecondary = (w, b) -> b.getAttacker_id() == w.getDefender_id();
            }
        } else if (args.size() == 2) {
            args = new ArrayList<>(args);
            args.add("*");
        }
        if (args.size() == 3) {
            Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();
            if (!MathMan.isInteger(args.get(2))) {
                return usage("Not a valid whole number: `" + args.get(2) + "`" , channel);
            }
            int days = MathMan.parseInt(args.get(2));
            long cutoffTurn = TimeUtil.getTurn(ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L);
            long cutoffMs = TimeUtil.getTimeFromTurn(cutoffTurn - cutoffTurn % 12);
            long warCutoff = cutoffMs - TimeUnit.DAYS.toMillis(5);

            Set<Integer> aaIdss1 = DiscordUtil.parseAllianceIds(guild, args.get(0));
            Set<Integer> aaIdss2 = DiscordUtil.parseAllianceIds(guild, args.get(1));
            if (aaIdss1 != null && aaIdss2 != null && !aaIdss1.isEmpty() && !aaIdss2.isEmpty()) {
                HashSet<Integer> alliances = new HashSet<>();
                alliances.addAll(aaIdss1);
                alliances.addAll(aaIdss2);
                Set<DBWar> wars = Locutus.imp().getWarDb().getWars(alliances, warCutoff);
                Map<Integer, DBWar> warMap = new HashMap<>();
                for (DBWar war : wars) warMap.put(war.warId, war);
                attacks = f -> Locutus.imp().getWarDb().iterateAttacksByWars(wars, cutoffMs, f);
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

                if (alliances1.isEmpty()) {
                    return "Invalid alliance: `" + args.get(0) + "`";
                }
                if (alliances2.isEmpty()) {
                    return "Invalid alliance: `" + args.get(1) + "`";
                }


                if (alliances1.size() == 1) {
                    attacks = f -> Locutus.imp().getWarDb().iterateAttacks(alliances1.iterator().next().getNation_id(), cutoffMs, f);
                } else if (alliances2.size() == 1) {
                    attacks = f -> Locutus.imp().getWarDb().iterateAttacks(alliances2.iterator().next().getNation_id(), cutoffMs, f);
                } else {
                    attacks = f -> Locutus.imp().getWarDb().iterateAttacks(allIds, cutoffMs, f);
                }

                isPrimary = (w, a) -> {
                    DBNation n1 = nations.get(a.getAttacker_id());
                    DBNation n2 = nations.get(a.getDefender_id());
                    return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
                };
                isSecondary = (w, a) -> {
                    DBNation n1 = nations.get(a.getAttacker_id());
                    DBNation n2 = nations.get(a.getDefender_id());
                    return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
                };
            }
        }
        if (args.isEmpty() || args.size() > 3) {
            return usage(args.size(), 1, 3, channel);
        }

        Map<Long, AttackCost> warCostByDay = new Long2ObjectOpenHashMap<>();

        String finalNameA = nameA;
        String finalNameB = nameB;

        long now = System.currentTimeMillis();
        BiFunction<DBWar, AbstractCursor, Boolean> finalIsPrimary = Objects.requireNonNull(isPrimary);
        BiFunction<DBWar, AbstractCursor, Boolean> finalIsSecondary = Objects.requireNonNull(isSecondary);
        attacks.accept((war, attack) -> {
            if (attack.getDate() > now) return;
            long turn = TimeUtil.getTurn(attack.getDate());
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(finalNameA, finalNameB, flags.contains('r'), false, false, false, flags.contains('b')));
            cost.addCost(attack, war, finalIsPrimary, finalIsSecondary);
        });

        long min = Collections.min(warCostByDay.keySet());
        long max = Collections.max(warCostByDay.keySet());
        boolean total = flags.contains('o');
        List<TimeDualNumericTable<AttackCost>> tables = new ArrayList<>();
        if (flags.contains('i')) tables.add(new TimeDualNumericTable<>("Infra Loss", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getInfraLost(true), cost.getInfraLost(false));
                processTotal(total, this);
            }
        });
        if (flags.contains('s')) tables.add(new TimeDualNumericTable<>("Soldier Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.SOLDIER, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.SOLDIER, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('t')) tables.add(new TimeDualNumericTable<>("Tank Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.TANK, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.TANK, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('p')) tables.add(new TimeDualNumericTable<>("Plane Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.AIRCRAFT, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.AIRCRAFT, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('n')) tables.add(new TimeDualNumericTable<>("Naval Ship Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.SHIP, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.SHIP, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('m')) tables.add(new TimeDualNumericTable<>("Missile Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.MISSILE, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.MISSILE, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('h'))
            tables.add(new TimeDualNumericTable<>("H-Bomb (nuke) Losses", "day", null, nameA, nameB) {
                @Override
                public void add(long day, AttackCost cost) {
                    add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.NUKE, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.NUKE, 0));
                    processTotal(total, this);
                }
            });
        if (flags.contains('u')) tables.add(new TimeDualNumericTable<>("Unit Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, ResourceType.convertedTotal(cost.getUnitCost(true)), ResourceType.convertedTotal(cost.getUnitCost(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('g')) tables.add(new TimeDualNumericTable<>("Gasoline", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getConsumption(true).getOrDefault(ResourceType.GASOLINE, 0d), cost.getConsumption(false).getOrDefault(ResourceType.GASOLINE, 0d));
                processTotal(total, this);
            }
        });
        if (flags.contains('a')) tables.add(new TimeDualNumericTable<>("Ammunition", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getConsumption(true).getOrDefault(ResourceType.MUNITIONS, 0d), cost.getConsumption(false).getOrDefault(ResourceType.MUNITIONS, 0d));
                processTotal(total, this);
            }
        });
        if (flags.contains('c')) tables.add(new TimeDualNumericTable<>("Consumption", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, ResourceType.convertedTotal(cost.getConsumption(true)), ResourceType.convertedTotal(cost.getConsumption(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('r')) tables.add(new TimeDualNumericTable<>("Building Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getNumBuildingsDestroyed(true), cost.getNumBuildingsDestroyed(true));
                processTotal(total, this);
            }
        });
        if (flags.contains('l')) tables.add(new TimeDualNumericTable<>("Looted", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, -ResourceType.convertedTotal(cost.getLoot(true)), -ResourceType.convertedTotal(cost.getLoot(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('f')) tables.add(new TimeDualNumericTable<>("Full Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, ResourceType.convertedTotal(cost.getTotal(true)), ResourceType.convertedTotal(cost.getTotal(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('b')) {
            for (AttackType attType : AttackType.values) {
                tables.add(new TimeDualNumericTable<>("Num " + attType.getName(), "day", null, nameA, nameB) {
                    @Override
                    public void add(long day, AttackCost cost) {
                        ArrayList<AbstractCursor> a = new ArrayList<>(cost.getAttacks(true));
                        ArrayList<AbstractCursor> b = new ArrayList<>(cost.getAttacks(false));
                        a.removeIf(f -> f.getAttack_type() != attType);
                        b.removeIf(f -> f.getAttack_type() != attType);
                        add(day, a.size(), b.size());
                        processTotal(total, this);
                    }
                });
            }
        }

        AttackCost nullCost = new AttackCost("", "", flags.contains('r'), false, false, true, flags.contains('b'));
        for (long day = min; day <= max; day++) {
            long dayOffset = day - min;
            AttackCost cost = warCostByDay.get(day);
            if (cost == null) {
                cost = nullCost;
            }

            for (TimeDualNumericTable<AttackCost> table : tables) {
                table.add(dayOffset, cost);
            }
        }

        for (TimeDualNumericTable<AttackCost> table : tables) {
            table.write(channel, TimeFormat.DAYS_TO_DATE, TableNumberFormat.SI_UNIT, GraphType.LINE, min, attachJson, attachCsv, null, null);
        }

        return null;
    }
}

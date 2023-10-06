package link.locutus.discord.commands.rankings;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.data.Row;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarAttackParser;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;
import java.util.function.Function;

public class WarCostRankingByDay extends Command {
    public WarCostRankingByDay() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "`" + super.help() + " <coalition-1> <coalition-2> <coalition-n> <days>`";
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
                Add `-o` to graph a running total
                Add `-e` to show enemy stats instead of attacker
                Add `-d` to show raw data (csv)
                Add `-n` to show number buildings lost
                """;
    }

    private void add2(TimeNumericTable<Map<String, WarAttackParser>> table, long dayRelative, long dayOffset, Map<String, WarAttackParser> parserMap, Map<String, Map<Long, AttackCost>> byDayMap, Function<AttackCost, Number> calc) {
        Comparable[] values = new Comparable[parserMap.size() + 1];
        values[0] = dayRelative;

        int i = 1;
        for (Map.Entry<String, WarAttackParser> entry : parserMap.entrySet()) {
            String name = entry.getKey();
            WarAttackParser parser = entry.getValue();
            Map<Long, AttackCost> costByDay = byDayMap.get(name);

            AttackCost cost = costByDay.getOrDefault(dayRelative + dayOffset, null);
            double val = 0;
            if (cost != null) {
                val = calc.apply(cost).doubleValue();
            }
            values[i++] = val;

        }
        table.getData().add(values);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) {
            return usage(args.size(), 2, channel);
        }
        int days = Integer.parseInt(args.get(args.size() - 1));
        if (days > 1000 || days <= 1) return "Invalid number of days: `" + days + "`";


        Map<String, WarAttackParser> coalitions = new LinkedHashMap<>();
        Map<String, Map<Long, AttackCost>> coalitionsByDay = new LinkedHashMap<>();
        for (int i = 0; i < args.size() - 1; i++) {
            String arg = args.get(i);
            WarAttackParser parser = new WarAttackParser(guild, Arrays.asList(arg, "*", (days + 1) + "d"), flags);
            coalitions.put(arg, parser);
            coalitionsByDay.put(arg, parser.toWarCostByDay(true, false, false, false, false));
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Map.Entry<String, Map<Long, AttackCost>> entry : coalitionsByDay.entrySet()) {
            min = Math.min(min, Collections.min(entry.getValue().keySet()));
            max = Math.max(max, Collections.max(entry.getValue().keySet()));
        }
        long finalMin = min;

        String[] labels = coalitions.keySet().toArray(new String[0]);

        boolean primary = !flags.contains('e');

        boolean total = flags.contains('o');
        List<TimeNumericTable<Map<String, WarAttackParser>>> tables = new ArrayList<>();
        if (flags.contains('i')) {
            tables.add(new TimeNumericTable<>("Infra Loss", "day", null, labels) {
                @Override
                public void add(long day, Map<String, WarAttackParser> costMap) {
                    add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getInfraLost(primary));
                    processTotal(total, this);
                }
            });
        }

        if (flags.contains('s')) tables.add(new TimeNumericTable<>("Soldier Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.SOLDIER, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('n')) tables.add(new TimeNumericTable<>("Building Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getNumBuildingsDestroyed(primary));
                processTotal(total, this);
            }
        });
        if (flags.contains('t')) tables.add(new TimeNumericTable<>("Tank Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.TANK, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('p')) tables.add(new TimeNumericTable<>("Plane Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.AIRCRAFT, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('n')) tables.add(new TimeNumericTable<>("Naval Ship Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.SHIP, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('m')) tables.add(new TimeNumericTable<>("Missile Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.MISSILE, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('h')) tables.add(new TimeNumericTable<>("H-Bomb (nuke) Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> f.getUnitsLost(primary).getOrDefault(MilitaryUnit.NUKE, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('u')) tables.add(new TimeNumericTable<>("Unit Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> PnwUtil.convertedTotal(f.getUnitCost(primary)));
                processTotal(total, this);
            }
        });
        if (flags.contains('g')) tables.add(new TimeNumericTable<>("Gasoline", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, f -> PnwUtil.convertedTotal(f.getUnitCost(primary)));
                processTotal(total, this);
            }
        });
        if (flags.contains('a')) tables.add(new TimeNumericTable<>("Ammunition", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, cost -> cost.getConsumption(primary).getOrDefault(ResourceType.MUNITIONS, 0d));
                processTotal(total, this);
            }
        });
        if (flags.contains('c')) tables.add(new TimeNumericTable<>("Consumption", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, cost -> PnwUtil.convertedTotal(cost.getConsumption(primary)));
                processTotal(total, this);
            }
        });
        if (flags.contains('l')) tables.add(new TimeNumericTable<>("Looted", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, cost -> PnwUtil.convertedTotal(cost.getLoot(primary)));
                processTotal(total, this);
            }
        });
        if (flags.contains('f')) tables.add(new TimeNumericTable<>("Full Losses", "day", null, labels) {
            @Override
            public void add(long day, Map<String, WarAttackParser> costMap) {
                add2(this, day, finalMin, costMap, coalitionsByDay, cost -> PnwUtil.convertedTotal(cost.getTotal(primary)));
                processTotal(total, this);
            }
        });
        if (flags.contains('b')) {
            for (AttackType attType : AttackType.values) {
                tables.add(new TimeNumericTable<>("Num " + attType.getName(), "day", null, labels) {
                    @Override
                    public void add(long day, Map<String, WarAttackParser> costMap) {

                        add2(this, day, finalMin, costMap, coalitionsByDay, cost -> {
                            ArrayList<AbstractCursor> a = new ArrayList<>(cost.getAttacks(true));
                            ArrayList<AbstractCursor> b = new ArrayList<>(cost.getAttacks(false));
                            a.removeIf(f -> f.getAttack_type() != attType);
                            b.removeIf(f -> f.getAttack_type() != attType);
                            return primary ? a.size() : b.size();
                        });

                        processTotal(total, this);
                    }
                });
            }
        }

        for (long day = min; day <= max; day++) {
            long dayRelative = day - min;

            for (TimeNumericTable<Map<String, WarAttackParser>> table : tables) {
                table.add(dayRelative, coalitions);
            }
        }

        for (TimeNumericTable<Map<String, WarAttackParser>> table : tables) {
            table.write(channel, true, false);
        }
        if (tables.isEmpty()) return "Please use one of the flag";

        return null;
    }

    private void processTotal(boolean total, TimeNumericTable table) {
        if (!total) return;
        DataTable data = table.getData();
        if (data.getRowCount() <= 1) return;
        Row row1 = data.getRow(data.getRowCount() - 2);
        Row row2 = data.getRow(data.getRowCount() - 1);

        Long day = (Long) row2.get(0);

        Comparable<?>[] arr = new Comparable[row2.size()];
        arr[0] = day;
        for (int i = 1; i < row1.size(); i++) arr[i] = (Double) row2.get(i) + (Double) row1.get(i);

        data.removeLast();
        data.add(arr);
    }
}

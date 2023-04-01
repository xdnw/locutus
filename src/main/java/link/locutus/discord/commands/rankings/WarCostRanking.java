package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.*;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class WarCostRanking extends Command {
    public WarCostRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    public static double scale(DBNation nation, double value, boolean enabled) {
        if (enabled) {
            value /= nation.getCities();
        }
        return value;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " [nations|alliances] [days]";
    }

    @Override
    public String desc() {
        return """
                Ranking of nations or alliances and their war costs over days
                Add -u to exclude unit cost
                Add -i to exclude infra cost
                Add -c to exclude consumption
                Add -l to exclude loot
                Add -t to rank total instead of average per war
                Add -p to do loss instead of profit
                Add -d to do damage dealt instead of cost
                Add -n to do net instead of total/average
                Add -a to group by alliance instead of nation
                Add -s to scale per city
                Add `kill:<unit>` to only include kills
                Add `death:<unit>` to only include unit losses
                Add `attack:<attack_type>` to sum attack types
                Add `resource:<resource>` to sum specific resource losses
                Add `-f` to not attach the full ranking File""";
    }

    @Override
    public String onCommand(MessageReceivedEvent event2, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        Iterator<String> iter = args.iterator();
        MilitaryUnit unitKill = null;
        MilitaryUnit unitLoss = null;
        AttackType attType = null;
        ResourceType resourceType = null;
        String typeName = null;
        while (iter.hasNext()) {
            String arg = iter.next();
            String[] split = arg.split("[:|=]");
            if (arg.startsWith("kill:")) {
                if (split.length != 2) return "Use `kill:UNIT_TYPE`";
                unitKill = MilitaryUnit.get(split[1]);
                if (unitKill == null)
                    throw new IllegalArgumentException("Valid units are: " + StringMan.getString(MilitaryUnit.values()));
                iter.remove();
                typeName = unitKill.name();
            } else if (arg.startsWith("death:")) {
                if (split.length != 2) return "Use `death:UNIT_TYPE`";
                unitLoss = MilitaryUnit.get(split[1]);
                if (unitLoss == null)
                    throw new IllegalArgumentException("Valid units are: " + StringMan.getString(MilitaryUnit.values()));
                iter.remove();
                typeName = unitLoss.name();
            } else if (arg.startsWith("attack:")) {
                if (split.length != 2) return "Use `attack:ATTACK_TYPE`";
                attType = AttackType.get(split[1]);
                if (attType == null)
                    throw new IllegalArgumentException("Valid attack types are: " + StringMan.getString(AttackType.values()));
                iter.remove();
                typeName = attType.name();
            } else if (arg.startsWith("resource:")) {
                if (split.length != 2) return "Use `resource:RESOURCE`";
                resourceType = ResourceType.parse(split[1]);
                iter.remove();
                typeName = resourceType.name();
            }
        }

        if (args.size() == 0 || args.size() > 3) return usage(event2);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, e -> e));
        boolean isAA = flags.contains('a'); // args.get(0).equalsIgnoreCase("*") ||


        boolean profit = !flags.contains('p') && unitKill == null && unitLoss == null && attType == null;
        boolean units = !flags.contains('u');
        boolean infra = !flags.contains('i');
        boolean consumption = !flags.contains('c');
        boolean average = !flags.contains('t');
        boolean loot = !flags.contains('l');

        boolean scale = flags.contains('s');

        boolean damage = flags.contains('d');
        boolean net = !damage || flags.contains('n');

        int limit = 25;

        int sign = profit ? -1 : 1;
        long diff = TimeUtil.timeToSec(args.get(1)) * 1000L;
        long start = System.currentTimeMillis() - diff;
        long end = args.size() >= 3 ? System.currentTimeMillis() - (TimeUtil.timeToSec(args.get(2)) * 1000L) : Long.MAX_VALUE;

        String diffStr;
        if (end == Long.MAX_VALUE) {
            diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);
        } else {
            diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, end - start);
        }

        String title = (damage && net ? "Net " : "Total ") + (typeName == null ? "" : typeName + " ") + (profit ? damage ? "damage" : "profit" : (unitKill != null ? "kills" : unitLoss != null ? "deaths" : "losses")) + " " + (average ? "per" : "of") + " war (%s)";
        title = String.format(title, diffStr);

        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(start, end);

        GroupedRankBuilder<Integer, DBAttack> attackGroup = new RankBuilder<>(attacks)
                .group((attack, map) -> {
                    // Group attacks into attacker and defender
                    map.put(attack.attacker_nation_id, attack);
                    map.put(attack.defender_nation_id, attack);
                });

        BiFunction<Boolean, DBAttack, Double> valueFunc;
        {
            BiFunction<Boolean, DBAttack, Double> getValue = null;
            if (unitKill != null) {
                MilitaryUnit finalUnit = unitKill;
                getValue = (attacker, attack) -> attack.getUnitLosses(!attacker).getOrDefault(finalUnit, 0).doubleValue();
            }
            if (unitLoss != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (1)");
                MilitaryUnit finalUnit = unitLoss;
                getValue = (attacker, attack) -> attack.getUnitLosses(attacker).getOrDefault(finalUnit, 0).doubleValue();
            }
            if (attType != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (2)");
                AttackType finalAttType = attType;
                getValue = (attacker, attack) -> attack.attack_type == finalAttType ? 1d : 0d;
            }
            if (resourceType != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (3)");
                double min = damage ? 0 : Double.NEGATIVE_INFINITY;
                ResourceType finalResourceType = resourceType;
                getValue = (attacker, attack) -> Math.max(min, attack.getLosses(attacker, units, infra, consumption, loot).getOrDefault(finalResourceType, 0d));
            }
            if (getValue == null) {
                getValue = (attacker, attack) -> {
                    if (!damage) {
                        return attack.getLossesConverted(attacker, units, infra, consumption, loot);
                    } else {
                        double total = 0;
                        Map<ResourceType, Double> losses = attack.getLosses(attacker, units, infra, consumption, loot);
                        for (Map.Entry<ResourceType, Double> entry : losses.entrySet()) {
                            if (entry.getValue() > 0) total += PnwUtil.convertedTotal(entry.getKey(), entry.getValue());
                        }
                        return total;
                    }
                };
            }
            valueFunc = getValue;
        }

        NumericMappedRankBuilder<Integer, Integer, Double> byNationMap;
        if (!damage) {
            byNationMap = attackGroup.map((i, a) -> a.war_id,
                    // Convert attack to profit value
                    (nationdId, attack) -> {
                        DBNation nation = nationMap.get(nationdId);
                        return nation != null ? scale(nation, sign * valueFunc.apply(attack.attacker_nation_id == nationdId, attack), scale) : 0;
                    });
        } else {
            byNationMap = attackGroup.map((i, a) -> a.war_id,
                    // Convert attack to profit value
                    (nationdId, attack) -> {
                        DBNation nation = nationMap.get(nationdId);
                        if (nation == null) return 0d;
                        boolean primary = (attack.attacker_nation_id != nationdId) == profit;
                        double total = valueFunc.apply(primary, attack);
                        if (net) {
                            total -= valueFunc.apply(!primary, attack);
                        }
                        return scale(nation, total, scale);
                    });
        }

        SummedMapRankBuilder<Integer, Number> byNation;
        if (average) {
            byNation = byNationMap.average();
        } else {
            byNation = byNationMap.sum();
        }

        RankBuilder<String> ranks;
        if (isAA) {
            // Group it by alliance
            NumericGroupRankBuilder<Integer, Number> byAAMap = byNation.group((entry, builder) -> {
                DBNation nation = nationMap.get(entry.getKey());
                if (nation != null) {
                    builder.put(nation.getAlliance_id(), entry.getValue());
                }
            });
            SummedMapRankBuilder<Integer, Number> byAA = average ? byAAMap.average() : byAAMap.sum();

            // Sort descending
            ranks = byAA.sort()
                    // Change key to alliance name
                    .nameKeys(allianceId -> PnwUtil.getName(allianceId, true));
        } else {
            // Sort descending
            ranks = byNation
                    .removeIfKey(nationId -> !nationMap.containsKey(nationId))
                    .sort()
                    // Change key to alliance name
                    .nameKeys(nationId -> nationMap.get(nationId).getNation());
        }

        // Embed the rank list
        ranks.build(event2, title);

        if (ranks.get().size() > 25 && !flags.contains('f')) {
            DiscordUtil.upload(event2.getChannel(), title, ranks.toString());
        }
        return null;
    }
}
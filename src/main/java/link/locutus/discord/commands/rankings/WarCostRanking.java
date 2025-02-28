package link.locutus.discord.commands.rankings;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.TriFunction;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class WarCostRanking extends Command {
    public WarCostRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.warCostRanking.cmd);
    }

    public static TriFunction<Integer, DBWar, Double, Double> getScaleFunction(boolean perCity, Map<Integer, Integer> warsByGroup, boolean groupByAA) {
        ToIntBiFunction<Integer, DBWar> getGroup;
        if (groupByAA) {
            getGroup = (nation, war) -> war.isAttacker(nation) ? war.getAttacker_aa() : war.getDefender_aa();
        } else {
            getGroup = (nation, war) -> nation;
        }
        ToIntBiFunction<Integer, DBWar> getCities;
        if (groupByAA) {
            Map<Integer, Integer> citiesByAACache = new Int2IntOpenHashMap();
            getCities = (nation, war) -> {
                int aaId = getGroup.applyAsInt(nation, war);
                return citiesByAACache.computeIfAbsent(aaId, i -> {
                    DBAlliance alliance = DBAlliance.get(war.getAttacker_id() == nation ? war.getAttacker_aa() : war.getDefender_aa());
                    if (alliance == null) return war.getCities(war.isAttacker(nation));
                    int total = 0;
                    for (DBNation n : alliance.getNations(true, 0, true)) {
                        total += n.getCities();
                    }
                    return total;
                });
            };
        } else {
            getCities = (nation, war) -> {
                int cities = war.getCities(war.isAttacker(nation));
                if (cities == 0) {
                    DBNation obj = DBNation.getById(nation);
                    if (obj != null) return obj.getCities();
                    cities = 1;
                }
                return cities;
            };
        }
        ToIntBiFunction<Integer, DBWar> getWarsByGroup = warsByGroup == null ? null : (nation, war) -> warsByGroup.get(getGroup.applyAsInt(nation, war));

        ToIntBiFunction<Integer, DBWar> getFactor;
        if (perCity) {
            if (warsByGroup != null) {
                getFactor = (nation, war) -> Math.max(1, getWarsByGroup.applyAsInt(nation, war)) * getCities.applyAsInt(nation, war);
            } else {
                getFactor = getCities;
            }
        } else if (warsByGroup != null) {
            getFactor = (nation, war) -> Math.max(1, getWarsByGroup.applyAsInt(nation, war));
        } else {
            return (nation, war, value) -> value;
        }
        return (nation, war, value) -> value / getFactor.applyAsInt(nation, war);
    }

    private static double scale(DBNation nation, double value, boolean enabled, boolean groupByAlliance) {
        if (enabled) {
            if (groupByAlliance) {
                DBAlliance alliance = nation.getAlliance(false);
                if (alliance == null) return 0;
                int total = 0;
                for (DBNation n : alliance.getNations()) {
                    if (n != nation && (n.getPositionEnum().id <= Rank.APPLICANT.id || n.getVm_turns() > 0)) continue;
                    total += n.getCities();
                }
                return value / total;
            } else {
                value /= nation.getCities();
            }
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
                Add -b to exclude buildings
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
                Add `-f` to attach the full ranking File""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
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

        if (args.size() < 2 || args.size() > 3) return usage(args.size(), 1, 2, channel);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, true);
        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, e -> e));
        boolean isAA = flags.contains('a'); // args.get(0).equalsIgnoreCase("*") ||


        boolean profit = !flags.contains('p') && unitKill == null && unitLoss == null && attType == null;
        boolean units = !flags.contains('u');
        boolean infra = !flags.contains('i');
        boolean consumption = !flags.contains('c');
        boolean average = !flags.contains('t');
        boolean loot = !flags.contains('l');
        boolean buildings = !flags.contains('b');

        boolean scale = flags.contains('s');

        boolean damage = flags.contains('d');
        boolean net = !damage || flags.contains('n');

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

        String title = (damage && net ? "Net " : "Total ") + (typeName == null ? "" : typeName + " ") + (profit ? damage ? "damage" : "profit" : (unitKill != null ? "kills" : unitLoss != null ? "deaths" : (attType != null) ? "attacks" : "losses")) + " " + (average ? "per" : "of") + " war (%s)";
        title = String.format(title, diffStr);

        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksEither(nationMap.keySet(), start, end);

        GroupedRankBuilder<Integer, AbstractCursor> attackGroup = new RankBuilder<>(attacks)
                .group((attack, map) -> {
                    if (nationMap.containsKey(attack.getAttacker_id())) {
                        map.put(attack.getAttacker_id(), attack);
                    }
                    if (nationMap.containsKey(attack.getDefender_id())) {
                        map.put(attack.getDefender_id(), attack);
                    }
                });

        BiFunction<Boolean, AbstractCursor, Double> valueFunc;
        {
            double[] rssBuffer = ResourceType.getBuffer();
            BiFunction<Boolean, AbstractCursor, Double> getValue = null;
            if (unitKill != null) {
                MilitaryUnit finalUnit = unitKill;
                getValue = (attacker, attack) -> (double) (!attacker ? attack.getAttUnitLosses(finalUnit) : attack.getDefUnitLosses(finalUnit));
            }
            if (unitLoss != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (1)");
                MilitaryUnit finalUnit = unitLoss;
                getValue = (attacker, attack) -> (double) (attacker ? attack.getAttUnitLosses(finalUnit) : attack.getDefUnitLosses(finalUnit));
            }
            if (attType != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (2)");
                AttackType finalAttType = attType;
                getValue = (attacker, attack) -> attack.getAttack_type() == finalAttType ? 1d : 0d;
            }
            if (resourceType != null) {
                if (getValue != null) throw new IllegalArgumentException("Cannot combine multiple type rankings (3)");
                double min = damage ? 0 : Double.NEGATIVE_INFINITY;
                ResourceType finalResourceType = resourceType;
                getValue = (attacker, attack) -> {
                    rssBuffer[finalResourceType.ordinal()] = 0;
                    return Math.max(min, attack.addLosses(rssBuffer, attack.getWar(), attacker, units, infra, consumption, loot, buildings)[finalResourceType.ordinal()]);
                };
            }
            if (getValue == null) {
                getValue = (attacker, attack) -> {
                    if (!damage) {
                        return attack.getLossesConverted(attack.getWar(), attacker, units, infra, consumption, loot, buildings);
                    } else {
                        Arrays.fill(rssBuffer, 0);
                        double total = 0;
                        double[] losses = attack.addLosses(rssBuffer, attack.getWar(), attacker, units, infra, consumption, loot, buildings);
                        for (ResourceType type : ResourceType.values) {
                            double val = losses[type.ordinal()];
                            if (val > 0) total += ResourceType.convertedTotal(type, val);
                        }
                        return total;
                    }
                };
            }
            valueFunc = getValue;
        }

        NumericMappedRankBuilder<Integer, Integer, Double> byNationMap;
        if (!damage) {
            byNationMap = attackGroup.map((i, a) -> a.getWar_id(),
                    // Convert attack to profit value
                    (nationdId, attack) -> {
                        DBNation nation = nationMap.get(nationdId);
                        return nation != null ? scale(nation, sign * valueFunc.apply(attack.getAttacker_id() == nationdId, attack), scale, isAA) : 0;
                    });
        } else {
            byNationMap = attackGroup.map((i, a) -> a.getWar_id(),
                    // Convert attack to profit value
                    (nationdId, attack) -> {
                        DBNation nation = nationMap.get(nationdId);
                        if (nation == null) return 0d;
                        boolean primary = (attack.getAttacker_id() != nationdId) == profit;
                        double total = valueFunc.apply(primary, attack);
                        if (net) {
                            total -= valueFunc.apply(!primary, attack);
                        }
                        return scale(nation, total, scale, isAA);
                    });
        }

        SummedMapRankBuilder<Integer, Number> byNation;
        Map<Integer, Integer> numWarsPerAA;
        if (average) {
            numWarsPerAA = byNationMap.get().values().stream()
                    .flatMap(map -> map.keySet().stream())
                    .collect(Collectors.toMap(Function.identity(), i -> 1, Integer::sum));
            byNation = byNationMap.average();
        } else {
            numWarsPerAA = null;
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
            SummedMapRankBuilder<Integer, Number> byAA = byAAMap.sum();
            if (average) {
                byAA.adapt(new BiFunction<Integer, Number, Number>() {
                    @Override
                    public Number apply(Integer aaId, Number number) {
                        int numWars = numWarsPerAA.getOrDefault(aaId, 1);
                        return number.doubleValue() / numWars;
                    }
                });
            }
            ranks = byAA.sort()
                    // Change key to alliance name
                    .nameKeys(allianceId -> PW.getName(allianceId, true));
        } else {
            // Sort descending
            ranks = byNation
                    .removeIfKey(nationId -> !nationMap.containsKey(nationId))
                    .sort()
                    // Change key to alliance name
                    .nameKeys(nationId -> nationMap.get(nationId).getNation());
        }

        // Embed the rank list
        ranks.build(author, channel, DiscordUtil.trimContent(fullCommandRaw), title, flags.contains('f'));
        return null;
    }
}
package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;
import java.util.function.Predicate;

public class Revenue extends Command {
    public Revenue() {
        super("revenue", "alliancerev", "income", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "revenue [json|city-link|nation-link]";
    }

    @Override
    public String desc() {
        return """
                Get revenue. Use `-bonus` to ignore new nation bonuses
                Add `-b` to exclude nation bonus
                Add `-i` to include inactive/beige/gray/vm/apps
                Equilibrium taxrate is the rate at which raws consumed equals taxed income in value""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }

        boolean force = flags.contains('f');
        boolean bonus = !flags.contains('b');
        for (String next : args) {
            if (next.contains("http") || next.contains("{")) break;
        }

        String content = DiscordUtil.trimContent(fullCommandRaw);
        int jsonStart = content.indexOf('{');
        int jsonEnd = content.lastIndexOf('}');

        boolean showTradeAndMilitary = false;

        StringBuilder response = new StringBuilder();

        Map<DBNation, Map<Integer, JavaCity>> cities = new HashMap<>();
        if (jsonStart != -1) {
            String buildJson = content.substring(jsonStart, jsonEnd + 1);
            CityBuild cityBuild = CityBuild.of(buildJson);
            JavaCity jc = new JavaCity(cityBuild);
            if (cityBuild.getLand() == null) {
                jc.setLand(cityBuild.getInfraNeeded() * 1.5);
            }
            HashMap<Integer, JavaCity> map = new HashMap<>();
            map.put(0, jc);
            cities.put(me, map);
            try {
                jc.validate(me.getContinent(), me::hasProject);
            } catch (IllegalArgumentException ignore) {
                response.append(ignore.getMessage()).append("\n\n");
            }
        } else if (content.contains("/city/id=")) {
            int cityId = Integer.parseInt(content.split("=")[1]);
            Map.Entry<DBNation, JavaCity> jcPair = JavaCity.getOrCreate(cityId, true);
            JavaCity jc = jcPair.getValue();
            DBNation nation = jcPair.getKey();

            HashMap<Integer, JavaCity> map = new HashMap<>();
            map.put(cityId, jc);
            cities.put(nation, map);
        } else {
            showTradeAndMilitary = true;
            Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
            if (!flags.contains('i')) {
                nations.removeIf(f -> f.getAlliance_id() == 0 || f.getVm_turns() != 0);
            }

            if (nations.size() > 1) {
                if (!flags.contains('i')) {
                    nations.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= 1);
                }
            } else {
                force = true;
            }

            if (nations.size() == 0) {
                return "Invalid nation or alliance: `" + args.get(0) + "` (add `-i` if they are inactive/vm/gray/app/beige)";
            } else {
                channel.sendMessage("Fetching cities (please wait)...");
                for (DBNation aaMember : nations) {
                    if (!force && (aaMember.isGray() || aaMember.getVm_turns() != 0)) {
                        continue;
                    }
                    cities.put(aaMember, aaMember.getCityMap(nations.size() == 1));
                }
            }
        }

        if (cities.isEmpty()) {
            return "No cities found" + (force ? "" : " (add -f to inclue gray nations)");
        }

        boolean useCache = cities.size() > 1;

        double[] cityProfit = new double[ResourceType.values.length];
        double[] milUp = new double[ResourceType.values.length];
        long tradeBonus = 0;

        Map<Integer, Integer> treasureByAA = new HashMap<>();
        for (Map.Entry<DBNation, Map<Integer, JavaCity>> entry : cities.entrySet()) {
            DBNation nation = entry.getKey();
            if (nation.getAlliance_id() == 0) continue;
            for (DBTreasure treasure : nation.getTreasures()) {
                treasureByAA.merge(nation.getAlliance_id(), 1, Integer::sum);
            }
        }

        for (Map.Entry<DBNation, Map<Integer, JavaCity>> entry : cities.entrySet()) {
            DBNation nation = entry.getKey();
            int treasures = treasureByAA.getOrDefault(nation.getAlliance_id(), 0);
            Set<DBTreasure> natTreasures = nation.getTreasures();
            double treasureBonus = ((treasures == 0 ? 0 : Math.sqrt(treasures * 4)) + natTreasures.stream().mapToDouble(DBTreasure::getBonus).sum()) * 0.01;

            Predicate<Project> hasProject = nation::hasProject;

            double rads = nation.getRads();

            int numCities = bonus ? nation.getCities() : 10;

            Collection<JavaCity> cityList = entry.getValue().values();

            for (JavaCity build : cityList) {
                cityProfit = build.profit(me.getContinent(), rads, -1L, hasProject, cityProfit, numCities, me.getGrossModifier() + treasureBonus, 12);
            }

            NationColor color = nation.getColor();
            long nationTurnBonus = (long) (color.getTurnBonus() * 12L * me.getGrossModifier());
            tradeBonus += nationTurnBonus;

            if (!nation.hasUnsetMil()) {
                double factor = nation.getMilitaryUpkeepFactor();
                boolean atWar = nation.getNumWars() > 0;

                for (MilitaryUnit unit : MilitaryUnit.values) {
                    int amt = nation.getUnits(unit);
                    if (amt == 0) continue;

                    double[] upkeep = unit.getUpkeep(atWar);
                    for (int i = 0; i < upkeep.length; i++) {
                        double value = upkeep[i];
                        if (value != 0) {
                            milUp[i] -= value * amt * factor;
                        }
                    }
                }
            }
        }

        response.append("Daily city revenue:")
                .append("```").append(PnwUtil.resourcesToString(cityProfit)).append("```");

        if (!showTradeAndMilitary) {
            response.append(String.format("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(cityProfit))));
        }

        if (showTradeAndMilitary) {
            response.append('\n').append("Military upkeep:")
                    .append("```").append(PnwUtil.resourcesToString(milUp)).append("```");

            response.append('\n').append("Color Bonus: ```").append(MathMan.format(tradeBonus)).append("```");

            Map<ResourceType, Double> total = PnwUtil.add(PnwUtil.resourcesToMap(cityProfit), PnwUtil.resourcesToMap(milUp));
            total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + tradeBonus);

            response.append('\n').append("Combined Total:")
                    .append("```").append(PnwUtil.resourcesToString(total)).append("```").append("Converted total: $").append(MathMan.format(PnwUtil.convertedTotal(total)));
        }

        {
            Map<ResourceType, Double> total = PnwUtil.add(PnwUtil.resourcesToMap(cityProfit), PnwUtil.resourcesToMap(milUp));
            double consumeCost = 0;
            double taxable = 0;
            for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                if (entry.getValue() < 0) {
                    consumeCost += Math.abs(PnwUtil.convertedTotal(entry.getKey(), -entry.getValue()));
                } else {
                    taxable += Math.abs(PnwUtil.convertedTotal(entry.getKey(), entry.getValue()));
                }
            }
            if (taxable > consumeCost) {
                double requiredTax = 100 * consumeCost / taxable;
                response.append("\nEquilibrium taxrate: `").append(MathMan.format(requiredTax)).append("%`");
            } else {
                response.append("\n`warn: Revenue is not sustainable.`");
            }
        }

        return response.toString();
    }
}

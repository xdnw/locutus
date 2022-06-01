package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class Revenue extends Command {
    public Revenue() {
        super("revenue", "alliancerev", "income", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }
    @Override
    public String help() {
        return "!revenue [json|city-link|nation-link]";
    }

    @Override
    public String desc() {
        return "Get revenue. Use `-bonus` to ignore new nation bonuses\n" +
                "Add `-b` to exclude nation bonus\n" +
                "Add `-i` to include inactive/beige/gray/vm/apps";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        if (me == null) {
            return "Please use !validate";
        }

        boolean force = flags.contains('f');
        boolean bonus = !flags.contains('b');
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.contains("http") || next.contains("{")) break;
        }

        String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        int jsonStart = content.indexOf('{');
        int jsonEnd = content.lastIndexOf('}');

        boolean showTradeAndMilitary = false;

        StringBuilder response = new StringBuilder();

        Map<DBNation, Map<Integer, JavaCity>> cities = new HashMap<>();
//        Collection<JavaCity> builds = new ArrayList<>();
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
            City city = Locutus.imp().getPnwApi().getCity(cityId);
            JavaCity jc = new JavaCity(city);

            HashMap<Integer, JavaCity> map = new HashMap<>();
            map.put(cityId, jc);
            DBNation nation = Locutus.imp().getNationDB().getNation(Integer.parseInt(city.getNationid()));
            cities.put(nation, map);
        } else {
            showTradeAndMilitary = true;
            Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
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

            if (nations.size() > 250 && !Locutus.imp().getGuildDB(guild).isWhitelisted()) {
                return ">250 nations. Please try using a filter";
            }

            if (nations.size() == 0) {
                return "Invalid nation or alliance: `" + args.get(0) + "` (add `-i` if they are inactive/vm/gray/app/beige)";
            } else {
                event.getChannel().sendMessage("Fetching cities (please wait)...").complete();
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

        for (Map.Entry<DBNation, Map<Integer, JavaCity>> entry : cities.entrySet()) {
            DBNation nation = entry.getKey();

            Predicate<Project> hasProject = new Predicate<Project>() {
                @Override
                public boolean test(Project project) {
                    return nation.hasProject(project);
//                    return project != null && project.get(pnwNation) > 0;
                }
            };

            double rads = nation.getRads();

            int numCities = bonus ? nation.getCities() : 10;

            Collection<JavaCity> cityList = entry.getValue().values();

            for (JavaCity build : cityList) {
                cityProfit = build.profit(rads, hasProject, cityProfit, numCities);
            }

            NationColor color = nation.getColor();
            tradeBonus = Locutus.imp().getTradeManager().getTradeBonus(color) * 12;

            if (!nation.hasUnsetMil()) {
                boolean war = nation.getOff() > 0 || nation.getDef() > 0;

                for (MilitaryUnit unit : MilitaryUnit.values) {
                    int amt = nation.getUnits(unit);
                    if (amt == 0) continue;

                    double[] upkeep = unit.getUpkeep(war);
                    for (int i = 0; i < upkeep.length; i++) {
                        double value = upkeep[i];
                        if (value != 0) {
                            milUp[i] -= value * amt;
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

            response.append('\n').append("Trade bonus: ```" + tradeBonus + "```");

            Map<ResourceType, Double> total = PnwUtil.add(PnwUtil.resourcesToMap(cityProfit), PnwUtil.resourcesToMap(milUp));
            total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + tradeBonus);

            response.append('\n').append("Combined Total:")
                    .append("```").append(PnwUtil.resourcesToString(total)).append("```")
                    .append("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(total)));
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
                response.append("\nEquilibrium taxrate: `" + MathMan.format(requiredTax) + "%`");
            } else {
                response.append("\n`warn: Revenue is not sustainable`");
            }
        }

        return response.toString();
    }
}

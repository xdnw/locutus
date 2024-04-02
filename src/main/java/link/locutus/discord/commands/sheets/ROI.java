package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ROI extends Command {
    public ROI() {
        super("roi", "ReturnOnInvestment", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + getClass().getSimpleName() + " <alliance|nation|tax-url|*> [days]";
    }

    @Override
    public String desc() {
        return "Find the ROI for various changes you can make to your nation, with a specified timeframe\n" +
                "(typically how long you expect the changes, or peacetime to last)\n" +
                "e.g. `" + Settings.commandPrefix(true) + "DebugROI @Borg 30`\n" +
                "Add `-r` to run it recursively for various infra levels";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    public static class ROIResult {
        private final double netProfit;
        private final double profit;
        public DBNation nation;
        public Investment investment;
        public Object info;
        public double roi;
        public Map<ResourceType, Double> cost;
        public double costConverted;

        public ROIResult(DBNation nation, Investment investment, Object info, double roi, Map<ResourceType, Double> cost, double netProfit, double profit) {
            this.nation = nation;
            this.investment = investment;
            this.info = info;
            this.roi = roi;
            this.cost = cost;
            this.costConverted = ResourceType.convertedTotal(cost);
            this.netProfit = netProfit;
            this.profit = profit;
        }

        public double getNetProfit() {
            return netProfit;
        }

        public double getProfit() {
            return profit;
        }

        public DBNation getNation() {
            return nation;
        }

        public Investment getInvestment() {
            return investment;
        }

        public Object getInfo() {
            return info;
        }

        public double getRoi() {
            return roi;
        }

        public Map<ResourceType, Double> getCost() {
            return cost;
        }

        public double getCostConverted() {
            return costConverted;
        }

        @Override
        public String toString() {
            return "ROIResult{" +
                    "netProfit=" + netProfit +
                    ", profit=" + profit +
                    ", nation=" + nation +
                    ", investment=" + investment +
                    ", info=" + info +
                    ", roi=" + roi +
                    ", cost=" + cost +
                    ", costConverted=" + costConverted +
                    '}';
        }
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }

        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        if (guildDb == null || !guildDb.hasAlliance()) {
            return "Invalid guild. Please register your alliance id with: " + GuildKey.ALLIANCE_ID.getCommandMention() + "";
        }

        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Fetching nations: ");

        Set<Integer> aaIds = guildDb.getAllianceIds();
        if (aaIds.isEmpty()) return "Please use " + GuildKey.ALLIANCE_ID.getCommandMention() + "";

        List<ROIResult> roiMap = new ArrayList<>();
        boolean useSheet = false;

        if (args.get(0).toLowerCase().equals("*")) {
            if (!Roles.ECON.has(author, guildDb.getGuild())) {
                return "You do not have the role: " + Roles.ECON;
            }
            Set<DBNation> nations = Locutus.imp().getNationDB().getNations(aaIds);
            try {
                for (DBNation nation : nations) {
                    if (nation.getPosition() <= 1) continue;
                    if (nation.active_m() > TimeUnit.DAYS.toMinutes(7)) continue;
                    IMessageBuilder msg = msgFuture.get();
                    if (msg != null && msg.getId() > 0) {
                        msg.clear().append("Calculating ROI for: " + nation.getNation()).sendIfFree();
                    }
                    roi(nation, days, roiMap);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            useSheet = nations.size() > 1;
        } else if (args.get(0).toLowerCase().contains("/city/")) {
            int cityId = Integer.parseInt(args.get(0).split("=")[1]);
            DBCity cityEntry = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId);
            int nationId = cityEntry.getNationId();
            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
            cityEntry.update(true);
            JavaCity from = cityEntry.toJavaCity(nation);
            roi(nation, Integer.MAX_VALUE, Integer.MAX_VALUE, from, days, roiMap, 500);
        } else {
            Collection<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
            nations.removeIf(n -> n.active_m() > 10000 || n.getVm_turns() > 0);
            if (nations.size() > 1 && !Roles.ADMIN.hasOnRoot(author)) {
                nations.removeIf(n -> !aaIds.contains(n.getAlliance_id()));
                if (nations.isEmpty()) {
                    return "You are only allowed to find grants for other alliances";
                }
            }
            if (nations.isEmpty()) {
                return "No nations found";
            }
//            for (DBNation nation : nations) {
////                if (nation.getAlliance_id() != allianceId) return "Invalid alliance for: " + nation.getNation();
//            }
            useSheet = nations.size() > 1;
            for (DBNation nation : nations) {
                try {
                    IMessageBuilder msg = msgFuture.get();
                    if (msg != null && msg.getId() > 0) {
                        msg.clear().append("Calculating ROI for: " + nation.getNation()).sendIfFree();
                    }
                    roi(nation, days, roiMap);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        IMessageBuilder msg = msgFuture.get();
        if (msg != null && msg.getId() > 0) {
            channel.delete(msg.getId());
        }
        msg = channel.create().append("Results:");

        roiMap.removeIf(roi -> roi.roi <= 0);

        Collections.sort(roiMap, new Comparator<ROIResult>() {
            @Override
            public int compare(ROIResult o1, ROIResult o2) {
                return Investment.compareProfit(o1, o2);
            }
        });

        me.setMeta(NationMeta.INTERVIEW_ROI, (byte) 1);

        if (roiMap.isEmpty()) {
            return "No ROI can be recovered over the specified timeframe.";
        }

        if (roiMap.size() > 1 && useSheet) {
            SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKey.ROI_SHEET);
            ArrayList<Object> formulas = new ArrayList<>(Arrays.asList(new Object[19]));
            for (int i = 0; i < 19; i++) formulas.set(i, "");
            formulas.set(15, "TOTALS:");
            formulas.set(16, "=SUMIF(A3:A,unique($A$3:A3),Q3:Q)");
            formulas.set(17, "=SUMIF(A3:A,unique($A$3:A3),R3:R)");
            sheet.addRow(formulas);

            List<String> header = new ArrayList<>(Arrays.asList(
                    "Nation",
                    "Deposit",
                    "CityTurns",
                    "ProjectTurns",
                    "Cities",
                    "AvgInfra",
                    "Position",
                    "Days old",
//                    "Seniority",
                    "Score",
                    "Off",
                    "Def",
                    "LastDefWar",
                    "Policy",
                    "INVESTMENT",
                    "INFO",
                    "ROI",
                    "PROFIT/DAY",
                    "COST",
                    "RAWS"

            ));
            sheet.setHeader(header);
            for (ROIResult result : roiMap) {
                DBNation nation = result.nation;
                Map<ResourceType, Double> deposits = ResourceType.resourcesToMap(nation.getNetDeposits(guildDb, false));
                double depositsConverted = ResourceType.convertedTotal(deposits);

                header.clear();
                header.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
                header.add(String.format("%.2f", depositsConverted));
                header.add("" + nation.getCityTurns());
                header.add("" + nation.getProjectTurns());
                header.add("" + nation.getCities());
                header.add("" + nation.getAvg_infra());
                header.add("" + nation.getPosition());
                header.add("" + nation.getAgeDays());

                header.add("" + nation.getScore());
                header.add("" + nation.getOff());
                header.add("" + nation.getDef());

                List<DBWar> wars = new ArrayList<>(Locutus.imp().getWarDb().getWarsForNationOrAlliance(f -> f == nation.getNation_id(), null, f -> f.getDefender_id() == nation.getNation_id()).values());
                long lastWar = 0;
                if (!wars.isEmpty()) {
                    wars.sort((o1, o2) -> Integer.compare(o2.warId, o1.warId));
                    for (DBWar war : wars) {
                        lastWar = Math.max(lastWar, war.getDate());
                    }
                }
                if (lastWar != 0) {
                    long lastWarDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastWar);
                    header.add("" + lastWarDays);
                } else {
                    header.add("" + nation.getAgeDays());
                }

                header.add(nation.getDomesticPolicy().name());

                header.add(result.investment.name());
                header.add(result.info.toString());
                header.add(String.format("%.2f", result.roi));
                header.add(String.format("%.2f", result.profit));
                header.add(String.format("%.2f", result.costConverted));
                header.add(ResourceType.resourcesToString(result.cost));

                sheet.addRow(header);
            }
            try {
                sheet.updateClearCurrentTab();
                sheet.updateWrite();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            sheet.attach(channel.create(), "roi").send();
            return null;
        } else {
            StringBuilder output = new StringBuilder("Weekly ROI (" + days + " days):\n");
            List<DBNation> nations = new ArrayList<>();
            for (ROIResult result : roiMap) {
                nations.add(result.nation);
                output.append(result.nation.getNation() + " | " + result.nation.getUrl()).append("\n");
                output.append(result.investment + " @ `" + result.info + "`: " + MathMan.format(result.roi) + "%");
                if (result.investment == Investment.RESOURCE_PROJECT) output.append(", Profit/Day: $" + MathMan.format(result.profit));
                        output.append("\n\n");
            }

            DBNation nation = nations.get(0);

            Set<Project> projects = nation.getProjects();
            Map<ResourceBuilding, Boolean> manufacturing = new LinkedHashMap();
            for (Map.Entry<Integer, JavaCity> entry : nation.getCityMap(false).entrySet()) {
                JavaCity city = entry.getValue();
                if (city.get(Buildings.GAS_REFINERY) > 0 && !projects.contains(Projects.EMERGENCY_GASOLINE_RESERVE))
                    manufacturing.put(Buildings.GAS_REFINERY, true);
                if (city.get(Buildings.STEEL_MILL) > 0 && !projects.contains(Projects.IRON_WORKS))
                    manufacturing.put(Buildings.STEEL_MILL, true);
                if (city.get(Buildings.ALUMINUM_REFINERY) > 0 && !projects.contains(Projects.BAUXITEWORKS))
                    manufacturing.put(Buildings.ALUMINUM_REFINERY, true);
                if (city.get(Buildings.MUNITIONS_FACTORY) > 0 && !projects.contains(Projects.ARMS_STOCKPILE))
                    manufacturing.put(Buildings.MUNITIONS_FACTORY, true);
            }
            if (!manufacturing.isEmpty()) {
                output.append("Currently producing without project:");
                for (Map.Entry<ResourceBuilding, Boolean> entry : manufacturing.entrySet()) {
                    ResourceBuilding building = entry.getKey();
                    output.append("\n- " + building.getResourceProduced().name());
                }
            }

            msg.append("\n" + output.toString()).send();
            return null;
        }
    }

    public static void roi(DBNation nation, int days, List<ROIResult> roiMap) throws InterruptedException, ExecutionException, IOException {
        Map<Integer, JavaCity> cities = nation.getCityMap(false);
        JavaCity existingCity = new JavaCity(cities.values().iterator().next());

        roi(nation, nation.getCityTurns(), nation.getProjectTurns(), existingCity, days, roiMap, 500);
    }

    public static void roi(DBNation nation, long cityTurns, long projectTurns2, JavaCity existingCity, int days, List<ROIResult> roiMap, int timeout) throws IOException {
        double radIndex = nation.getRads();
        double rads = (1 + (radIndex / (-1000)));

        int numCities = nation.getCities();
        double baseProfit = existingCity.profitConvertedCached(nation.getContinent(), rads, p -> p.get(nation) > 0, numCities, nation.getGrossModifier());

        Predicate<Project> hasProjects = p -> p.get(nation) > 0;

        {
            JavaCity optimal = existingCity.roiBuild(nation.getContinent(), rads, numCities, hasProjects, nation.getGrossModifier(), days, timeout);
            if (optimal != null) {
                double baseOptimizedProfit = optimal.profitConvertedCached(nation.getContinent(), rads, hasProjects, numCities, nation.getGrossModifier());
                if (baseOptimizedProfit > baseProfit) {
                    Map<ResourceType, Double> cost = ResourceType.resourcesToMap(optimal.calculateCost(existingCity, new double[ResourceType.values.length]));
                    double costConverted = ResourceType.convertedTotal(cost);
                    double profit = (baseOptimizedProfit - baseProfit);
                    double netProfit = profit * days - costConverted;
                    if (netProfit > 0 && !optimal.equals(existingCity)) {
                        double roi = ((netProfit / costConverted) * 100 * 7) / days;

                        roiMap.add(new ROIResult(nation, Investment.BUILD, Settings.commandPrefix(true) + "OptimalBuild " + days + " <city-url>", roi, cost, netProfit, profit));
                        baseProfit = baseOptimizedProfit;
                    }
                }
            }
        }

        if (projectTurns2 <= 0) {
            for (Building building : Buildings.values()) {
                if (!(building instanceof ResourceBuilding)) continue;
                ResourceType type = ((ResourceBuilding) building).getResourceProduced();
                Project project = type.getProject();
                if (type == ResourceType.URANIUM && !Buildings.URANIUM_MINE.canBuild(nation.getContinent())) continue;
                if (project == null) continue;
                if (nation.hasProject(project)) continue;

                Predicate<Project> hasProjectsProxy = new Predicate<Project>() {
                    @Override
                    public boolean test(Project p) {
                        return project == p || p.get(nation) > 0;
                    }
                };

                JavaCity optimal = existingCity.roiBuild(nation.getContinent(), rads, numCities, hasProjectsProxy, nation.getGrossModifier(), days, timeout);
                if (optimal != null) {
                    double profit = optimal.profitConvertedCached(nation.getContinent(), rads, hasProjectsProxy, numCities, nation.getGrossModifier());
                    Map<ResourceType, Double> cost = ResourceType.add(
                            project.cost(),
                            PW.multiply(
                                    ResourceType.resourcesToMap(optimal.calculateCost(existingCity)),
                                    (double) numCities)
                    );
                    double costConverted = ResourceType.convertedTotal(cost);
                    double profitOverBase = (profit - baseProfit);
                    double netProfit = profitOverBase * numCities * days - costConverted;
                    double roi = ((netProfit / costConverted) * 100 * 7) / days;

                    roiMap.add(new ROIResult(nation, Investment.RESOURCE_PROJECT, type, roi, cost, netProfit, profitOverBase * numCities));
                }
            }
            Project[] projects = new Project[]{
                    Projects.GREEN_TECHNOLOGIES,
                    Projects.INTERNATIONAL_TRADE_CENTER,
                    Projects.RECYCLING_INITIATIVE,
            };
            for (Project project : projects) {
                Predicate<Project> hasProjectsProxy = new Predicate<Project>() {
                    @Override
                    public boolean test(Project p) {
                        return project == p || p.get(nation) > 0;
                    }
                };
                JavaCity optimal = existingCity.roiBuild(nation.getContinent(), rads, numCities, hasProjectsProxy, nation.getGrossModifier(), days, timeout);
                if (optimal != null) {
                    double profit = optimal.profitConvertedCached(nation.getContinent(), rads, hasProjectsProxy, numCities, nation.getGrossModifier());
                    Map<ResourceType, Double> cost = ResourceType.add(
                            project.cost(),
                            PW.multiply(
                                    ResourceType.resourcesToMap(optimal.calculateCost(existingCity)),
                                    (double) numCities)
                    );
                    double costConverted = ResourceType.convertedTotal(cost);
                    double profitOverBase = (profit - baseProfit);
                    double netProfit = profitOverBase * numCities * days - costConverted;
                    double roi = ((netProfit / costConverted) * 100 * 7) / days;

                    roiMap.add(new ROIResult(nation, Investment.RESOURCE_PROJECT, project.name(), roi, cost, netProfit, profitOverBase * numCities));
                }
            }
        }
        if (cityTurns <= 0) {
            boolean manifest = nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
            boolean cityPlanning = Projects.URBAN_PLANNING.get(nation) > 0;
            boolean advCityPlanning = Projects.ADVANCED_URBAN_PLANNING.get(nation) > 0;
            boolean metroPlanning = Projects.METROPOLITAN_PLANNING.get(nation) > 0;

            boolean getCityPlanning = numCities >= Projects.URBAN_PLANNING.requiredCities() && !cityPlanning;
            boolean getAdvCityPlanning = numCities >= Projects.ADVANCED_URBAN_PLANNING.requiredCities() && !advCityPlanning;
            boolean getMetroPlanning = numCities >= Projects.METROPOLITAN_PLANNING.requiredCities() && !advCityPlanning;
            boolean gsa = nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY);

            double cityCost = PW.City.nextCityCost(numCities, manifest || true, cityPlanning || getCityPlanning, advCityPlanning || getAdvCityPlanning, metroPlanning || getMetroPlanning, gsa);
            double[] buildCost = existingCity.calculateCost(new JavaCity());
            double[] totalCost = buildCost.clone();

            totalCost[ResourceType.MONEY.ordinal()] += cityCost;
            double costConverted = ResourceType.convertedTotal(totalCost);
            double netProfit = baseProfit * days - costConverted;
            double roi = ((netProfit / costConverted) * 100 / days) * 7;

            if (getCityPlanning) {
                roiMap.add(new ROIResult(nation, Investment.CITY_PROJECT, "CITY_PLANNING", roi, ResourceType.resourcesToMap(totalCost), netProfit, baseProfit));
            } else if (getAdvCityPlanning) {
                roiMap.add(new ROIResult(nation, Investment.CITY_PROJECT, "ADVANCED_CITY_PLANNING", roi, ResourceType.resourcesToMap(totalCost), netProfit, baseProfit));
            } else {
                roiMap.add(new ROIResult(nation, Investment.CITY, (numCities + 1), roi, ResourceType.resourcesToMap(totalCost), netProfit, baseProfit));
            }
        }

        {
            JavaCity withInfra = new JavaCity(existingCity).setInfra(existingCity.getInfra() + Math.max(100, 1500 - existingCity.getInfra()));
            withInfra = withInfra.roiBuild(nation.getContinent(), rads, numCities, hasProjects, nation.getGrossModifier(), days, timeout);
            if (withInfra != null) {
                double profit = withInfra.profitConvertedCached(nation.getContinent(), rads, hasProjects, numCities, nation.getGrossModifier());
                double[] cost = withInfra.calculateCost(existingCity);
                cost[ResourceType.MONEY.ordinal()] += PW.City.Infra.calculateInfra((int) existingCity.getInfra(), (int) withInfra.getInfra());
                double costConverted = ResourceType.convertedTotal(cost);
                double profitOverBase = (profit - baseProfit);
                double netProfit = profitOverBase * days - costConverted;
                double roi = ((netProfit / costConverted) * 100 * 7) / days;

                roiMap.add(new ROIResult(nation, Investment.INFRA, withInfra.getInfra(), roi, ResourceType.resourcesToMap(cost), netProfit, profitOverBase));
            }
        }

        {
            JavaCity withLand = new JavaCity(existingCity).setLand(existingCity.getLand() + 500);
            withLand = withLand.roiBuild(nation.getContinent(), rads, numCities, hasProjects, nation.getGrossModifier(), days, timeout);
            if (withLand != null) {
                double profit = withLand.profitConvertedCached(nation.getContinent(), rads, hasProjects, numCities, nation.getGrossModifier());
                double[] cost = withLand.calculateCost(existingCity);
                cost[ResourceType.MONEY.ordinal()] += (PW.City.Land.calculateLand(existingCity.getLand(), withLand.getLand()));
                double costConverted = ResourceType.convertedTotal(cost);
                double profitOverBase = (profit - baseProfit);
                double netProfit = profitOverBase * days - costConverted;
                double roi = ((netProfit / costConverted) * 100 * 7) / days;

                roiMap.add(new ROIResult(nation, Investment.LAND, withLand.getLand(), roi, ResourceType.resourcesToMap(cost), netProfit, profitOverBase));
            }
        }
    }

    public enum Investment {
        BUILD(0.75),
        CITY(1),
        RESOURCE_PROJECT(0.75, false),
        INFRA(0.5),
        LAND(0.9),
        CITY_PROJECT(0.1),

        ;

        private final double factor;
        private final boolean sortByROI;

        Investment(double factor) {
            this(factor, true);
        }

        Investment(double factor, boolean sortByROI) {
            this.factor = factor;
            this.sortByROI = sortByROI;
        }

        public static int compare(ROIResult a, ROIResult b) {
            if (a.investment.sortByROI && b.investment.sortByROI) {
                return Double.compare(b.roi * b.investment.factor, a.roi * a.investment.factor);
            }
            return Double.compare(b.profit * b.investment.factor, a.profit * a.investment.factor);
        }

        public static int compareProfit(ROIResult a, ROIResult b) {
            return Double.compare(b.profit * b.investment.factor, a.profit * a.investment.factor);
        }

        public static int compareRoi(ROIResult a, ROIResult b) {
            return Double.compare(b.roi * b.investment.factor, a.roi * a.investment.factor);
        }

    }
}

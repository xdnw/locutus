package link.locutus.discord.commands.info.optimal;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import rocker.grant.city;
import rocker.grant.nation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class OptimalBuild extends Command {
    public OptimalBuild() {
        super("OptimalBuild", "GenerateBuild", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "OptimalBuild [days] <json|city-url>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String desc() {
        return "Optimize a build for a city e.g.\n" +
                "For 30 days:" +
                " - `" + Settings.commandPrefix(true) + "OptimalBuild 30 " + Settings.INSTANCE.PNW_URL() + "/city/id=XXXX`\n" +
                "For an indefinite time span:\n" +
                " - `" + Settings.commandPrefix(true) + "OptimalBuild " + Settings.INSTANCE.PNW_URL() + "/city/id=XXXX`\n" +
                "To specify an MMR, add e.g. `mmr=5050`\n" +
                "To specify a continent, add e.g. `continent=australia`\n" +
                "To specify an age (int days), add e.g. `age=150`\n" +
                "To specify infra, add e.g. `infra=1.5k`\n" +
                "To specify land, add e.g. `land=1000`\n" +
                "To cap disease add e.g. `disease<10`\n" +
                "To cap crime add e.g. `crime<10`\n" +
                "To require pop add e.g. `pop>100k`\n" +
                "To check revenue at a damaged infra level use e.g. `infralow=800`\n" +
                "To check pop at this damaged infra level use e.g. `poplow>90k`\n" +
                "To require cash positive add `cash=true`\n" +
                "To avoid importing raws, use. `manu=false`\n" +
                "For radiation. `radiation=123`\n" +
                "To specify a tax rate, use `tax=25/25`" +
                "With an exported build:\n" +
                "```" + Settings.commandPrefix(true) + "OptimalBuild 30 {\n" +
                "    \"infra_needed\": 3850,\n" +
                "    \"imp_total\": 77,\n" +
                "    \"imp_coalpower\": 0,\n" +
                "    \"imp_oilpower\": 0,\n" +
                "    \"imp_windpower\": 0,\n" +
                "    \"imp_nuclearpower\": 2,\n" +
                "    \"imp_coalmine\": 0,\n" +
                "    \"imp_oilwell\": 0,\n" +
                "    \"imp_uramine\": 0,\n" +
                "    \"imp_leadmine\": 0,\n" +
                "    \"imp_ironmine\": 0,\n" +
                "    \"imp_bauxitemine\": 0,\n" +
                "    \"imp_farm\": 20,\n" +
                "    \"imp_gasrefinery\": 0,\n" +
                "    \"imp_aluminumrefinery\": 5,\n" +
                "    \"imp_munitionsfactory\": 0,\n" +
                "    \"imp_steelmill\": 5,\n" +
                "    \"imp_policestation\": 2,\n" +
                "    \"imp_hospital\": 5,\n" +
                "    \"imp_recyclingcenter\": 3,\n" +
                "    \"imp_subway\": 1,\n" +
                "    \"imp_supermarket\": 4,\n" +
                "    \"imp_bank\": 5,\n" +
                "    \"imp_mall\": 4,\n" +
                "    \"imp_stadium\": 3,\n" +
                "    \"imp_barracks\": 5,\n" +
                "    \"imp_factory\": 5,\n" +
                "    \"imp_hangars\": 5,\n" +
                "    \"imp_drydock\": 3,\n" +
                "\t\"land\": 10000\n" +
                "}```\n" +
                "Add `-p` to print plaintext result";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        DiscordChannelIO io = new DiscordChannelIO(event);
        return onCommand(io, guild, author, me, args, flags);
    }

    public String onCommand(IMessageIO io, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }
        Integer days = null;
        if (args.size() >= 2 && MathMan.isInteger(args.get(0))) {
            days = Integer.parseInt(args.get(0));
        }
        JavaCity origin;

        Set<Project> addProject = new HashSet<>();

        Boolean positiceCash = false;
        Boolean manu = true;
        String mmr = null;

        Double crimeLimit = null;
        Double diseaseLimit = null;
        Double popLimit = null;
        Double infraLow = null;
        Double popLow = null;
        Double infra = null;
        Double land = null;
        Integer steel = null;
        Integer munitions = null;
        Integer gasoline = null;
        Integer aluminum = null;
        TaxRate taxes = null;
        Map<ResourceType, Double> overrideResourcePrice = null;

        Integer age = null;
        Continent continent = null;
        double baseRadiation = -1;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next().toLowerCase();
            Project project = Projects.get(next);
            if (project != null) {
                addProject.add(project);
                iter.remove();
                continue;
            }

            String[] split = next.split("[=<>]");
            if (split.length == 2) {
                switch (split[0].toLowerCase()) {
                    case "mmr":
                        String mmrStr = split[1];
                        if (MathMan.isInteger(mmrStr) && mmrStr.length() == 4) {
                            mmr = mmrStr;
                            iter.remove();
                        }
                        break;
                    case "radiation":
                        baseRadiation = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "steel":
                        steel = MathMan.parseInt(split[1]);
                        iter.remove();
                        break;
                    case "gas":
                    case "gasoline":
                        gasoline = MathMan.parseInt(split[1]);
                        iter.remove();
                        break;
                    case "muni":
                    case "munitions":
                        munitions = MathMan.parseInt(split[1]);
                        iter.remove();
                        break;
                    case "alu":
                    case "aluminium":
                    case "aluminum":
                        aluminum = MathMan.parseInt(split[1]);
                        iter.remove();
                        break;
                    case "infrastructure":
                    case "infra":
                        infra = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "infralow":
                        infraLow = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "crime":
                        crimeLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "pop":
                    case "population":
                        popLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "poplow":
                    case "populationlow":
                        popLow = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "disease":
                        diseaseLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "land":
                        land = MathMan.parseDouble(split[1]);
                        iter.remove();
                        break;
                    case "age":
                        age = MathMan.parseInt(split[1]);
                        iter.remove();
                        break;
                    case "continent":
                        continent = Continent.valueOf(split[1].toUpperCase());
                        iter.remove();
                        break;
                    case "manufacturing":
                    case "manufactured":
                    case "manu":
                        manu = Boolean.parseBoolean(split[1]);
                        iter.remove();
                        break;
                    case "cash":
                        positiceCash = Boolean.parseBoolean(split[1]);
                        iter.remove();
                        break;
                    case "tax":
                    case "taxes":
                    case "taxrate":
                        taxes = new TaxRate(split[1]);
                        iter.remove();
                        break;
                }
            }
        }
        if (args.isEmpty()) {
            return usage(null, io);
        }

        Integer cityId = null;
        if (args.size() <= 3 && args.get(args.size() - 1).contains("/city/")) {
            String cityArg = args.size() == 2 ? args.get(1) : args.get(0);
            if (!cityArg.toLowerCase().contains("/city/")) {
                return "Invalid city url: " + cityArg;
            }
            cityId = Integer.parseInt(cityArg.split("=")[1]);
            City pnwCity = Locutus.imp().getPnwApi().getCity(cityId);
            me = Locutus.imp().getNationDB().getNation(Integer.parseInt(pnwCity.getNationid()));
            origin = new JavaCity(pnwCity);

            checkup(io, me, cityId, origin); // show help

            if (days == null) {
                origin.zeroNonMilitary();
            }
        } else {
            String content = args.get(0);
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            if (jsonStart == -1 && jsonEnd == -1) {
                if (content.contains("{")) {
                    return "Invalid city export json: ```" + content + "```";
                } else if (args.size() >= 2 && MathMan.isInteger(args.get(1))) {
                    return "Did you mean: `" + Settings.commandPrefix(true) + "OptimalBuild " + args.get(1) + " " + args.get(0) + "` ?";
                } else {
                    return usage(null, io);
                }
            }

            String buildJson = content.substring(jsonStart, jsonEnd + 1);
            CityBuild build = CityBuild.of(buildJson);
            origin = new JavaCity(build);
        }

        if (mmr != null) {
            origin.set(Buildings.BARRACKS, mmr.charAt(0) - '0');
            origin.set(Buildings.FACTORY, mmr.charAt(1) - '0');
            origin.set(Buildings.HANGAR, mmr.charAt(2) - '0');
            origin.set(Buildings.DRYDOCK, mmr.charAt(3) - '0');
        }
        if (steel != null) origin.set(Buildings.STEEL_MILL, steel);
        if (gasoline != null) origin.set(Buildings.GAS_REFINERY, gasoline);
        if (munitions != null) origin.set(Buildings.MUNITIONS_FACTORY, munitions);
        if (aluminum != null) origin.set(Buildings.ALUMINUM_REFINERY, aluminum);
        if (!manu && steel != null) origin.set(Buildings.COAL_MINE, steel);
        if (!manu && steel != null) origin.set(Buildings.IRON_MINE, steel);
        if (!manu && gasoline != null) origin.set(Buildings.OIL_WELL, gasoline);
        if (!manu && munitions != null) origin.set(Buildings.LEAD_MINE, munitions);
        if (!manu && aluminum != null) origin.set(Buildings.BAUXITE_MINE, aluminum);

        if (infra != null) origin.setInfra(infra);
        if (land != null) origin.setLand(land);
        if (age != null) origin.setAge(age);
        if (continent == null) continent = me.getContinent();

        if (baseRadiation == -1) {
            baseRadiation = Locutus.imp().getTradeManager().getGlobalRadiation(continent) + Locutus.imp().getTradeManager().getGlobalRadiation();
        }

        if (origin.getInfra() > 3600) {
            return "Too much infra";
        }

        double radIndex = baseRadiation;
        double radsFactor = continent == Continent.ANTARCTICA ? 0.5 : 1;
        double rads = (1 + (radIndex / (-1000))) * radsFactor;

        int numCities = me.getCities();

        DBNation finalMe = me;
        Continent finalContinent = continent;
        Predicate<Project> hasProject = project -> addProject.contains(project) || project.get(finalMe) > 0;
        double grossModifier = finalMe.getGrossModifier();;

        CompletableFuture<IMessageBuilder> future = io.send("Please wait...");

        Function<JavaCity, Double> valueFunc;
        if (taxes != null) {
            double[] buffer = new double[ResourceType.values.length];
            double moneyFactor = (100 - taxes.money) / 100d;
            double rssFactor = (100 - taxes.resources) / 100d;
            valueFunc = new Function<JavaCity, Double>() {
                @Override
                public Double apply(JavaCity javaCity) {
                    Arrays.fill(buffer, 0);
                    double[] profit = javaCity.profit(finalContinent, rads, -1L, hasProject, buffer, numCities, grossModifier, 12);
                    profit[0] *= moneyFactor;
                    for (int i = 1; i < profit.length; i++) {
                        if (profit[i] > 0) {
                            profit[i] *= rssFactor;
                        }
                    }
                    return PnwUtil.convertedTotal(profit) / javaCity.getImpTotal();
                }
            };
        } else {
            valueFunc = javaCity -> {
                return javaCity.profitConvertedCached(finalContinent, rads, hasProject, numCities, finalMe.getGrossModifier()) / javaCity.getImpTotal();
            };
        }

        if (infraLow != null) {
            Function<JavaCity, Double> parent = valueFunc;
            Double finalInfraLow = infraLow;
            Double popLowFinal = popLow;
            valueFunc = city -> {
                if (city.getFreeSlots() <= 0) {
                    double currentInfra = city.getInfra();
                    city.setInfra(Math.min(currentInfra, finalInfraLow));
                    city.getMetrics(hasProject).recalculate(city, hasProject);
                    if (popLowFinal != null && city.getPopulation(hasProject) < popLowFinal) return Double.NEGATIVE_INFINITY;
                    Double value = parent.apply(city);
                    city.setInfra(currentInfra);
                    return value;
                }
                return parent.apply(city);
            };
        }

        if (popLimit != null) {
            Double finalpopLimit = popLimit;

            Function<JavaCity, Double> parent = valueFunc;
            valueFunc = city -> {
                if (city.getPopulation(hasProject) < finalpopLimit) return Double.NEGATIVE_INFINITY;
                return parent.apply(city);
            };
        }

        if (popLow != null) {
            Double finalpopLimit = popLow;

            Function<JavaCity, Double> parent = valueFunc;
            valueFunc = city -> {
                if (city.getPopulation(hasProject) < finalpopLimit) return Double.NEGATIVE_INFINITY;
                return parent.apply(city);
            };
        }

        if (!manu) {
            Function<JavaCity, Double> parent = valueFunc;
            valueFunc = city -> {
                if (city.get(Buildings.MUNITIONS_FACTORY) > city.get(Buildings.LEAD_MINE)) return Double.NEGATIVE_INFINITY;
                if (city.get(Buildings.GAS_REFINERY) > city.get(Buildings.OIL_WELL)) return Double.NEGATIVE_INFINITY;
                if (city.get(Buildings.ALUMINUM_REFINERY) > city.get(Buildings.BAUXITE_MINE)) return Double.NEGATIVE_INFINITY;
                if (city.get(Buildings.STEEL_MILL) > city.get(Buildings.COAL_MINE) || city.get(Buildings.STEEL_MILL) > city.get(Buildings.IRON_MINE)) return Double.NEGATIVE_INFINITY;
                return parent.apply(city);
            };
        }

        Function<JavaCity, Double> finalValueFunc = valueFunc;
        Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc = f -> finalValueFunc;

        Function<JavaCity, Boolean> goal = javaCity -> javaCity.getFreeInfra() < 50;

        if (diseaseLimit != null) {
            Double finalDiseaseLimit = diseaseLimit;

            double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
            double recyclingPct = (-Buildings.RECYCLING_CENTER.pollution(hasProject)) / 20d;
            double subwayPct = (-Buildings.SUBWAY.pollution(hasProject)) / 20d;


            Function<JavaCity, Double> parent = valueFunc;
            valueFunc = city -> {
                Double disease = city.getDisease(hasProject);
                if (disease > finalDiseaseLimit) {
                    int remainingSlots = city.getFreeSlots();
                    if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;
                    int latestBuilding = 0;
                    for (int j = Buildings.SUBWAY.ordinal(); j < city.getBuildings().length; j++) {
                        if (city.get(j) > 0) latestBuilding = j;
                    }
                    double reduced = disease;

                    if (latestBuilding <= Buildings.RECYCLING_CENTER.ordinal()) {
                        int amt = Math.min(Buildings.RECYCLING_CENTER.cap(hasProject), remainingSlots);
                        reduced -= recyclingPct * amt;
                        if (reduced <= finalDiseaseLimit) return parent.apply(city);
                        remainingSlots -= amt;
                        if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;
                    }

                    if (latestBuilding <= Buildings.HOSPITAL.ordinal()) {
                        int amt = Math.min(Buildings.HOSPITAL.cap(hasProject), remainingSlots);
                        reduced -= hospitalPct * amt;
                        if (reduced <= finalDiseaseLimit) return parent.apply(city);
                        remainingSlots -= amt;
                        if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;
                    }

                    if (latestBuilding <= Buildings.RECYCLING_CENTER.ordinal()) {
                        int amt = Math.min(Buildings.SUBWAY.cap(hasProject), remainingSlots);
                        reduced -= subwayPct * amt;
                        if (reduced <= finalDiseaseLimit) return parent.apply(city);
                    }

                    return Double.NEGATIVE_INFINITY;
                }
                return parent.apply(city);
            };
        }

        if (crimeLimit != null) {
            double policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;
            int max = Buildings.POLICE_STATION.cap(hasProject);
            Double finalCrimeLimit = crimeLimit;

            Function<JavaCity, Double> parent = valueFunc;
            valueFunc = city -> {
                double crime = city.getCrime(hasProject);

                if (crime > finalCrimeLimit) {
                    int remainingSlots = city.getFreeSlots();
                    if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;
                    double reduced = crime - Math.min(max, remainingSlots) * policePct;
                    if (reduced > crime) return Double.NEGATIVE_INFINITY;

                    // if has any building past police station
                    byte[] buildings = city.getBuildings();
                    for (int i = Buildings.POLICE_STATION.ordinal() + 1; i < buildings.length; i++) {
                        if (buildings[i] > 0) return Double.NEGATIVE_INFINITY;
                    }
                }
                return parent.apply(city);
            };
        }


        if (positiceCash) {
            Function<JavaCity, Boolean> parentGoal = goal;

            double[] profitBuffer = new double[ResourceType.values.length];

            goal = new Function<JavaCity, Boolean>() {
                @Override
                public Boolean apply(JavaCity city) {
                    if (parentGoal.apply(city)) {
                        Arrays.fill(profitBuffer, 0);
                        city.profit(finalContinent, rads, -1L, hasProject, profitBuffer, numCities, finalMe.getGrossModifier(), 12);
                        profitBuffer[0] += 500000d / numCities;
                        double original = profitBuffer[0];

                        for (MilitaryUnit unit : MilitaryUnit.values) {
                            MilitaryBuilding building = unit.getBuilding();
                            if (building == null) continue;
                            int numBuilt = city.get(building);
                            if (numBuilt == 0) continue;
                            int amt = building.max() * numBuilt;

                            double[] upkeep = unit.getUpkeep(true);
                            profitBuffer[0] -= upkeep[0] * amt;
                        }
                        if (profitBuffer[0] < 0) {
                            return false;
                        }
                        return true;
                    }
                    return false;
                }
            };
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Guild root = Locutus.imp().getServer();
        GuildDB rootDb = Locutus.imp().getGuildDB(root);
        long timeout = db.isWhitelisted() && (Roles.ADMIN.hasOnRoot(author) || db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) ? 15000 : 5000;

        JavaCity optimized;
        if (days == null) {
            optimized = origin.optimalBuild(continent, rads, numCities, hasProject, finalMe.getGrossModifier(), timeout, modifyValueFunc, goal);
        } else {
            optimized = origin.roiBuild(continent, rads, numCities, hasProject, finalMe.getGrossModifier(), days, timeout, modifyValueFunc, goal);
        }

        optimized.setInfra(origin.getInfra());
        optimized.getMetrics(hasProject).recalculate(optimized, hasProject);
        double profit = optimized.profitConvertedCached(finalContinent, rads, hasProject, numCities, finalMe.getGrossModifier());
        double cost = PnwUtil.convertedTotal(optimized.calculateCost(origin));

        String json = optimized.toCityBuild().toString();

        StringBuilder result = new StringBuilder("```" + json + "```");
        String title = "$" + MathMan.format(profit) + " / day";

        if (days != null) {
            double baseProfit = origin.profitConvertedCached(finalContinent, rads, hasProject, numCities, finalMe.getGrossModifier());
            double netProfit = ((profit - baseProfit) * days - cost);

            result.append("\nNet Profit: (" + days + " days): $"  + MathMan.format(netProfit));
            if (netProfit > 0) {
                double roi;
                if (cost < 0) {
                    roi = Double.POSITIVE_INFINITY;
                    result.append("\nROI (weekly): " + MathMan.format(roi) + " (no cost to import)");
                } else {
                    roi = ((netProfit / cost) / days) * 7 * 100;
                    result.append("\nROI (weekly): " + MathMan.format(roi) + "%");
                }
            }
        }
        json = json.replaceAll(" ", "");

        String emoji = "Grant";
        String command = Settings.commandPrefix(true) + "grant %user% " + json;

        if (true) {
            result.append(" Disease: " + optimized.getDisease(hasProject)).append("\n");
            result.append(" Crime: " + optimized.getCrime(hasProject)).append("\n");
            result.append(" Commerce: " + optimized.getCommerce(hasProject)).append("\n");
            result.append(" Population: " + optimized.getPopulation(hasProject)).append("\n");
        }

        result.append(" Click " + emoji + " to request a grant");

        Role role = Roles.ECON.toRole(db);
        if (role != null) {
            result.append("\nPing " + role.getAsMention() + " to transfer you the funds");
        }
        result.append("\n" + author.getAsMention());

        me.setMeta(NationMeta.INTERVIEW_OPTIMALBUILD, (byte) 1);

        if (flags.contains('p')) {
            return title + "\n" + result.toString() + "";
        }
        io.create().embed(title, result.toString()).commandButton(command, emoji).send();
//        DiscordUtil.createEmbedCommand(event.getChannel(), title, result.toString(), emoji, command);
        return null;
    }

    private void checkup(IMessageIO io, DBNation me, int cityId, JavaCity city) {
        Map<Integer, JavaCity> cities = Collections.singletonMap(cityId, city);

        ArrayList<Map.Entry<Object, String>> audits = new ArrayList<Map.Entry<Object, String>>();
        audits.add(IACheckup.checkUnpowered(me, cities));
        audits.add(IACheckup.checkOverpowered(cities));
        audits.add(IACheckup.checkNuclearPower(cities));
        audits.add(IACheckup.checkExcessService(me, cities, Buildings.HOSPITAL, null));
        audits.add(IACheckup.checkExcessService(me, cities, Buildings.RECYCLING_CENTER, null));
        audits.add(IACheckup.checkExcessService(me, cities, Buildings.POLICE_STATION, null));
        audits.add(IACheckup.checkProductionBonus(me, cities));
        audits.add(IACheckup.checkEmptySlots(cities));
        audits.removeIf(f -> f == null || f.getKey() == null);
        if (!audits.isEmpty()) {
            StringBuilder message = new StringBuilder("\n```");
            for (Map.Entry<Object, String> audit : audits) {
                message.append(audit.getValue()).append("\n");
            }
            message.append("```");
            io.send("<" + PnwUtil.getCityUrl(cityId) + "> notes:" + message);
        }
    }
}

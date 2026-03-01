package link.locutus.discord.commands.info.optimal;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class OptimalBuild extends Command {
    public OptimalBuild() {
        super("OptimalBuild", "GenerateBuild", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.city.optimalBuild.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "OptimalBuild [days] <json|city-url>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Optimize a build for a city e.g.\n" +
                "For 30 days:" +
                "- `" + Settings.commandPrefix(true) + "OptimalBuild 30 " + Settings.PNW_URL() + "/city/id=1234`\n" +
                "For an indefinite time span:\n" +
                "- `" + Settings.commandPrefix(true) + "OptimalBuild " + Settings.PNW_URL() + "/city/id=1234`\n" +
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
                "To specify a tax rate, use `tax=25/25`\n" +
                "To spend longer finding a build, use e.g. `timeout:60s`\n" +
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        return onCommand(channel, guild, author, me, args, flags);
    }

    public String onCommand(IMessageIO io, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }
        Integer days = null;
        if (args.size() >= 2 && MathMan.isInteger(args.get(0))) {
            days = Integer.parseInt(args.get(0));
            args.remove(0);
        }
        JavaCity origin;

        Set<Project> addProject = new HashSet<>();

        boolean positiveCash = false;
        boolean manu = true;
        String mmr = null;

        GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
        long timeout = io instanceof StringMessageIO ? 1000 : db != null && db.isWhitelisted() && (Roles.ADMIN.hasOnRoot(author) || db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) ? 20000 : 9000;

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
                    case "timeout" -> {
                        String timeoutStr = split[1];
                        timeout = TimeUtil.timeToSec(timeoutStr) * 1000;
                        if (timeout > TimeUnit.MINUTES.toMillis(2)) {
                            throw new IllegalArgumentException("Timeout too long (max: 120s)");
                        }
                        iter.remove();
                    }
                    case "mmr" -> {
                        String mmrStr = split[1];
                        if (MathMan.isInteger(mmrStr) && mmrStr.length() == 4) {
                            mmr = mmrStr;
                            iter.remove();
                        }
                    }
                    case "radiation" -> {
                        baseRadiation = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "steel" -> {
                        steel = MathMan.parseInt(split[1]);
                        iter.remove();
                    }
                    case "gas", "gasoline" -> {
                        gasoline = MathMan.parseInt(split[1]);
                        iter.remove();
                    }
                    case "muni", "munitions" -> {
                        munitions = MathMan.parseInt(split[1]);
                        iter.remove();
                    }
                    case "alu", "aluminium", "aluminum" -> {
                        aluminum = MathMan.parseInt(split[1]);
                        iter.remove();
                    }
                    case "infrastructure", "infra" -> {
                        infra = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "infralow" -> {
                        infraLow = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "crime" -> {
                        crimeLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "pop", "population" -> {
                        popLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "poplow", "populationlow" -> {
                        popLow = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "disease" -> {
                        diseaseLimit = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "land" -> {
                        land = MathMan.parseDouble(split[1]);
                        iter.remove();
                    }
                    case "age" -> {
                        age = MathMan.parseInt(split[1]);
                        iter.remove();
                    }
                    case "continent" -> {
                        continent = Continent.valueOf(split[1].toUpperCase());
                        iter.remove();
                    }
                    case "manufacturing", "manufactured", "manu" -> {
                        manu = Boolean.parseBoolean(split[1]);
                        iter.remove();
                    }
                    case "cash" -> {
                        positiveCash = Boolean.parseBoolean(split[1]);
                        iter.remove();
                    }
                    case "tax", "taxes", "taxrate" -> {
                        taxes = new TaxRate(split[1]);
                        iter.remove();
                    }
                }
            }
        }
        if (args.isEmpty()) {
            return usage(null, io);
        }

        int cityId;
        if (args.size() <= 3 && args.get(args.size() - 1).contains("/city/")) {
            String cityArg = args.size() == 2 ? args.get(1) : args.get(0);
            if (!cityArg.toLowerCase().contains("/city/")) {
                return "Invalid city url: " + cityArg;
            }
            cityId = Integer.parseInt(cityArg.split("=")[1]);

            DBCity cityEntry = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId);
            if (cityEntry == null) Locutus.imp().runEventsAsync(events -> Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId, true, events));
            cityEntry.update(true);
            me = Locutus.imp().getNationDB().getNationById(cityEntry.getNationId());
            origin = cityEntry.toJavaCity(me);
            checkup(io, me, cityId, origin); // show help
//            if (days == null)
            {
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
            origin.setBuilding(Buildings.BARRACKS, mmr.charAt(0) - '0');
            origin.setBuilding(Buildings.FACTORY, mmr.charAt(1) - '0');
            origin.setBuilding(Buildings.HANGAR, mmr.charAt(2) - '0');
            origin.setBuilding(Buildings.DRYDOCK, mmr.charAt(3) - '0');
        }
        if (steel != null) origin.setBuilding(Buildings.STEEL_MILL, steel);
        if (gasoline != null) origin.setBuilding(Buildings.GAS_REFINERY, gasoline);
        if (munitions != null) origin.setBuilding(Buildings.MUNITIONS_FACTORY, munitions);
        if (aluminum != null) origin.setBuilding(Buildings.ALUMINUM_REFINERY, aluminum);
        if (!manu && steel != null) origin.setBuilding(Buildings.COAL_MINE, steel);
        if (!manu && steel != null) origin.setBuilding(Buildings.IRON_MINE, steel);
        if (!manu && gasoline != null) origin.setBuilding(Buildings.OIL_WELL, gasoline);
        if (!manu && munitions != null) origin.setBuilding(Buildings.LEAD_MINE, munitions);
        if (!manu && aluminum != null) origin.setBuilding(Buildings.BAUXITE_MINE, aluminum);

        if (infra != null) origin.setInfra(infra);
        if (land != null) origin.setLand(land);
        if (age != null) origin.setAge(age);
        if (continent == null) continent = me.getContinent();
        origin.setOptimalPower(continent);

        if (baseRadiation == -1) {
            baseRadiation = Locutus.imp().getTradeManager().getGlobalRadiation(continent) + Locutus.imp().getTradeManager().getGlobalRadiation();
        }

        if (origin.getInfra() > 4000) {
            return "Too much infrastructure.";
        }

        double radIndex = baseRadiation;
        double radsFactor = continent == Continent.ANTARCTICA ? 0.5 : 1;
        double rads = (1 + (Math.min(1000, radIndex) / (-1000))) * radsFactor;

        int numCities = me.getCities();

        DBNation finalMe = me;
        Continent finalContinent = continent;
        Predicate<Project> hasProject = Projects.optimize(project -> addProject.contains(project) || project.get(finalMe) > 0);

        CompletableFuture<IMessageBuilder> future = io.send("Please wait...");

        ToDoubleFunction<INationCity> valueFunc;
        if (taxes != null) {
            double[] buffer = new double[ResourceType.values.length];
            double moneyFactor = (100 - taxes.money) / 100d;
            double rssFactor = (100 - taxes.resources) / 100d;
            valueFunc = javaCity -> {
                Arrays.fill(buffer, 0);
                double[] profit = javaCity.getProfit(buffer);
                profit[0] *= moneyFactor;
                for (int i = 1; i < profit.length; i++) {
                    if (profit[i] > 0) {
                        profit[i] *= rssFactor;
                    }
                }
                return ResourceType.convertedTotal(profit);
            };
        } else {
            valueFunc = INationCity::getRevenueConverted;
        }

//        if (infraLow != null) {
//            ToDoubleFunction<CityNode> parent = valueFunc;
//            Double finalInfraLow = infraLow;
//            Double popLowFinal = popLow;
//            valueFunc = city -> {
//                if (city.getFreeSlots() <= 0) {
//                    double currentInfra = city.getInfra();
//                    city.setInfra(Math.min(currentInfra, finalInfraLow));
//                    city.getMetrics(hasProject).recalculate(city, hasProject);
//                    if (popLowFinal != null && city.calcPopulation(hasProject) < popLowFinal)
//                        return Double.NEGATIVE_INFINITY;
//                    Double value = parent.apply(city);
//                    city.setInfra(currentInfra);
//                    return value;
//                }
//                return parent.apply(city);
//            };
//        }

        if (popLimit != null) {
            Double finalpopLimit = popLimit;

            ToDoubleFunction<INationCity> parent = valueFunc;
            valueFunc = city -> {
                if (city.calcPopulation(hasProject) < finalpopLimit) return Double.NEGATIVE_INFINITY;
                return parent.applyAsDouble(city);
            };
        }

        if (popLow != null) {
            Double finalpopLimit = popLow;

            ToDoubleFunction<INationCity> parent = valueFunc;
            valueFunc = city -> {
                if (city.calcPopulation(hasProject) < finalpopLimit) return Double.NEGATIVE_INFINITY;
                return parent.applyAsDouble(city);
            };
        }

        Predicate<INationCity> goal = javaCity -> javaCity.getFreeSlots() <= 0;

        if (!manu) {
            Predicate<INationCity> parent = goal;
            goal = city -> {
                if (parent.test(city)) {
                    if (city.getBuilding(Buildings.MUNITIONS_FACTORY) > city.getBuilding(Buildings.LEAD_MINE))
                        return false;
                    if (city.getBuilding(Buildings.GAS_REFINERY) > city.getBuilding(Buildings.OIL_WELL))
                        return false;
                    if (city.getBuilding(Buildings.ALUMINUM_REFINERY) > city.getBuilding(Buildings.BAUXITE_MINE))
                        return false;
                    if (city.getBuilding(Buildings.STEEL_MILL) > city.getBuilding(Buildings.COAL_MINE) || city.getBuilding(Buildings.STEEL_MILL) > city.getBuilding(Buildings.IRON_MINE))
                        return false;
                    return true;
                }
                return false;
            };
        }
        if (steel != null) {
            int steelFinal = steel;
            Predicate<INationCity> parent = goal;
            goal = city -> {
                if (parent.test(city)) {
                    if (city.getBuilding(Buildings.STEEL_MILL) < steelFinal) return false;
                    return true;
                }
                return false;
            };
        }
        if (gasoline != null) {
            int gasolineFinal = gasoline;
            Predicate<INationCity> parent = goal;
            goal = city -> {
                if (parent.test(city)) {
                    if (city.getBuilding(Buildings.GAS_REFINERY) < gasolineFinal) return false;
                    return true;
                }
                return false;
            };
        }
        if (munitions != null) {
            int munitionsFinal = munitions;
            Predicate<INationCity> parent = goal;
            goal = city -> {
                if (parent.test(city)) {
                    if (city.getBuilding(Buildings.MUNITIONS_FACTORY) < munitionsFinal) return false;
                    return true;
                }
                return false;
            };
        }
        if (aluminum != null) {
            int aluminumFinal = aluminum;
            Predicate<INationCity> parent = goal;
            goal = city -> {
                if (parent.test(city)) {
                    if (city.getBuilding(Buildings.ALUMINUM_REFINERY) < aluminumFinal) return false;
                    return true;
                }
                return false;
            };
        }

        if (diseaseLimit != null) {
            Double finalDiseaseLimit = diseaseLimit;

            double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
            double recyclingPct = (-Buildings.RECYCLING_CENTER.pollution(hasProject)) * 0.05;
            double subwayPct = (-Buildings.SUBWAY.pollution(hasProject)) * 0.05;

            ToDoubleFunction<INationCity> parent = valueFunc;
            valueFunc = city -> {
                Double disease = city.calcDisease(hasProject);
                if (disease > finalDiseaseLimit) {

                    int remainingSlots = city.getFreeSlots();
                    if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;

                    int latestBuilding = 0;
                    for (int j = Buildings.SUBWAY.ordinal(); j <= Buildings.RECYCLING_CENTER.ordinal(); j++) {
                        if (city.getBuildingOrdinal(j) > 0) latestBuilding = j;
                    }

                    double pollutionDisease = city.calcPollution(hasProject) * 0.05;
                    double diseaseInfra = disease - pollutionDisease;

                    if (latestBuilding <= Buildings.RECYCLING_CENTER.ordinal()) {
                        int amt = (int) Math.min(Math.ceil(pollutionDisease / recyclingPct), Math.min(Buildings.RECYCLING_CENTER.cap(hasProject), remainingSlots));
                        if (amt > 0) {
                            pollutionDisease = Math.max(0, pollutionDisease - recyclingPct * amt);
                            double reduced = pollutionDisease + diseaseInfra;
                            if (reduced <= finalDiseaseLimit) return parent.applyAsDouble(city);
                            remainingSlots -= amt;
                            if (remainingSlots <= 0) return Double.NEGATIVE_INFINITY;
                        }
                    }

                    if (latestBuilding <= Buildings.HOSPITAL.ordinal()) {
                        int amt = Math.min(Buildings.HOSPITAL.cap(hasProject), remainingSlots);
                        if (amt > 0) {
                            diseaseInfra -= hospitalPct * amt;
                            double reduced = pollutionDisease + diseaseInfra;
                            if (reduced <= finalDiseaseLimit) return parent.applyAsDouble(city);
                            remainingSlots -= amt;
                            if (remainingSlots <= 0) return Double.NEGATIVE_INFINITY;
                        }
                    }

                    if (latestBuilding <= Buildings.RECYCLING_CENTER.ordinal()) {
                        int amt = (int) Math.min(Math.ceil(pollutionDisease / subwayPct), Math.min(Buildings.SUBWAY.cap(hasProject), remainingSlots));
                        if (amt > 0) {
                            pollutionDisease = Math.max(0, pollutionDisease - subwayPct * amt);
                            double reduced = pollutionDisease + diseaseInfra;
                            if (reduced <= finalDiseaseLimit) return parent.applyAsDouble(city);
                        }
                    }

                    return Double.NEGATIVE_INFINITY;
                }
                return parent.applyAsDouble(city);
            };
        }

        if (crimeLimit != null) {
            double policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;
            int max = Buildings.POLICE_STATION.cap(hasProject);
            Double finalCrimeLimit = crimeLimit;

            int maxIndex = PW.City.Building.SIZE - 1;
            int policeIndex = Buildings.POLICE_STATION.ordinal();
            int diff = maxIndex - policeIndex;

            ToDoubleFunction<INationCity> parent = valueFunc;
            valueFunc = city -> {
                double crime = city.calcCrime(hasProject);
                if (crime > finalCrimeLimit) {
                    int remainingSlots = city.getFreeSlots();
                    if (remainingSlots == 0) return Double.NEGATIVE_INFINITY;
                    int currentPolice = city.getBuilding(Buildings.POLICE_STATION);
                    if (currentPolice >= max) return Double.NEGATIVE_INFINITY;
                    double reduced = crime - Math.min(max - currentPolice, remainingSlots) * policePct;
                    if (reduced > crime) return Double.NEGATIVE_INFINITY;
                    if (city instanceof CityNode node) {
                        int index = node.getIndex();
                        int cachedMaxIndex = node.getCached().getMaxIndex() - 1;
                        if (index > cachedMaxIndex - diff) {
                            return Double.NEGATIVE_INFINITY;
                        }
                    } else {
                        return Double.NEGATIVE_INFINITY;
                    }
                }
                return parent.applyAsDouble(city);
            };
        }

        if (positiveCash) {
            Predicate<INationCity> parentGoal = goal;
            double[] profitBuffer = new double[ResourceType.values.length];
            double upkeepCash = 0;
            for (MilitaryUnit unit : MilitaryUnit.values) {
                MilitaryBuilding building = unit.getBuilding();
                if (building == null) continue;
                int numBuilt = origin.getBuilding(building);
                if (numBuilt == 0) continue;
                int amt = building.getUnitCap() * numBuilt;
                double[] upkeep = unit.addUpkeep(ResourceType.getBuffer(), amt, true, f -> finalMe.getResearch(null, f), finalMe.getMilitaryUpkeepFactor());
                upkeepCash += upkeep[0];
            }
            double finalUpkeepCash = upkeepCash;
            goal = city -> {
                if (parentGoal.test(city)) {
                    Arrays.fill(profitBuffer, 0);
                    city.getProfit(profitBuffer);
                    profitBuffer[0] += 500000d / numCities;
                    profitBuffer[0] -= finalUpkeepCash;
                    return !(profitBuffer[0] < 0);
                }
                return false;
            };
        }

        JavaCity optimized;
        if (days == null) {
            optimized = origin.optimalBuild(continent, numCities, valueFunc, goal, hasProject, timeout, rads, !manu, true, finalMe.getGrossModifier(), infraLow);
        } else {
            ToDoubleFunction<INationCity> finalValueFunc = valueFunc;
            Function<ToDoubleFunction<INationCity>, ToDoubleFunction<INationCity>> modifyValueFunc = f -> finalValueFunc;
            optimized = origin.roiBuild(continent, rads, numCities, hasProject, finalMe.getGrossModifier(), days, timeout, !manu, infraLow, modifyValueFunc, goal);
        }
        if (optimized == null) {
            throw new IllegalArgumentException("The bot failed to resolve an optimalbuild based on your inputs. Please try different arguments.\n" +
                    "If you believe this error is a bug, open a ticket in: " + DiscordUtil.getSupportServer() + " and provide the EXACT command you used.");
        }

        optimized.setInfra(origin.getInfra());
        optimized.getMetrics(hasProject).recalculate(optimized, hasProject);
        double profit = optimized.profitConvertedCached(finalContinent, rads, hasProject, numCities, finalMe.getGrossModifier());
        double cost = ResourceType.convertedTotal(optimized.calculateCost(origin));

        String json = optimized.toCityBuild().toString();

        StringBuilder result = new StringBuilder("```" + json + "```");
        String title = "$" + MathMan.format(profit) + " / day";

        if (days != null) {
            double baseProfit = origin.profitConvertedCached(finalContinent, rads, hasProject, numCities, finalMe.getGrossModifier());
            double netProfit = ((profit - baseProfit) * days - cost);

            result.append("\nNet Profit: (").append(days).append(" days): $").append(MathMan.format(netProfit));
            if (netProfit > 0) {
                double roi;
                if (cost < 0) {
                    roi = Double.POSITIVE_INFINITY;
                    result.append("\nROI (weekly): ").append(MathMan.format(roi)).append(" (no cost to import)\n");
                } else {
                    roi = ((netProfit / cost) / days) * 7 * 100;
                    result.append("\nROI (weekly): ").append(MathMan.format(roi)).append("%\n");
                }
            }
        }
        json = json.replaceAll(" ", "");

        String emoji = "Grant";
        String command = Settings.commandPrefix(true) + "grant {usermention} " + json;

        result.append(" Disease: ").append(MathMan.format(optimized.calcDisease(hasProject))).append("\n");
        result.append(" Pollution: ").append(optimized.calcPollution(hasProject)).append("\n");
        result.append(" Crime: ").append(MathMan.format(optimized.calcCrime(hasProject))).append("\n");
        result.append(" Commerce: ").append(optimized.calcCommerce(hasProject) + "/" + optimized.getMaxCommerce(hasProject)).append("\n");
        result.append(" Population: ").append(optimized.calcPopulation(hasProject)).append("\n");

        result.append(" Click ").append(emoji).append(" to request a grant");

        Role role = db == null ? null : Roles.ECON.toRole(me.getAlliance_id(), db);
        if (role != null) {
            result.append("\nPing ").append(role.getAsMention()).append(" to transfer you the funds");
        }
        result.append("\n").append(author != null ? author.getAsMention() : "");

        me.setMeta(NationMeta.INTERVIEW_OPTIMALBUILD, (byte) 1);

        if (flags.contains('p')) {
            return title + "\n" + result.toString();
        }
        io.create().embed(title, result.toString()).commandButton(command, emoji).send();
//        DiscordUtil.createEmbedCommand(channel, title, result.toString(), emoji, command);
        return null;
    }

    private void checkup(IMessageIO io, DBNation me, int cityId, JavaCity city) {
        Map<Integer, JavaCity> cities = Collections.singletonMap(cityId, city);

        ArrayList<Map.Entry<Object, String>> audits = new ArrayList<>();
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
            io.send("<" + PW.City.getCityUrl(cityId) + "> notes:" + message);
        }
    }
}

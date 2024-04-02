package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.commands.info.optimal.CityBranch;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.search.BFSUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.CommerceBuilding;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class JavaCity {
    private final byte[] buildings;
    private int numBuildings = -1;
    private double infra;
    private double land_;
    private long dateCreated;
    private int nuke_turn;

    private Metrics metrics;

    public static Map.Entry<DBNation, JavaCity> getOrCreate(int cityId, boolean update) {
        List<Event> events = new LinkedList<>();
        long now = System.currentTimeMillis();
        DBCity cityEntry = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId, true, events::add);
        Locutus.imp().runEventsAsync(events);
        if (cityEntry != null) {
            if (update && now > cityEntry.fetched) {
                cityEntry.update(true);
            }
            DBNation nation = DBNation.getById(cityEntry.getNationId());
            if (nation != null) {
                return Map.entry(nation, cityEntry.toJavaCity(nation));
            }
            DBNation dummy = new DBNation();
            dummy.setNation_id(cityEntry.getNationId());
            return Map.entry(dummy, cityEntry.toJavaCity(f -> false));
        }
        return null;
    }

    public void clear() {
        numBuildings = 0;
        Arrays.fill(buildings, (byte) 0);
    }

    public void set(JavaCity other, int maxBuildingIndex) {
//        if (this.metrics != null) {
//            if (other.metrics != null) {
//                this.metrics.profit = other.metrics.profit;
//                this.metrics.population = other.metrics.population;
//                this.metrics.disease = other.metrics.disease;
//                this.metrics.crime = other.metrics.crime;
//                this.metrics.pollution = other.metrics.pollution;
//                this.metrics.commerce = other.metrics.commerce;
//            } else {
//                clearMetrics();
//            }
//        }
//        maxBuildingIndex = Math.min(buildings.length, maxBuildingIndex);
//        for (int i = 0; i <= maxBuildingIndex; i++) {
        for (int i = 0; i < buildings.length; i++) {
            buildings[i] = other.buildings[i];
        }
        this.numBuildings = other.numBuildings;
        this.infra = other.infra;
        this.dateCreated = other.dateCreated;
        this.land_ = other.land_;
        this.nuke_turn = other.nuke_turn;
    }

    public void clearMetrics() {
        if (this.metrics != null) {
            metrics.commerce = null;
            metrics.profit = null;
        }
    }

    public long getNukeTurn() {
        return nuke_turn;
    }

    public String getMMR() {
        return get(Buildings.BARRACKS) + "" + get(Buildings.FACTORY) + "" + get(Buildings.HANGAR) + "" + get(Buildings.DRYDOCK);
    }

    public int[] getMMRArray() {
        return new int[]{get(Buildings.BARRACKS), get(Buildings.FACTORY), get(Buildings.HANGAR), get(Buildings.DRYDOCK)};
    }

    public byte[] getBuildings() {
        return buildings;
    }

    public Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject) {
        double density = getPopulation(hasProject) / getLand();
        double infra = getInfra();
        double destroyedMin = Math.min(infra, Math.min(1700, infra * 0.8 + 150));
        double destroyedMax = Math.min(infra, Math.max(Math.max(2000, density * 13.5), infra * 0.8 + 150));
        return new AbstractMap.SimpleEntry<>((int) Math.round(destroyedMin), (int) Math.round(destroyedMax));
    }

    public Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
        double density = getPopulation(hasProject) / getLand();
        double infra = getInfra();
        double destroyedMin = Math.min(infra, Math.min(300, infra * 0.3 + 100));
        double destroyedMax = Math.min(infra, Math.max(Math.max(350, density * 3), infra * 0.8 + 150));
        return new AbstractMap.SimpleEntry<>((int) Math.round(destroyedMin), (int) Math.round(destroyedMax));
    }

    public void setMMR(MMRInt mmr) {
        set(Buildings.BARRACKS, (int) Math.round(mmr.getBarracks()));
        set(Buildings.FACTORY, (int) Math.round(mmr.getFactory()));
        set(Buildings.HANGAR, (int) Math.round(mmr.getHangar()));
        set(Buildings.DRYDOCK, (int) Math.round(mmr.getDrydock()));
    }

    public static class Metrics {
        public Integer population;
        public Double disease;
        public Double crime;
        public Integer pollution;
        public Integer commerce;
        public Boolean powered;
        public Double profit;

        public void recalculate(JavaCity city, Predicate<Project> hasProject) {
            pollution = 0;
            commerce = 0;

            double pollutionMax = 400d;
            int turnsMax = 11 * 12;
            long turns = TimeUtil.getTurn() - city.nuke_turn;
            if (turns < turnsMax) {
                double nukePollution = (turnsMax - turns) * pollutionMax / (turnsMax);
                if (nukePollution > 0) {
                    pollution += (int) nukePollution;
                }
            }

            for (Building building : Buildings.POLLUTION_BUILDINGS) {
                int amt = city.buildings[building.ordinal()];
                if (amt == 0) continue;
                pollution += amt * building.pollution(hasProject);
            }

            for (Building building : Buildings.COMMERCE_BUILDINGS) {
                int amt = city.buildings[building.ordinal()];
                if (amt == 0) continue;
                commerce += amt * ((CommerceBuilding) building).getCommerce();
            }
            int maxCommerce;
            if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
                if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                    maxCommerce = 125;
                } else {
                    maxCommerce = 115;
                }
            } else {
                maxCommerce = 100;
            }
            if (commerce > maxCommerce) {
                commerce = maxCommerce;
            }

            pollution = Math.max(0, pollution);

            double basePopulation = city.getInfra() * 100;

            int hospitals = city.get(Buildings.HOSPITAL);
            double hospitalModifier;
            if (hospitals > 0) {
                double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
                hospitalModifier = hospitals * hospitalPct;
            } else {
                hospitalModifier = 0;
            }

            int police = city.get(Buildings.POLICE_STATION);
            double policeMod;
            if (police > 0) {
                double policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;
                policeMod = police * (policePct);
            } else {
                policeMod = 0;
            }

            double pollutionModifier = pollution * 0.05;
            disease = Math.max(0, ((0.01 * MathMan.sqr((city.getInfra() * 100) / (city.getLand() + 0.001)) - 25) * 0.01d) + (city.getInfra() * 0.001) - hospitalModifier + pollutionModifier);

            double diseaseDeaths = ((disease * 0.01) * basePopulation);

            crime = Math.max(0, ((MathMan.sqr(103 - commerce) + (city.getInfra() * 100))*(0.000009d) - policeMod));
            double crimeDeaths = Math.max((crime * 0.1) * (100 * city.getInfra()) - 25, 0);

            double ageBonus = (1 + Math.log(Math.max(1, city.getAge())) * 0.0666666666666666666666666666666);
            population = (int) Math.round(Math.max(10, ((basePopulation - diseaseDeaths - crimeDeaths) * ageBonus)));
        }
    }

    public Metrics getCachedMetrics() {
        return metrics;
    }

    public Metrics getMetrics(Predicate<Project> hasProject) {
        if (metrics == null) {
            metrics = new Metrics();
        }
        if (metrics.commerce == null) {

            metrics.recalculate(this, hasProject);
        }
        return metrics;
    }

    public JavaCity(String json) {
        this(CityBuild.of(json));
        if (getLand() == null) {
            setLand(getInfra());
        }
        initImpTotal();
    }

    public JavaCity(CityBuild city) {
        this.buildings = new byte[Buildings.size()];
        this.infra = city.getInfraNeeded();
        set(Buildings.COAL_POWER, city.getImpCoalpower());
        set(Buildings.OIL_POWER, city.getImpOilpower());
        set(Buildings.WIND_POWER, city.getImpWindpower());
        set(Buildings.NUCLEAR_POWER, city.getImpNuclearpower());
        set(Buildings.COAL_MINE, city.getImpCoalmine());
        set(Buildings.OIL_WELL, city.getImpOilwell());
        set(Buildings.URANIUM_MINE, city.getImpUramine());
        set(Buildings.LEAD_MINE, city.getImpLeadmine());
        set(Buildings.IRON_MINE, city.getImpIronmine());
        set(Buildings.BAUXITE_MINE, city.getImpBauxitemine());
        set(Buildings.FARM, city.getImpFarm());
        set(Buildings.GAS_REFINERY, city.getImpGasrefinery());
        set(Buildings.ALUMINUM_REFINERY, city.getImpAluminumrefinery());
        set(Buildings.MUNITIONS_FACTORY, city.getImpMunitionsfactory());
        set(Buildings.STEEL_MILL, city.getImpSteelmill());
        set(Buildings.POLICE_STATION, city.getImpPolicestation());
        set(Buildings.HOSPITAL, city.getImpHospital());
        set(Buildings.RECYCLING_CENTER, city.getImpRecyclingcenter());
        set(Buildings.SUBWAY, city.getImpSubway());
        set(Buildings.SUPERMARKET, city.getImpSupermarket());
        set(Buildings.BANK, city.getImpBank());
        set(Buildings.MALL, city.getImpMall());
        set(Buildings.STADIUM, city.getImpStadium());
        set(Buildings.BARRACKS, city.getImpBarracks());
        set(Buildings.FACTORY, city.getImpFactory());
        set(Buildings.HANGAR, city.getImpHangars());
        set(Buildings.DRYDOCK, city.getImpDrydock());
        if (city.getLand() != null) setLand(city.getLand());
        if (city.getAge() != null) setAge(city.getAge());
        initImpTotal();
    }

    public JavaCity(byte[] buildings, double infra, double land, long dateCreated, int nuke_turn) {
        this.buildings = buildings;
        this.dateCreated = dateCreated;
        this.land_ = land;
        this.infra = infra;
        this.nuke_turn = nuke_turn;
        initImpTotal();
    }

    public JavaCity() {
        this.buildings = new byte[Buildings.size()];
        dateCreated = Long.MAX_VALUE;
        land_ = 0d;
        this.numBuildings = 0;
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeInt((int) (getInfra() * 100));
            dos.writeInt((int) (getLand() * 100));
            for (int i = 0; i < buildings.length; i++) {
                if (buildings[i] != 0) {
                    dos.writeByte(i);
                    dos.writeByte(buildings[i]);
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JavaCity fromBytes(byte[] data) {
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(data));
        JavaCity city = new JavaCity();
        try {
            city.setInfra(is.readInt() / 100d);
            city.setLand(is.readInt() / 100d);
            int type;
            while ((type = is.read()) != -1) {
                city.buildings[type] = is.readByte();
            }
            return city;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String instructions(int cityId, JavaCity from, double[] total) {
        HashMap<Integer, JavaCity> map = new HashMap<>();
        map.put(cityId, from);
        return instructions(map, total);
    }

    public String instructions(Map<Integer, JavaCity> fromMap, double[] total) {

        Map<Integer, Double> landPurchases = new HashMap<>();
        Map<Integer, Double> infraPurchases = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> entry : fromMap.entrySet()) {
            JavaCity from = entry.getValue();
            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, this.calculateCost(from));
            // remove negatives
            for (int i = 0; i < total.length; i++) {
                total[i] = Math.max(0, total[i]);
            }
            if (getLand() != null && getLand() > from.getLand()) {
                landPurchases.put(entry.getKey(), getLand());
            }
            if (getInfra() > from.getInfra()) {
                infraPurchases.put(entry.getKey(), getInfra());
            }
        }

        StringBuilder response = new StringBuilder();
        int i = 0;
        response.append(++i+". Ensure you have the following resources:");
        Map<ResourceType, Double> totalMap = ResourceType.resourcesToMap(total);
        if (!totalMap.isEmpty()) response.append('\n').append("```" + ResourceType.resourcesToString(totalMap) + "```");
        if (!infraPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : infraPurchases.entrySet()) {
                if (entry.getValue() > 0) {
                    response.append('\n').append(++i + ". (to buy) Enter @" + getInfra() + " infra in <" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey() + ">");
                }
            }
        }

        if (!landPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : landPurchases.entrySet()) {
                if (entry.getValue() > 0) {
                    response.append('\n').append(++i + ". (to buy) Enter @" + getLand() + " land in <" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey() + ">");
                }
            }
        }
        response.append('\n').append(++i+". Go to <" + Settings.INSTANCE.PNW_URL() + "/city/improvements/bulk-import/>");
        response.append('\n').append(++i+". Copy the following build:");
        response.append("```").append(toCityBuild().toString()).append("```");
        response.append('\n').append(++i+". Check the checkbox and click the submit import button");
        response.append('\n').append(++i+". If you are missing any resources or money, obtain them and try again");

        if (!infraPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : infraPurchases.entrySet()) {
                if (entry.getValue() < 0) {
                    response.append('\n').append(++i + ". (to sell) Enter @" + getInfra() + " infra in <" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey() + ">");
                }
            }
        }

        response.append('\n').append("7. Repurchase military units.");

        return response.toString().trim();
    }

    public String toJson() {
        JsonObject object = new JsonObject();

        Map<String, String> json = new HashMap<>();
        json.put("infra_needed", getRequiredInfra() + "");
        json.put("imp_total", getImpTotal() + "");
        if (land_ > 0) {
            json.put("land", getLand() + "");
        }
        if (getAge() > 0) {
            json.put("age", getAge() + "");
        }
        for (int ordinal = 0; ordinal < buildings.length; ordinal++) {
            int amt = buildings[ordinal];
            if (amt == 0) continue;

            json.put(Buildings.get(ordinal).nameSnakeCase(), amt + "");
        }

        return new Gson().toJson(json);
    }

    public CityBuild toCityBuild() {
        return CityBuild.of(toJson());
    }

    public JavaCity(City city) {
        this.buildings = new byte[Buildings.size()];
        this.dateCreated = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(city.getAge());
        this.land_ = Double.parseDouble(city.getLand());

        if (city.getNuclearpollution() > 0) {
            double turns = 11 * 12 * (400d - city.getNuclearpollution()) / 400d;
            nuke_turn = (int) (TimeUtil.getTurn() - ((int) turns));
        }

        metrics = new Metrics();
        metrics.disease = city.getDisease();
        metrics.crime = city.getCrime();
        metrics.pollution = (int) city.getPollution();
        metrics.population = (int) city.getPopulation();
        metrics.commerce = (int) city.getCommerce();
        metrics.powered = city.getPowered().equalsIgnoreCase("Yes");

        infra = Double.parseDouble(city.getInfrastructure());

        set(Buildings.COAL_POWER, Integer.parseInt(city.getImpCoalpower()));
        set(Buildings.OIL_POWER, Integer.parseInt(city.getImpOilpower()));
        set(Buildings.WIND_POWER, Integer.parseInt(city.getImpWindpower()));
        set(Buildings.NUCLEAR_POWER, Integer.parseInt(city.getImpNuclearpower()));
        set(Buildings.COAL_MINE, Integer.parseInt(city.getImpCoalmine()));
        set(Buildings.OIL_WELL, Integer.parseInt(city.getImpOilwell()));
        set(Buildings.URANIUM_MINE, Integer.parseInt(city.getImpUramine()));
        set(Buildings.LEAD_MINE, Integer.parseInt(city.getImpLeadmine()));
        set(Buildings.IRON_MINE, Integer.parseInt(city.getImpIronmine()));
        set(Buildings.BAUXITE_MINE, Integer.parseInt(city.getImpBauxitemine()));
        set(Buildings.FARM, Integer.parseInt(city.getImpFarm()));
        set(Buildings.GAS_REFINERY, Integer.parseInt(city.getImpGasrefinery()));
        set(Buildings.ALUMINUM_REFINERY, Integer.parseInt(city.getImpAluminumrefinery()));
        set(Buildings.MUNITIONS_FACTORY, Integer.parseInt(city.getImpMunitionsfactory()));
        set(Buildings.STEEL_MILL, Integer.parseInt(city.getImpSteelmill()));
        set(Buildings.POLICE_STATION, Integer.parseInt(city.getImpPolicestation()));
        set(Buildings.HOSPITAL, Integer.parseInt(city.getImpHospital()));
        set(Buildings.RECYCLING_CENTER, Integer.parseInt(city.getImpRecyclingcenter()));
        set(Buildings.SUBWAY, Integer.parseInt(city.getImpSubway()));
        set(Buildings.SUPERMARKET, Integer.parseInt(city.getImpSupermarket()));
        set(Buildings.BANK, Integer.parseInt(city.getImpBank()));
        set(Buildings.MALL, Integer.parseInt(city.getImpMall()));
        set(Buildings.STADIUM, Integer.parseInt(city.getImpStadium()));
        set(Buildings.BARRACKS, Integer.parseInt(city.getImpBarracks()));
        set(Buildings.FACTORY, Integer.parseInt(city.getImpFactory()));
        set(Buildings.HANGAR, Integer.parseInt(city.getImpHangar()));
        set(Buildings.DRYDOCK, Integer.parseInt(city.getImpDrydock()));

        initImpTotal();
    }

    public JavaCity(JavaCity other) {
        this.buildings = other.buildings.clone();
        this.numBuildings = other.numBuildings;
        this.infra = other.infra;
        this.dateCreated = other.dateCreated;
        this.land_ = other.land_;
    }

    private transient int hashCode = 0;

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        JavaCity javaCity = (JavaCity) o;
//        return javaCity.hashCode() == hashCode();
        return Arrays.equals(buildings, javaCity.buildings) && javaCity.infra == infra;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Arrays.hashCode(buildings);
        }
        return hashCode;
    }

    public JavaCity zeroNonMilitary() {
        for (int i = 0; i < buildings.length; i++) {
            if (Buildings.get(i) instanceof MilitaryBuilding) continue;
            numBuildings -= buildings[i];
            buildings[i] = 0;
        }
        return this;
    }

    public JavaCity optimalBuild(DBNation nation, long timeout) {
        return optimalBuild(nation.getContinent(), nation.getRads(), nation.getCities(), nation::hasProject, nation.getGrossModifier(), timeout);
    }

    public JavaCity optimalBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, long timeout) {
        return optimalBuild(continent, rads, numCities, hasProject, grossModifier, timeout, f -> f, null);
    }

    public JavaCity optimalBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, long timeout,
                                 Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc, Function<JavaCity, Boolean> goal) {
        Predicate<Project> finalHasProject = Projects.hasProjectCached(hasProject);
        Function<JavaCity, Double> valueFunction = javaCity -> {
            return javaCity.profitConvertedCached(continent, rads, finalHasProject, numCities, grossModifier) / javaCity.getImpTotal();
        };
        valueFunction = modifyValueFunc.apply(valueFunction);

        return optimalBuild(continent, valueFunction, goal, finalHasProject, timeout);
    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, int days, long timeout) {
        return roiBuild(continent, rads, numCities, hasProject, grossModifier, days, timeout, f -> f, null);
    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, int days, long timeout, Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc, Function<JavaCity, Boolean> goal) {
        JavaCity origin = new JavaCity(this);
        origin.setAge(this.getAge() + days / 2);

        JavaCity zeroed = new JavaCity(origin);
        zeroed.zeroNonMilitary();

        int maxImp = (int) (this.getInfra() / 50);
        if (maxImp == zeroed.getImpTotal()) return zeroed;

        double baseProfit = origin.profitConvertedCached(continent, rads, hasProject, numCities, grossModifier);
        double zeroedProfit = zeroed.profitConvertedCached(continent, rads, hasProject, numCities, grossModifier);

        int baseImp = zeroed.getImpTotal();
        int impOverZero = maxImp - zeroed.getImpTotal();
        double baseProfitNonTax = (baseProfit - zeroedProfit);
        double profitPerImp = baseProfitNonTax / impOverZero;

        Predicate<Project> finalHasProject = Projects.hasProjectCached(hasProject);
        Function<JavaCity, Double> valueFunction = javaCity -> {
            double newProfit = javaCity.profitConvertedCached(continent, rads, finalHasProject, numCities, grossModifier);

            double cost = javaCity.calculateCostConverted(origin);

//            int imp = javaCity.getImpTotal() - baseImp;
//            double expectedProfit = profitPerImp * imp;
            return (newProfit * days - cost) / javaCity.getImpTotal();
        };

        valueFunction = modifyValueFunc.apply(valueFunction);

        JavaCity optimal = zeroed.optimalBuild(continent, valueFunction, goal, finalHasProject, timeout);
        return optimal;
    }

    public JavaCity optimalBuild(Continent continent, Function<JavaCity, Double> valueFunction, Predicate<Project> hasProject, long timeout) {
        return optimalBuild(continent, valueFunction, null, hasProject, timeout);
    }

    public JavaCity optimalBuild(Continent continent, Function<JavaCity, Double> valueFunction, Function<JavaCity, Boolean> goal, Predicate<Project> hasProject, long timeout) {
        if (goal == null) {
            goal = javaCity -> javaCity.getFreeInfra() < 50;
        }
        CityBranch searchServices = new CityBranch(continent, -1d, -1d, false, hasProject);

        Function<Map.Entry<JavaCity, Integer>, Double> valueFunction2 = e -> valueFunction.apply(e.getKey());
        Function<JavaCity, Boolean> finalGoal = goal;
        Function<Map.Entry<JavaCity, Integer>, Boolean> goal2 = e -> finalGoal.apply(e.getKey());

        PriorityQueue<Map.Entry<JavaCity, Integer>> queue = new ObjectHeapPriorityQueue<Map.Entry<JavaCity, Integer>>(500000,
                (o1, o2) -> Double.compare(valueFunction2.apply(o2), valueFunction2.apply(o1)));

        JavaCity origin = new JavaCity(this);

        Function<Double, Function<Map.Entry<JavaCity, Integer>, Double>> valueCompletionFunction;

        valueCompletionFunction = factor -> (Function<Map.Entry<JavaCity, Integer>, Double>) entry -> {
            Double parentValue = valueFunction2.apply(entry);
            int imps = entry.getKey().getImpTotal();

            if (factor <= 1) {
                parentValue = (parentValue * imps) * factor + (parentValue * (1 - factor));
            } else {
                double factor2 = factor - 1;
                parentValue = imps * factor2 + (parentValue * imps) * (1 - factor2);
            }
            return parentValue;
        };

        if (origin.getRequiredInfra() > origin.getInfra()) {
            throw new IllegalArgumentException("The city infrastructure (" + MathMan.format(origin.getInfra()) + ") is too low for the required buildings (required infra: " + MathMan.format(origin.getRequiredInfra()) + ", mmr: " + origin.getMMR() + ")");
        }

        Map.Entry<JavaCity, Integer> optimized = BFSUtil.search(goal2, valueFunction2, valueCompletionFunction, searchServices, searchServices, new AbstractMap.SimpleEntry<>(origin, 4), queue, timeout);

        return optimized == null ? null : optimized.getKey();
    }

    public double profitConvertedCached(Continent continent, double rads, Predicate<Project> hasProject, int numCities, double grossModifier) {
        if (metrics != null && metrics.profit != null) {
            return metrics.profit;
        }
        double profit = profitConverted2(continent, rads, hasProject, numCities, grossModifier);
        if (metrics != null) {
            metrics.profit = profit;
        }
        return profit;
    }

    public void validate(Continent continent, Predicate<Project> hasProject) {
        if (getRequiredInfra() > getInfra()) {
            throw new IllegalArgumentException("Infra is too low: " + getInfra() + " < " + getRequiredInfra()) {
                @Override
                public Throwable fillInStackTrace() {
                    return this;
                }
            };
        }
        for (int i = 0; i < buildings.length; i++) {
            int num = buildings[i];
            if (num == 0) continue;

            Building building = Buildings.get(i);

            if (num < 0) {
                throw new IllegalArgumentException("Cannot have negative buildings: " + building.nameSnakeCase()) {
                    @Override
                    public Throwable fillInStackTrace() {
                        return this;
                    }
                };
            }

            if (num > building.cap(hasProject)) {
                throw new IllegalArgumentException("Cannot have more than: " + building.cap(hasProject) + " of " + building.nameSnakeCase() + " (you have: " + num + ")") {
                    @Override
                    public Throwable fillInStackTrace() {
                        return this;
                    }
                };
            }

            if (building instanceof ResourceBuilding && !((ResourceBuilding) building).canBuild(continent)) {
                throw new IllegalArgumentException("Cannot build: " + building.nameSnakeCase() + " on " + continent.name()) {
                    @Override
                    public Throwable fillInStackTrace() {
                        return this;
                    }
                };
            }
        }

        if (getInfra() > getPoweredInfra()) {
            throw new IllegalArgumentException("Insufficient power. " + getPoweredInfra() + " < " + getInfra());
        }
    }

    public int getPoweredInfra() {
        int powered = 0;
        powered += get(Buildings.WIND_POWER) * Buildings.WIND_POWER.getInfraMax();
        powered += get(Buildings.COAL_POWER) * Buildings.COAL_POWER.getInfraMax();
        powered += get(Buildings.OIL_POWER) * Buildings.OIL_POWER.getInfraMax();
        powered += get(Buildings.NUCLEAR_POWER) * Buildings.NUCLEAR_POWER.getInfraMax();

        return powered;
    }

    public double profitConverted2(Continent continent, double rads, Predicate<Project> hasProject, int numCities, double grossModifier) {
        double profit = 0;

        final boolean powered = (metrics == null || metrics.powered != Boolean.FALSE) && (getPoweredInfra() >= infra);
        int unpoweredInfra = (int) Math.ceil(infra);

        if (powered) {
            for (int ordinal = 0; ordinal < 4; ordinal++) {
                int amt = buildings[ordinal];
                if (amt == 0) continue;

                Building building = Buildings.get(ordinal);

                for (int i = 0; i < amt; i++) {
                    if (unpoweredInfra > 0) {
                        profit += ((APowerBuilding) building).consumptionConverted(unpoweredInfra);
                        unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                    }
                }
                profit += building.profitConverted(continent, rads, hasProject, this, amt);
            }
            for (int ordinal = Buildings.GAS_REFINERY.ordinal(); ordinal < buildings.length; ordinal++) {
                int amt = buildings[ordinal];
                if (amt == 0) continue;

                Building building = Buildings.get(ordinal);
                profit += building.profitConverted(continent, rads, hasProject, this, amt);
            }
        }

        for (int ordinal = 4; ordinal < Buildings.GAS_REFINERY.ordinal(); ordinal++) {
            int amt = buildings[ordinal];
            if (amt == 0) continue;

            Building building = Buildings.get(ordinal);
            profit += building.profitConverted(continent, rads, hasProject, this, amt);
        }

        // if any commerce buildings

        int commerce = powered ? getMetrics(hasProject).commerce : 0;

        double newPlayerBonus = numCities < 10 ? Math.max(1, (200d - ((numCities - 1) * 10d)) * 0.01) : 1;

        double income = Math.max(0, (((commerce * 0.02) * 0.725) + 0.725) * getMetrics(hasProject).population * newPlayerBonus) * grossModifier;;


        profit += income;

        double basePopulation = getInfra() * 100;
        double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(getAge()) / 15d) - basePopulation) / 850;

        profit -= ResourceType.convertedTotalNegative(ResourceType.FOOD, food);

        return profit;
    }

    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, double[] profitBuffer, int numCities, double grossModifier, int turns) {
        return profit(continent, rads, date, hasProject, profitBuffer, numCities, grossModifier, false, turns);
    }
    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, double[] profitBuffer, int numCities, double grossModifier, boolean forceUnpowered, int turns) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

        boolean powered;
        if (forceUnpowered) {
            powered = false;
        } else {
            powered = true;
            if (metrics != null && metrics.powered != null) powered = metrics.powered;
            if (powered && getPoweredInfra() < infra) powered = false;
        }

        int unpoweredInfra = (int) Math.ceil(infra);
        for (int ordinal = 0; ordinal < buildings.length; ordinal++) {
            int amt = buildings[ordinal];
            if (amt == 0) continue;

            Building building = Buildings.get(ordinal);

            if (!powered) {
                if (building instanceof CommerceBuilding || building instanceof MilitaryBuilding || (building instanceof ResourceBuilding && ((AResourceBuilding) building).getResourceProduced().isManufactured())) {
                    continue;
                }
            }
            profitBuffer = building.profit(continent, rads, date, hasProject, this, profitBuffer, turns);
            if (building instanceof APowerBuilding) {
                for (int i = 0; i < amt; i++) {
                    if (unpoweredInfra > 0) {
                        profitBuffer = ((APowerBuilding) building).consumption(unpoweredInfra, profitBuffer, turns);
                        unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                    }
                }
            }
        }
        int commerce = getMetrics(hasProject).commerce;
        if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
            if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
                commerce = Math.min(125, metrics.commerce + 2);
            } else {
                commerce = Math.min(115, metrics.commerce);
            }
        } else {
            commerce = Math.min(100, metrics.commerce);
        }

        double newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

        double income = (((commerce/50d) * 0.725d) + 0.725d) * metrics.population * newPlayerBonus * grossModifier;

        profitBuffer[ResourceType.MONEY.ordinal()] += income * turns / 12;

        double basePopulation = getInfra() * 100;
        double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(getAge()) / 15d) - basePopulation) / 850;
        profitBuffer[ResourceType.FOOD.ordinal()] -= food * turns / 12d;

        return profitBuffer;
    }


    public double[] calculateCost(JavaCity from) {
        return calculateCost(from, new double[ResourceType.values.length]);
    }

    public double calculateCostConverted(JavaCity from) {
        double total = 0;
        for (int i = 0; i < buildings.length; i++) {
            int amtOther = from.buildings[i];
            int amt = buildings[i];
            if (amt != amtOther) {
                if (amtOther > amt) {
                    total += Buildings.get(i).getNMarketCost((amt - amtOther) * 0.5);
                } else {
                    total += Buildings.get(i).getNMarketCost(amt - amtOther);
                }
            }
        }

        if (this.getInfra() > from.getInfra()) {
            total += PW.City.Infra.calculateInfra(from.getInfra(), getInfra());
        }
        if (!Objects.equals(getLand(), from.getLand())) {
            total += PW.City.Land.calculateLand(from.getLand(), getLand());
        }
        return total;
    }

    public double[] calculateCost(JavaCity from, double[] buffer) {
        return calculateCost(from, buffer, true, true);
    }

    public double[] calculateCost(JavaCity from, double[] buffer, boolean infra, boolean land) {
        for (int i = 0; i < buildings.length; i++) {
            int amtOther = from.buildings[i];
            int amt = buildings[i];
            if (amt != amtOther) {
                if (amtOther > amt) {
                    Buildings.get(i).cost(buffer, (amt - amtOther) * 0.5);
                } else {
                    Buildings.get(i).cost(buffer, amt - amtOther);
                }
            }
        }

        if (infra && this.getInfra() > from.getInfra()) {
            buffer[ResourceType.MONEY.ordinal()] += PW.City.Infra.calculateInfra(from.getInfra(), getInfra());
        }
        if (land && !Objects.equals(getLand(), from.getLand())) {
            buffer[ResourceType.MONEY.ordinal()] += PW.City.Land.calculateLand(from.getLand(), getLand());
        }
        return buffer;
    }

    private void initImpTotal() {
        numBuildings = 0;
        for (int building : buildings) {
            numBuildings += building;
        }
    }

    public int getImpTotal() {
        return numBuildings;
    }

    public JavaCity set(Building building) {
        this.numBuildings++;
        this.buildings[building.ordinal()]++;
        return this;
    }

    public JavaCity remove(Building building) {
        this.numBuildings--;
        this.buildings[building.ordinal()]--;
        return this;
    }

    public JavaCity set(Building building, int amt) {
        int existing = this.buildings[building.ordinal()];
        this.numBuildings += amt - existing;
        this.buildings[building.ordinal()] = (byte) amt;
        return this;
    }

    public int get(int ordinal) {
        return buildings[ordinal];
    }

    public int get(Building building) {
        return get(building.ordinal());
    }

    public int getRequiredInfra() {
        return getImpTotal() * 50;
    }

    public double getInfra() {
        return infra;
    }

    public int getAge() {
        if (dateCreated <= 0) return 1;
        if (dateCreated == Long.MAX_VALUE) return 1;
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - dateCreated));
    }

    public double getFreeInfra() {
        return getInfra() - getRequiredInfra();
    }

    public int getFreeSlots() {
        return (int) (getFreeInfra() / 50);
    }

    public Double getLand() {
        return land_;
    }
    public JavaCity setInfra(double infra) {
        this.infra = infra;
        return this;
    }

    public JavaCity setAge(Integer age) {
        this.dateCreated = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(age);
        return this;
    }

    public JavaCity setLand(Double land) {
        this.land_ = land;
        return this;
    }

    public Integer getPopulation(Predicate<Project> hasProject) {
        return getMetrics(hasProject).population;
    }

    public void setPopulation(Predicate<Project> hasProject, Integer population) {
        getMetrics(hasProject).population = population;
    }

    public Double getDisease(Predicate<Project> hasProject) {
        return getMetrics(hasProject).disease;
    }

    public void setDisease(Predicate<Project> hasProject, Double disease) {
        getMetrics(hasProject).disease = disease;
    }

    public Double getCrime(Predicate<Project> hasProject) {
        return getMetrics(hasProject).crime;
    }

    public void setCrime(Predicate<Project> hasProject, Double crime) {
        getMetrics(hasProject).crime = crime;
    }

    public Integer getPollution(Predicate<Project> hasProject) {
        return getMetrics(hasProject).pollution;
    }

    public void setPollution(Predicate<Project> hasProject, Integer pollution) {
        getMetrics(hasProject).pollution = pollution;
    }

    public Integer getCommerce(Predicate<Project> hasProject) {
        return getMetrics(hasProject).commerce;
    }

    public void setCommerce(Predicate<Project> hasProject, Integer commerce) {
        getMetrics(hasProject).commerce = commerce;
    }

    public Boolean getPowered(Predicate<Project> hasProject) {
        return getMetrics(hasProject).powered;
    }

    public void setPowered(Predicate<Project> hasProject, Boolean powered) {
        getMetrics(hasProject).powered = powered;
    }
}

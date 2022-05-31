package com.boydti.discord.apiv1.enums.city;

import com.boydti.discord.commands.info.optimal.CityBranch;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.json.CityBuild;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.math.ArrayUtil;
import com.boydti.discord.util.search.BFSUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.boydti.discord.apiv1.enums.Continent;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.building.Building;
import com.boydti.discord.apiv1.enums.city.building.Buildings;
import com.boydti.discord.apiv1.enums.city.building.CommerceBuilding;
import com.boydti.discord.apiv1.enums.city.building.MilitaryBuilding;
import com.boydti.discord.apiv1.enums.city.building.ResourceBuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.APowerBuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import com.boydti.discord.apiv1.enums.city.project.Project;
import com.boydti.discord.apiv1.enums.city.project.Projects;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.boydti.discord.apiv1.enums.city.building.Buildings.*;

public class JavaCity {
    private byte[] buildings;
    private int numBuildings = -1;
    private double infra;

    private Integer age;
    private Double land;

    private Metrics metrics;

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
        this.age = other.age;
        this.land = other.land;
    }

    public void clearMetrics() {
        if (this.metrics != null) {
            metrics.commerce = null;
            metrics.profit = null;
        }
    }

    public String getMMR() {
        return get(BARRACKS) + "" + get(FACTORY) + "" + get(HANGAR) + "" + get(DRYDOCK);
    }

    public int[] getMMRArray() {
        return new int[]{get(BARRACKS), get(FACTORY), get(HANGAR), get(DRYDOCK)};
    }

    public byte[] getBuildings() {
        return buildings;
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

            for (Building building : POLLUTION_BUILDINGS) {
                int amt = city.buildings[building.ordinal()];
                if (amt == 0) continue;
                pollution += amt * building.pollution(hasProject);
            }

            for (Building building : COMMERCE_BUILDINGS) {
                int amt = city.buildings[building.ordinal()];
                if (amt == 0) continue;
                commerce += amt * ((CommerceBuilding) building).commerce();
            }
            pollution = Math.max(0, pollution);

            double basePopulation = city.getInfra() * 100;

            int hospitals = city.get(HOSPITAL);
            double hospitalModifier;
            if (hospitals > 0) {
                double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
                hospitalModifier = hospitals * hospitalPct;
            } else {
                hospitalModifier = 0;
            }

            int police = city.get(POLICE_STATION);
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
            population = Math.max(10, (int) ((basePopulation - diseaseDeaths - crimeDeaths) * ageBonus));
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
        set(COAL_POWER, city.getImpCoalpower());
        set(OIL_POWER, city.getImpOilpower());
        set(WIND_POWER, city.getImpWindpower());
        set(NUCLEAR_POWER, city.getImpNuclearpower());
        set(COAL_MINE, city.getImpCoalmine());
        set(OIL_WELL, city.getImpOilwell());
        set(URANIUM_MINE, city.getImpUramine());
        set(LEAD_MINE, city.getImpLeadmine());
        set(IRON_MINE, city.getImpIronmine());
        set(BAUXITE_MINE, city.getImpBauxitemine());
        set(FARM, city.getImpFarm());
        set(GAS_REFINERY, city.getImpGasrefinery());
        set(ALUMINUM_REFINERY, city.getImpAluminumrefinery());
        set(MUNITIONS_FACTORY, city.getImpMunitionsfactory());
        set(STEEL_MILL, city.getImpSteelmill());
        set(POLICE_STATION, city.getImpPolicestation());
        set(HOSPITAL, city.getImpHospital());
        set(RECYCLING_CENTER, city.getImpRecyclingcenter());
        set(SUBWAY, city.getImpSubway());
        set(SUPERMARKET, city.getImpSupermarket());
        set(BANK, city.getImpBank());
        set(MALL, city.getImpMall());
        set(STADIUM, city.getImpStadium());
        set(BARRACKS, city.getImpBarracks());
        set(FACTORY, city.getImpFactory());
        set(HANGAR, city.getImpHangars());
        set(DRYDOCK, city.getImpDrydock());
        if (city.getLand() != null) setLand(city.getLand());
        if (city.getAge() != null) setAge(city.getAge());
        initImpTotal();
    }

    public JavaCity(byte[] buildings, double infra, double land, int age) {
        this.buildings = buildings;
        this.age = age;
        this.land = land;
        this.infra = infra;
        initImpTotal();
    }

    public JavaCity() {
        this.buildings = new byte[Buildings.size()];
        age = 0;
        land = 0d;
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
        Map<ResourceType, Double> totalMap = PnwUtil.resourcesToMap(total);
        if (!totalMap.isEmpty()) response.append('\n').append("```" + PnwUtil.resourcesToString(totalMap) + "```");
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

    public JavaCity(com.boydti.discord.apiv1.domains.City city) {
        this.buildings = new byte[Buildings.size()];
        age = city.getAge();
        this.land = Double.parseDouble(city.getLand());

        metrics = new Metrics();
        metrics.disease = city.getDisease();
        metrics.crime = city.getCrime();
        metrics.pollution = (int) city.getPollution();
        metrics.population = (int) city.getPopulation();
        metrics.commerce = (int) city.getCommerce();
        metrics.powered = city.getPowered().equalsIgnoreCase("Yes");

        infra = Double.parseDouble(city.getInfrastructure());

        set(COAL_POWER, Integer.parseInt(city.getImpCoalpower()));
        set(OIL_POWER, Integer.parseInt(city.getImpOilpower()));
        set(WIND_POWER, Integer.parseInt(city.getImpWindpower()));
        set(NUCLEAR_POWER, Integer.parseInt(city.getImpNuclearpower()));
        set(COAL_MINE, Integer.parseInt(city.getImpCoalmine()));
        set(OIL_WELL, Integer.parseInt(city.getImpOilwell()));
        set(URANIUM_MINE, Integer.parseInt(city.getImpUramine()));
        set(LEAD_MINE, Integer.parseInt(city.getImpLeadmine()));
        set(IRON_MINE, Integer.parseInt(city.getImpIronmine()));
        set(BAUXITE_MINE, Integer.parseInt(city.getImpBauxitemine()));
        set(FARM, Integer.parseInt(city.getImpFarm()));
        set(GAS_REFINERY, Integer.parseInt(city.getImpGasrefinery()));
        set(ALUMINUM_REFINERY, Integer.parseInt(city.getImpAluminumrefinery()));
        set(MUNITIONS_FACTORY, Integer.parseInt(city.getImpMunitionsfactory()));
        set(STEEL_MILL, Integer.parseInt(city.getImpSteelmill()));
        set(POLICE_STATION, Integer.parseInt(city.getImpPolicestation()));
        set(HOSPITAL, Integer.parseInt(city.getImpHospital()));
        set(RECYCLING_CENTER, Integer.parseInt(city.getImpRecyclingcenter()));
        set(SUBWAY, Integer.parseInt(city.getImpSubway()));
        set(SUPERMARKET, Integer.parseInt(city.getImpSupermarket()));
        set(BANK, Integer.parseInt(city.getImpBank()));
        set(MALL, Integer.parseInt(city.getImpMall()));
        set(STADIUM, Integer.parseInt(city.getImpStadium()));
        set(BARRACKS, Integer.parseInt(city.getImpBarracks()));
        set(FACTORY, Integer.parseInt(city.getImpFactory()));
        set(HANGAR, Integer.parseInt(city.getImpHangar()));
        set(DRYDOCK, Integer.parseInt(city.getImpDrydock()));

        initImpTotal();
    }

    public JavaCity(JavaCity other) {
        this.buildings = other.buildings.clone();
        this.numBuildings = other.numBuildings;
        this.infra = other.infra;
        this.age = other.age;
        this.land = other.land;
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
//            long longHc = 0;
//            int shift = 0;
//            for (int i = 0; i < buildings.length; i++) {
//                int amt = buildings[i];
//                if (amt != 0) {
//                    longHc ^= ((long) amt) << shift;
//                }
//                Building building = Buildings.get(i);
//                if (building instanceof PowerBuilding) {
//                    shift += 2;
//                } else if (building instanceof ResourceBuilding && ((ResourceBuilding) building).resource().isRaw()) {
//                    shift += 4;
//                } else {
//                    shift += 3;
//                }
//            }
            hashCode = Arrays.hashCode(buildings);
        }
        return hashCode;
    }

    public void zeroNonMilitary() {
        for (int i = 0; i < buildings.length; i++) {
            if (Buildings.get(i) instanceof MilitaryBuilding) continue;
            numBuildings -= buildings[i];
            buildings[i] = 0;
        }
    }

    public JavaCity optimalBuild(DBNation nation, long timeout) {
        return optimalBuild(nation.getContinent(), nation.getRads(), nation.getCities(), nation::hasProject, timeout);
    }

    public JavaCity optimalBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, long timeout) {
        return optimalBuild(continent, rads, numCities, hasProject, timeout, f -> f, null);
    }

    public JavaCity optimalBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, long timeout,
                                 Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc, Function<JavaCity, Boolean> goal) {
        Predicate<Project> finalHasProject = Projects.hasProjectCached(hasProject);
        Function<JavaCity, Double> valueFunction = javaCity -> {
            return javaCity.profitConvertedCached(rads, finalHasProject, numCities) / javaCity.getImpTotal();
        };
        valueFunction = modifyValueFunc.apply(valueFunction);

        boolean tradeCenter = hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER);

        return optimalBuild(continent, tradeCenter, valueFunction, goal, finalHasProject, timeout);
    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, int days, long timeout) {
        return roiBuild(continent, rads, numCities, hasProject, days, timeout, f -> f, null);
    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, int days, long timeout, Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc, Function<JavaCity, Boolean> goal) {
        JavaCity origin = new JavaCity(this);
        origin.setAge(this.getAge() + days / 2);

        JavaCity zeroed = new JavaCity(origin);
        zeroed.zeroNonMilitary();

        int maxImp = (int) (this.getInfra() / 50);
        if (maxImp == zeroed.getImpTotal()) return zeroed;

        double baseProfit = origin.profitConvertedCached(rads, hasProject, numCities);
        double zeroedProfit = zeroed.profitConvertedCached(rads, hasProject, numCities);

        int baseImp = zeroed.getImpTotal();
        int impOverZero = maxImp - zeroed.getImpTotal();
        double baseProfitNonTax = (baseProfit - zeroedProfit);
        double profitPerImp = baseProfitNonTax / impOverZero;

        Predicate<Project> finalHasProject = Projects.hasProjectCached(hasProject);
        Function<JavaCity, Double> valueFunction = javaCity -> {
            double newProfit = javaCity.profitConvertedCached(rads, finalHasProject, numCities);

            double cost = javaCity.calculateCostConverted(origin);

            int imp = javaCity.getImpTotal() - baseImp;
            double expectedProfit = profitPerImp * imp;
            return ((newProfit - zeroedProfit) - expectedProfit) * days - cost;
        };

        valueFunction = modifyValueFunc.apply(valueFunction);

        boolean tradeCenter = finalHasProject.test(Projects.INTERNATIONAL_TRADE_CENTER);
        JavaCity optimal = zeroed.optimalBuild(continent, tradeCenter, valueFunction, goal, finalHasProject, timeout);
        return optimal;
    }

    public JavaCity optimalBuild(Continent continent, boolean tradeCenter, Function<JavaCity, Double> valueFunction, Predicate<Project> hasProject, long timeout) {
        return optimalBuild(continent, tradeCenter, valueFunction, null, hasProject, timeout);
    }

    public JavaCity optimalBuild(Continent continent, boolean tradeCenter, Function<JavaCity, Double> valueFunction, Function<JavaCity, Boolean> goal, Predicate<Project> hasProject, long timeout) {
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

        Map.Entry<JavaCity, Integer> optimized = BFSUtil.search(goal2, valueFunction2, valueCompletionFunction, searchServices, searchServices, new AbstractMap.SimpleEntry<>(origin, 4), queue, timeout);

        return optimized == null ? null : optimized.getKey();
    }

    public double profitConvertedCached(double rads, Predicate<Project> hasProject, int numCities) {
        if (metrics != null && metrics.profit != null) {
            return metrics.profit;
        }
        double profit = profitConverted2(rads, hasProject, numCities);
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
        powered += get(WIND_POWER) * WIND_POWER.infraMax();
        powered += get(COAL_POWER) * COAL_POWER.infraMax();
        powered += get(OIL_POWER) * OIL_POWER.infraMax();
        powered += get(NUCLEAR_POWER) * NUCLEAR_POWER.infraMax();

        return powered;
    }

    public double[] profit(DBNation nation, double[] myProfit) {
        Predicate<Project> hasProject = p -> p != null && p.get(nation) > 0;

        double radIndex = nation.getRads();
        double rads = (1 + (radIndex * (-0.001)));

        int numCities = nation.getCities();

        return profit(rads, hasProject, myProfit, numCities);
    }

    public double profitConverted2(double rads, Predicate<Project> hasProject, int numCities) {
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
                        unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).infraMax();
                    }
                }
                profit += building.profitConverted(rads, hasProject, this, amt);
            }
            for (int ordinal = GAS_REFINERY.ordinal(); ordinal < buildings.length; ordinal++) {
                int amt = buildings[ordinal];
                if (amt == 0) continue;

                Building building = Buildings.get(ordinal);
                profit += building.profitConverted(rads, hasProject, this, amt);
            }
        }

        for (int ordinal = 4; ordinal < Buildings.GAS_REFINERY.ordinal(); ordinal++) {
            int amt = buildings[ordinal];
            if (amt == 0) continue;

            Building building = Buildings.get(ordinal);
            profit += building.profitConverted(rads, hasProject, this, amt);
        }

        // if any commerce buildings

        int commerce = getMetrics(hasProject).commerce;
        if (commerce > 100) {
            boolean project = hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER);
            if (project) {
                commerce = Math.min(115, metrics.commerce);
            } else {
                commerce = Math.min(100, metrics.commerce);
            }
        }

        double newPlayerBonus = numCities < 10 ? Math.max(1, (200d - ((numCities - 1) * 10d)) * 0.01) : 1;

        double income = Math.max(0, (((commerce * 0.02) * 0.725) + 0.725) * metrics.population * newPlayerBonus);

        profit += income;

        profit -= PnwUtil.convertedTotalNegative(ResourceType.FOOD, metrics.population * 0.001);

        return profit;
    }

    public double[] profit(double rads, Predicate<Project> hasProject, double[] profitBuffer, int numCities) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

        boolean powered = true;
        if (metrics != null && metrics.powered != null) powered = metrics.powered;
        if (powered && getPoweredInfra() < infra) powered = false;
        int unpoweredInfra = (int) Math.ceil(infra);
        for (int ordinal = 0; ordinal < buildings.length; ordinal++) {
            int amt = buildings[ordinal];
            if (amt == 0) continue;

            Building building = Buildings.get(ordinal);

            if (!powered) {
                if (building instanceof CommerceBuilding || building instanceof MilitaryBuilding || (building instanceof ResourceBuilding && ((AResourceBuilding) building).resource().isManufactured())) {
                    continue;
                }
            }
            profitBuffer = building.profit(rads, hasProject, this, profitBuffer);
            if (building instanceof APowerBuilding) {
                for (int i = 0; i < amt; i++) {
                    if (unpoweredInfra > 0) {
                        profitBuffer = ((APowerBuilding) building).consumption(unpoweredInfra, profitBuffer);
                        unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).infraMax();
                    }
                }
            }
        }
        boolean project = hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER);
        int commerce = getMetrics(hasProject).commerce;
        if (project) {
            commerce = Math.min(115, metrics.commerce);
        } else {
            commerce = Math.min(100, metrics.commerce);
        }

        double newPlayerBonus = Math.max(1, (200d - ((numCities - 1) * 10d)) / 100d);

        double income = Math.max(0, (((commerce/50d) * 0.725) + 0.725) * metrics.population * newPlayerBonus);

        profitBuffer[ResourceType.MONEY.ordinal()] += income;

        profitBuffer[ResourceType.FOOD.ordinal()] -= metrics.population / 1000d;

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
                    total += Buildings.get(i).costConverted((amt - amtOther) * 0.5);
                } else {
                    total += Buildings.get(i).costConverted(amt - amtOther);
                }
            }
        }

        if (this.getInfra() > from.getInfra()) {
            total += PnwUtil.calculateInfra(from.getInfra(), getInfra());
        }
        if (!Objects.equals(getLand(), from.getLand())) {
            total += PnwUtil.calculateLand(from.getLand(), getLand());
        }
        return total;
    }

    public double[] calculateCost(JavaCity from, double[] buffer) {
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

        if (this.getInfra() > from.getInfra()) {
            buffer[ResourceType.MONEY.ordinal()] += PnwUtil.calculateInfra(from.getInfra(), getInfra());
        }
        if (!Objects.equals(getLand(), from.getLand())) {
            buffer[ResourceType.MONEY.ordinal()] += PnwUtil.calculateLand(from.getLand(), getLand());
        }
        return buffer;
    }

    public Map<ResourceType, Double> calculateImprovementCost(Map<ResourceType, Double> total) {
        if (total == null) {
            total = new HashMap<>();
        }
        for (int i = 0; i < buildings.length; i++) {
            int amt = buildings[i];
            if (amt != 0) {
                total = PnwUtil.addResourcesToA(total, Buildings.get(i).cost());
            }
        }
        return total;
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
        return age == null ? 0 : age;
    }

    public double getFreeInfra() {
        return getInfra() - getRequiredInfra();
    }

    public int getFreeSlots() {
        return (int) (getFreeInfra() / 50);
    }

    public Double getLand() {
        return land == null ? infra : land;
    }
    public JavaCity setInfra(double infra) {
        this.infra = infra;
        return this;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public JavaCity setLand(Double land) {
        this.land = land;
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

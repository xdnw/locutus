package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.commands.info.optimal.CityBranch;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.CityNode;
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
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
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

public class JavaCity implements ICity {
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

    public void setBuildings(ICity other) {
        for (Building building : Buildings.values()) {
            set(building, other.getBuilding(building));
        }
    }

    public void set(JavaCity other) {
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

    public int getNukeTurn() {
        return nuke_turn;
    }

    public void setNuke_turn(int nuke_turn) {
        this.nuke_turn = nuke_turn;
    }

    public int[] getMMRArray() {
        return new int[]{getBuilding(Buildings.BARRACKS), getBuilding(Buildings.FACTORY), getBuilding(Buildings.HANGAR), getBuilding(Buildings.DRYDOCK)};
    }

    public byte[] getBuildings() {
        return buildings;
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

            this.pollution = PW.City.getNukePollution(city.nuke_turn);

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
                    commerce += 2;
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

            int hospitals = city.getBuilding(Buildings.HOSPITAL);
            double hospitalModifier;
            if (hospitals > 0) {
                double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
                hospitalModifier = hospitals * hospitalPct;
            } else {
                hospitalModifier = 0;
            }

            int police = city.getBuilding(Buildings.POLICE_STATION);
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
            double crimeDeaths = Math.max((crime * 0.1) * (basePopulation) - 25, 0);

            double ageBonus = (1 + Math.log(Math.max(1, city.getAgeDays())) * 0.0666666666666666666666666666666);
            population = (int) Math.round(Math.max(10, ((basePopulation - diseaseDeaths - crimeDeaths) * ageBonus)));
        }
    }

    public int getMaxCommerce(Predicate<Project> hasProject) {
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
        return maxCommerce;
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
        initImpTotal();
    }

    public JavaCity(CityBuild city) {
        this.buildings = new byte[PW.City.Building.SIZE];
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
        this.buildings = new byte[PW.City.Building.SIZE];
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
        return instructions(map, total, false, false);
    }

    public String instructions(Map<Integer, JavaCity> fromMap, double[] total, boolean isBulk, boolean dontSellInfra) {
        Map<Integer, Double> landPurchases = new HashMap<>();
        Map<Integer, Double> infraPurchases = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> entry : fromMap.entrySet()) {
            JavaCity from = entry.getValue();
            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, this.calculateCost(from));
            for (int i = 0; i < total.length; i++) {
                total[i] = Math.max(0, total[i]);
            }
            if (getLand() > from.getLand()) {
                landPurchases.put(entry.getKey(), getLand());
            }
            if (getInfra() > from.getInfra()) {
                infraPurchases.put(entry.getKey(), getInfra());
            }
        }

        Function<Integer, String> cityName = id -> id > 0 ? "<" + Settings.INSTANCE.PNW_URL() + "/city/id=" + id + ">" : "your new city";

        StringBuilder response = new StringBuilder();
        int i = 0;
        response.append(++i+". Ensure you have the following resources:");
        Map<ResourceType, Double> totalMap = ResourceType.resourcesToMap(total);
        if (!totalMap.isEmpty()) response.append('\n').append("```" + ResourceType.resourcesToString(totalMap) + "```");
        if (!infraPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : infraPurchases.entrySet()) {
                if (entry.getValue() > 0) {
                    response.append('\n').append(++i + ". (to buy) Enter @" + getInfra() + " infra in " + cityName.apply(entry.getKey()));
                }
            }
        }

        if (!landPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : landPurchases.entrySet()) {
                if (entry.getValue() > 0) {
                    response.append('\n').append(++i + ". (to buy) Enter @" + getLand() + " land in " + cityName.apply(entry.getKey()));
                }
            }
        }
        String importUrl;
        if (fromMap.size() == 1) {
            int cityId = fromMap.keySet().iterator().next();
            String cityIdStr = String.valueOf(cityId);
            if (cityId <= 0) {
                cityIdStr = "CITY_ID> (replace CITY_ID with the city id)";
            }
            importUrl = "<" + Settings.INSTANCE.PNW_URL() + "/city/improvements/import/id=" + cityId + (cityId > 0 ? ">" : "");
        } else if (isBulk) {
            importUrl = "<" + Settings.INSTANCE.PNW_URL() + "/city/improvements/bulk-import/>";
        } else {
            importUrl = "<" + Settings.INSTANCE.PNW_URL() + "/city/improvements/import/=CITY_ID> for each city in " + fromMap.keySet();
        }
        response.append('\n').append(++i+". Go to " + importUrl);
        response.append('\n').append(++i+". Copy the following build:\n");
        response.append("```json\n").append(toCityBuild().toString()).append("\n```");
        response.append('\n').append(++i+". Check the checkbox and click the submit import button");
        response.append('\n').append(++i+". If you are missing any resources or money, obtain them and try again");
        if (!infraPurchases.isEmpty()) {
            for (Map.Entry<Integer, Double> entry : infraPurchases.entrySet()) {
                if (entry.getValue() < 0 && !dontSellInfra) {
                    response.append('\n').append(++i + ". (to sell) Enter @" + getInfra() + " infra in <" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey() + ">");
                }
            }
        }

        response.append('\n').append("7. Repurchase military units.");

        return response.toString().trim();
    }

    public CityBuild toCityBuild() {
        return CityBuild.of(toJson());
    }

    public JavaCity(City city) {
        this.buildings = new byte[PW.City.Building.SIZE];
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

    public boolean equals(JavaCity other, boolean checkInfra, boolean checkLand, boolean checkAge) {
        for (Building building : Buildings.values()) {
            if (getBuilding(building) != other.getBuilding(building)) {
                return false;
            }
        }
        if (checkInfra && Math.round(getInfra() * 100) != Math.round(other.getInfra() * 100)) {
            return false;
        }
        if (checkLand && Math.round(getLand() * 100) != Math.round(other.getLand() * 100)) {
            return false;
        }
        if (checkAge && getAgeDays() != other.getAgeDays()) {
            return false;
        }
        return true;
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

    public JavaCity optimalBuild(DBNation nation, long timeout, boolean selfSufficient, Double infraLow) {
        // Continent continent,
        // int numCities,
        // Function<CityNode, Double> valueFunction,
        // Function<CityNode, Boolean> goal,
        // Predicate<Project> hasProject,
        // long timeout, double rads,
        // boolean selfSufficient,
        // double grossModifier,
        // Double infraLow
        return optimalBuild(nation.getContinent(),
                nation.getCities(),
                CityNode::getRevenueConverted,
                null,
                nation::hasProject,
                timeout,
                nation.getRads(),
                selfSufficient,
                nation.getGrossModifier(),
                infraLow);
    }

//    public JavaCity optimalBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, long timeout, boolean selfSufficient,
//                                 Function<Function<JavaCity, Double>, Function<JavaCity, Double>> modifyValueFunc, Function<JavaCity, Boolean> goal) {
//        Predicate<Project> finalHasProject = Projects.optimize(hasProject);
//        Function<JavaCity, Double> valueFunction = javaCity -> {
//            return javaCity.profitConvertedCached(continent, rads, finalHasProject, numCities, grossModifier) / javaCity.getNumBuildings();
//        };
//        valueFunction = modifyValueFunc.apply(valueFunction);
//        return optimalBuild(continent, valueFunction, goal, finalHasProject, timeout, rads, selfSufficient);
//    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, int days, long timeout, boolean selfSufficient, Double infraLow) {
        return roiBuild(continent, rads, numCities, hasProject, grossModifier, days, timeout, selfSufficient, infraLow, f -> f, null);
    }

    public JavaCity roiBuild(Continent continent, double rads, int numCities, Predicate<Project> hasProject, double grossModifier, int days, long timeout, boolean selfSufficient, Double infraLow, Function<Function<CityNode, Double>, Function<CityNode, Double>> modifyValueFunc, Function<CityNode, Boolean> goal) {
        JavaCity origin = new JavaCity(this);
        origin.setAge(this.getAgeDays() + days / 2);

        JavaCity zeroed = new JavaCity(origin);
        zeroed.zeroNonMilitary().setOptimalPower(continent);

        int maxImp = (int) (this.getInfra() / 50);
        if (maxImp == zeroed.getNumBuildings()) return zeroed;

        double baseProfit = origin.profitConvertedCached(continent, rads, hasProject, numCities, grossModifier);
        double zeroedProfit = zeroed.profitConvertedCached(continent, rads, hasProject, numCities, grossModifier);

        int baseImp = zeroed.getNumBuildings();
        int impOverZero = maxImp - zeroed.getNumBuildings();
        double baseProfitNonTax = (baseProfit - zeroedProfit);
        double profitPerImp = baseProfitNonTax / impOverZero;

        Predicate<Project> finalHasProject = Projects.optimize(hasProject);
        Function<CityNode, Double> valueFunction = javaCity -> {
            double newProfit = javaCity.getRevenueConverted();
            double cost = javaCity.calculateCostConverted(origin);
            return (newProfit * days - cost) / javaCity.getNumBuildings();
        };
        valueFunction = modifyValueFunc.apply(valueFunction);
        JavaCity optimal = zeroed.optimalBuild(continent, numCities, valueFunction, goal, finalHasProject, timeout, rads, selfSufficient, grossModifier, infraLow);
        return optimal;
    }

    public JavaCity zeroPower() {
        for (int i = 0; i < buildings.length; i++) {
            if (!(Buildings.get(i) instanceof PowerBuilding)) continue;
            numBuildings -= buildings[i];
            buildings[i] = 0;
        }
        return this;
    }

    public JavaCity setOptimalPower(Continent continent) {
        int nuclear = 0;
        int coal = 0;
        int oil = 0;
        int wind = 0;
        double infra = getInfra();
        while (infra > 500 || (getInfra() > 2000 && infra > 250)) {
            nuclear++;
            infra -= Buildings.NUCLEAR_POWER.getInfraMax();
        }
        while (infra > 250) {
            if (continent.canBuild(Buildings.COAL_POWER)) {
                coal++;
                infra -= Buildings.COAL_POWER.getInfraMax();
            } else if (continent.canBuild(Buildings.OIL_POWER)) {
                oil++;
                infra -= Buildings.OIL_POWER.getInfraMax();
            } else {
                break;
            }
        }
        while (infra > 0) {
            wind++;
            infra -= Buildings.WIND_POWER.getInfraMax();
        }
        set(Buildings.NUCLEAR_POWER, nuclear);
        set(Buildings.COAL_POWER, coal);
        set(Buildings.OIL_POWER, oil);
        set(Buildings.WIND_POWER, wind);
        return this;
    }

    public JavaCity optimalBuild(Continent continent, int numCities, Function<CityNode, Double> valueFunction, Function<CityNode, Boolean> goal, Predicate<Project> hasProject, long timeout, double rads, boolean selfSufficient, double grossModifier, Double infraLow) {
        JavaCity copy = new JavaCity(this);
        CityNode.CachedCity cached = new CityNode.CachedCity(this, continent, selfSufficient, hasProject, numCities, grossModifier, rads, infraLow);
        CityBranch searchServices = new CityBranch(cached);
        CityNode optimized = searchServices.toOptimal(valueFunction, goal, timeout);
        if (optimized != null) copy.setBuildings(optimized);
        return optimized == null ? null : copy;
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

    @Override
    public int getPoweredInfra() {
        int powered = 0;
        powered += getBuilding(Buildings.WIND_POWER) * Buildings.WIND_POWER.getInfraMax();
        powered += getBuilding(Buildings.COAL_POWER) * Buildings.COAL_POWER.getInfraMax();
        powered += getBuilding(Buildings.OIL_POWER) * Buildings.OIL_POWER.getInfraMax();
        powered += getBuilding(Buildings.NUCLEAR_POWER) * Buildings.NUCLEAR_POWER.getInfraMax();

        return powered;
    }

    public double profitConverted2(Continent continent, double rads, Predicate<Project> hasProject, int numCities, double grossModifier) {
        return PW.City.profitConverted(continent, rads, hasProject, numCities, grossModifier, this);
    }

    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, double[] profitBuffer, int numCities, double grossModifier, int turns) {
        return profit(continent, rads, date, hasProject, profitBuffer, numCities, grossModifier, false, turns);
    }
    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, double[] profitBuffer, int numCities, double grossModifier, boolean forceUnpowered, int turns) {
        return PW.City.profit(continent, rads, date, hasProject, profitBuffer, numCities, grossModifier, forceUnpowered, turns, this);
    }

    public double[] calculateCost(JavaCity from) {
        return calculateCost(from, new double[ResourceType.values.length]);
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

    public int getNumBuildings() {
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

    public int getBuildingOrdinal(int ordinal) {
        return buildings[ordinal];
    }

    public int getBuilding(Building building) {
        return getBuildingOrdinal(building.ordinal());
    }

    public int getRequiredInfra() {
        return getNumBuildings() * 50;
    }

    public double getInfra() {
        return infra;
    }

    public int getAgeDays() {
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

    public double getLand() {
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

    public void setDateCreated(long dateCreated) {
        this.dateCreated = dateCreated;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public JavaCity setLand(Double land) {
        this.land_ = land;
        return this;
    }

    @Override
    public int calcPopulation(Predicate<Project> hasProject) {
        return getMetrics(hasProject).population;
    }

    public void setPopulation(Predicate<Project> hasProject, Integer population) {
        getMetrics(hasProject).population = population;
    }

    @Override
    public double calcDisease(Predicate<Project> hasProject) {
        return getMetrics(hasProject).disease;
    }

    public void setDisease(Predicate<Project> hasProject, Double disease) {
        getMetrics(hasProject).disease = disease;
    }

    @Override
    public double calcCrime(Predicate<Project> hasProject) {
        return getMetrics(hasProject).crime;
    }

    public void setCrime(Predicate<Project> hasProject, Double crime) {
        getMetrics(hasProject).crime = crime;
    }

    @Override
    public int calcPollution(Predicate<Project> hasProject) {
        return getMetrics(hasProject).pollution;
    }

    public void setPollution(Predicate<Project> hasProject, Integer pollution) {
        getMetrics(hasProject).pollution = pollution;
    }

    @Override
    public int calcCommerce(Predicate<Project> hasProject) {
        return getMetrics(hasProject).commerce;
    }

    public void setCommerce(Predicate<Project> hasProject, Integer commerce) {
        getMetrics(hasProject).commerce = commerce;
    }

    @Override
    public Boolean getPowered() {
        return metrics != null ? metrics.powered : null;
    }

    public void setPowered(Predicate<Project> hasProject, Boolean powered) {
        getMetrics(hasProject).powered = powered;
    }
}

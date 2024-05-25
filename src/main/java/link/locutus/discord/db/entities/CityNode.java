package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;

import java.util.*;
import java.util.function.*;

public class CityNode implements ICity {
    private final byte[] modifiable;
    private double revenue = 0;
    private int numBuildings;
    private int commerce;
    private int pollution;

    private int index;

    private CachedCity cached;

    private static int modIs = 4;
    private static int modIe = Buildings.values().length - 4;
    private static int LENGTH = modIe - modIs;

    private static final BiFunction<CityNode, CachedCity, Integer>[] getNumBuildings;
    static {
        getNumBuildings = new BiFunction[Buildings.values().length];
        for (int i = 0; i < modIs; i++) {
            int finalI = i;
            getNumBuildings[i] = (n, c) -> c.getBuildingOrdinal(finalI);
        }
        for (int i = modIs; i < modIe; i++) {
            int finalI = i;
            getNumBuildings[i] = (n, c) -> (int) n.modifiable[finalI - modIs];
        }
        for (int i = modIe; i < Buildings.values().length; i++) {
            int finalI = i;
            getNumBuildings[i] = (n, c) -> c.getBuildingOrdinal(finalI);
        }
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getFreeSlots() {
        return cached.maxSlots - numBuildings;
    }

    public int getIndex() {
        return index;
    }

    public int getCommerce() {
        return commerce;
    }

    public int getPollution() {
        return pollution;
    }

    public double calculateCostConverted(JavaCity from) {
        return caclulateBuildingCostConverted(from);
    }

    public CachedCity getCached() {
        return cached;
    }

    public static class CachedCity {
        private final Consumer<CityNode>[] ADD_BUILDING;
        private final Consumer<CityNode>[] REMOVE_BUILDING;

        private final byte[] buildings;
        private final int basePollution;
        private final List<Building> existing;
        private final List<Building> buildable;
        private final Building[] buildableArr;
        private final Building[] buildablePollution;
        private final int maxCommerce;
        private final int baseCommerce;
        private final double basePopulation;
        private final double ageBonus;
        private final double baseDisease;
        private final double newPlayerBonus;
        private final double[] crimeCache;
        private final double[] commerceIncome;
        private final double food;
        private final double baseProfitConverted;
        private final double[] baseProfit;
        private final double hospitalPct;
        private final double policePct;
        private final int ageDays;
        private final double buildingInfra;
        private final double land;
        private final int numBuildings;
        private final Predicate<Project> hasProject;
        private final double rads;
        private final Continent continent;
        private final boolean selfSufficient;
        private final int maxSlots;
        private final double infraLow;
        private int maxIndex;

        public int getBuildingOrdinal(int ordinal) {
            return buildings[ordinal];
        }

        public CachedCity(JavaCity city, Continent continent, boolean selfSufficient, Predicate<Project> hasProject, int numCities, double grossModifier, double rads, Double infraLow) {
            this.hasProject = hasProject;
            this.selfSufficient = selfSufficient;
            this.ageDays = city.getAgeDays();
            this.rads = rads;
            this.continent = continent;
            this.buildingInfra = city.getInfra();
            this.land = city.getLand();
            this.buildings = city.getBuildings();
            this.maxSlots = (int) (buildingInfra / 50);
            int basePollution = PW.City.getNukePollution(city.getNukeTurn());
            this.infraLow = infraLow == null ? city.getInfra() : infraLow;

            this.existing = new ArrayList<>();
            this.buildable = new ArrayList<>();
            for (Building building : Buildings.values()) {
                if (building instanceof PowerBuilding) {
                    existing.add(building);
                } else if (building instanceof MilitaryBuilding) {
                    existing.add(building);
                } else {
                    buildable.add(building);
                }
            }
            buildable.removeIf(f -> !f.canBuild(continent));
            if (selfSufficient) {
                buildable.removeIf(f -> {
                    if (f instanceof ResourceBuilding rssBuild) {
                        if (rssBuild.getResourceProduced().isManufactured()) {
                            for (ResourceType req : rssBuild.getResourceTypesConsumed()) {
                                if (!continent.hasResource(req)) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                });
            }

            this.buildableArr = buildable.toArray(new Building[0]);
            this.buildablePollution = Arrays.stream(buildableArr).filter(f -> f.pollution(hasProject) > 0).toArray(Building[]::new);

            this.maxCommerce = city.getMaxCommerce(hasProject);
            this.baseCommerce = hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE) ? 2 : 0;

            this.basePopulation = this.infraLow * 100;
            this.ageBonus = (1 + Math.log(Math.max(1, city.getAgeDays())) * 0.0666666666666666666666666666666);
            this.baseDisease = ((0.01 * MathMan.sqr((this.infraLow * 100) / (city.getLand() + 0.001)) - 25) * 0.01d) + (this.infraLow * 0.001);
            this.newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

            this.crimeCache = new double[126];
            this.commerceIncome = new double[126];
            for (int i = 0; i <= 125; i++) {
                crimeCache[i] = (MathMan.sqr(103 - i) + (this.infraLow * 100))*(0.000009d);
                commerceIncome[i] = Math.max(0, (((i * 0.02) * 0.725) + 0.725) * newPlayerBonus) * grossModifier;
            }

            this.hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
            this.policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;

            double baseProfitConverted = 0;
            this.baseProfit = ResourceType.getBuffer();
            double infraUnpowered = this.infraLow;
            int numBuildings = 0;
            for (Building building : existing) {
                int num = city.getBuilding(building);
                if (num > 0) {
                    numBuildings += num;
                    basePollution += num * building.pollution(hasProject);
                    if (building instanceof PowerBuilding power) {
                        for (int i = 0; i < num; i++) {
                            if (infraUnpowered > 0) {
                                baseProfitConverted += power.consumptionConverted((int) infraUnpowered);
                                power.consumption((int) infraUnpowered, this.baseProfit, 12);
                                infraUnpowered -= power.getInfraMax();
                            }
                        }
                    }
                    baseProfitConverted += building.profitConverted(continent, rads, hasProject, city, num);
                    building.profit(continent, rads, -1L, hasProject, city, this.baseProfit, 12, num);
                }
            }
            this.numBuildings = numBuildings;
            this.basePollution = basePollution;
            this.food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;
            this.baseProfit[ResourceType.FOOD.ordinal()] -= food;
            baseProfitConverted -= ResourceType.convertedTotalNegative(ResourceType.FOOD, food);
            this.baseProfitConverted = baseProfitConverted;

            ADD_BUILDING = new Consumer[modIe - modIs];
            REMOVE_BUILDING = new Consumer[modIe - modIs];
            for (int i = modIs, j = 0; i < modIe; i++, j++) {
                int finalJ = j;
                Building building = Buildings.get(i);
                int addCommerce = building.getCommerce();
                int addPollution = building.getPollution(hasProject);
                if (addCommerce != 0) {
                    if (addPollution != 0) {
                        ADD_BUILDING[j] = (n) -> {
                            n.commerce += addCommerce;
                            n.pollution += addPollution;
                            n.numBuildings++;
                            n.modifiable[finalJ]++;
                            n.revenue = 0;
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            n.commerce -= addCommerce;
                            n.pollution -= addPollution;
                            n.numBuildings--;
                            n.modifiable[finalJ]--;
                            n.revenue = 0;
                        };
                    } else {
                        ADD_BUILDING[j] = (n) -> {
                            n.commerce += addCommerce;
                            n.numBuildings++;
                            n.modifiable[finalJ]++;
                            n.revenue = 0;
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            n.commerce -= addCommerce;
                            n.numBuildings--;
                            n.modifiable[finalJ]--;
                            n.revenue = 0;
                        };
                    }
                } else if (addPollution != 0) {
                    ADD_BUILDING[j] = (n) -> {
                        n.pollution += addPollution;
                        n.numBuildings++;
                        n.modifiable[finalJ]++;
                        n.revenue = 0;
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        n.pollution -= addPollution;
                        n.numBuildings--;
                        n.modifiable[finalJ]--;
                        n.revenue = 0;
                    };
                } else {
                    ADD_BUILDING[j] = (n) -> {
                        n.numBuildings++;
                        n.modifiable[finalJ]++;
                        n.revenue = 0;
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        n.numBuildings--;
                        n.modifiable[finalJ]--;
                        n.revenue = 0;
                    };
                }
            }
        }

        public CityNode create() {
            CityNode node = new CityNode(this, new byte[LENGTH], numBuildings, baseCommerce, basePollution, 0);
            return node;
        }

        public Continent getContinent() {
            return continent;
        }

        public double getRads() {
            return rads;
        }

        public Predicate<Project> hasProject() {
            return hasProject;
        }

        public boolean selfSufficient() {
            return selfSufficient;
        }

        public int getMaxCommerce() {
            return maxCommerce;
        }

        public void setMaxIndex(int length) {
            this.maxIndex = length;
        }

        public int getMaxIndex() {
            return maxIndex;
        }
    }

    public CityNode(CachedCity origin, byte[] modifiable, int numBuildings, int commerce, int pollution, int index) {
        this.cached = origin;
        this.modifiable = modifiable;
        this.numBuildings = numBuildings;
        this.commerce = commerce;
        this.pollution = pollution;
        this.index = index;
    }

    public CityNode clone() {
        return new CityNode(cached, modifiable.clone(), numBuildings, commerce, pollution, index);
    }

    @Override
    public Boolean getPowered() {
        return true;
    }

    @Override
    public int getBuilding(Building building) {
        return getBuildingOrdinal(building.ordinal());
    }

    @Override
    public int getPoweredInfra() {
        return Integer.MAX_VALUE;
    }

    @Override
    public double getInfra() {
        return cached.buildingInfra;
    }

    @Override
    public double getLand() {
        return cached.land;
    }

    @Override
    public int getAgeDays() {
        return cached.ageDays;
    }

    @Override
    public int getBuildingOrdinal(int ordinal) {
        return getNumBuildings[ordinal].apply(this, cached);
    }

    public void add(Building building) {
        cached.ADD_BUILDING[building.ordinal() - modIs].accept(this);
    }

    public void remove(Building building) {
        cached.REMOVE_BUILDING[building.ordinal() - modIs].accept(this);
    }

    public double getRevenueConverted() {
        if (revenue != 0) {
            return revenue;
        }
        int population = calcPopulation(cached.hasProject);
        double revenue = cached.baseProfitConverted + cached.commerceIncome[commerce] * population;
        for (int i = 0; i < modifiable.length; i++) {
            byte amt = modifiable[i];
            if (amt == 0) continue;
            Building building = Buildings.get(i + modIs);
            revenue += building.profitConverted(cached.continent, cached.rads, cached.hasProject, this, amt);
        }
        return this.revenue = revenue;
    }

    public double[] profit(double[] profitBuffer) {
        for (int i = 0; i < profitBuffer.length; i++) {
            profitBuffer[i] = cached.baseProfit[i];
        }
        int population = calcPopulation(cached.hasProject);
        double revenue = cached.baseProfitConverted + cached.commerceIncome[commerce] * population;
        profitBuffer[0] += revenue;

        for (int i = 0; i < modifiable.length; i++) {
            byte amt = modifiable[i];
            if (amt == 0) continue;
            Building building = Buildings.get(i + modIs);
            building.profit(cached.continent, cached.rads, -1L, cached.hasProject, this, profitBuffer, 12, amt);
        }
        return profitBuffer;
    }

    @Override
    public int calcPopulation(Predicate<Project> hasProject) {
        double disease = calcDisease(hasProject);
        double crime = calcCrime(hasProject);
        return calcPopulation(disease, crime);
    }

    public int calcPopulation(double disease, double crime) {
        double diseaseDeaths = ((disease * 0.01) * cached.basePopulation);
        double crimeDeaths = Math.max((crime * 0.1) * cached.basePopulation - 25, 0);
        return (int) Math.round(Math.max(10, ((cached.basePopulation - diseaseDeaths - crimeDeaths) * cached.ageBonus)));
    }

    @Override
    public double calcDisease(Predicate<Project> hasProject) {
        int hospitals = this.modifiable[Buildings.HOSPITAL.ordinal() - modIs];
        double hospitalModifier;
        if (hospitals > 0) {
            hospitalModifier = hospitals * cached.hospitalPct;
        } else {
            hospitalModifier = 0;
        }
        double pollutionModifier = pollution * 0.05;
        return Math.max(0, cached.baseDisease - hospitalModifier + pollutionModifier);
    }

    @Override
    public double calcCrime(Predicate<Project> hasProject) {
        int police = this.modifiable[Buildings.POLICE_STATION.ordinal() - modIs];
        double policeMod;
        if (police > 0) {
            policeMod = police * (cached.policePct);
        } else {
            policeMod = 0;
        }
        return Math.max(0, cached.crimeCache[commerce] - policeMod);
    }

    @Override
    public int calcCommerce(Predicate<Project> hasProject) {
        return commerce;
    }

    @Override
    public int calcPollution(Predicate<Project> hasProject) {
        return pollution;
    }

    @Override
    public int getNumBuildings() {
        return numBuildings;
    }
}

package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;

import java.util.*;
import java.util.function.*;

public final class CityNode implements INationCity {
    private final byte[] modifiable;
    private double revenueFull = Double.MIN_VALUE;
    private double baseRevenue = 0;

    private char numBuildings;
    private byte commerce;
    private char pollution;

    private int index;

    private final CachedCity cached;

    private static final int modIs = 4;
    private static final int modIe = Buildings.values().length - 4;
    private static final int LENGTH = modIe - modIs;
    private static final int HOSPITAL_MOD_INDEX = Buildings.HOSPITAL.ordinal() - modIs;
    private static final int POLICE_MOD_INDEX  = Buildings.POLICE_STATION.ordinal() - modIs;


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

    @Override
    public int getNuke_turn() {
        return 0;
    }

    @Override
    public long getCreated() {
        return cached.dateCreated;
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
        private final char basePollution;
        private final List<Building> existing;
        private final List<Building> buildable;
        private final Building[] buildableArr;
        private final Building[] buildablePollution;
        private final int maxCommerce;
        private final byte baseCommerce;
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
        private final char numBuildings;
        private final Predicate<Project> hasProject;
        private final double rads;
        private final Continent continent;
        private final boolean selfSufficient;
        private final int maxSlots;
        private final double infraLow;
        private final double[][] modifiableProfit;
        private final double[][] marginalProfit;
        private final long dateCreated;
        private int maxIndex;

        public int getBuildingOrdinal(int ordinal) {
            return buildings[ordinal];
        }

        public CachedCity(JavaCity city, Continent continent, boolean selfSufficient, Predicate<Project> hasProject, int numCities, double grossModifier, double rads, Double infraLow) {
            this.hasProject = hasProject;
            this.selfSufficient = selfSufficient;
            this.ageDays = city.getAgeDays();
            this.dateCreated = city.getCreated();
            this.rads = rads;
            this.continent = continent;
            this.buildingInfra = city.getInfra();
            this.land = city.getLand();
            this.buildings = city.getBuildings();
            this.maxSlots = (int) (buildingInfra / 50);
            int basePollution = PW.City.getNukePollution(city.getNuke_turn());
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
            this.baseCommerce = (byte) (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE) ? 2 : 0);

            this.basePopulation = this.infraLow * 100;
            this.ageBonus = (1 + Math.log(Math.max(1, city.getAgeDays())) * 0.0666666666666666666666666666666);
            this.baseDisease = ((0.01 * MathMan.sqr((this.infraLow * 100) / (city.getLand() + 0.001)) - 25) * 0.01d) + (this.infraLow * 0.001);
            this.newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

            this.crimeCache = new double[150];
            this.commerceIncome = new double[150];
            for (int i = 0; i <= 149; i++) {
                int commerce = Math.min(maxCommerce, i);
                crimeCache[i] = (MathMan.sqr(103 - commerce) + (this.infraLow * 100))*(0.000009d);
                commerceIncome[i] = Math.max(0, (((commerce * 0.02) * 0.725) + 0.725) * newPlayerBonus) * grossModifier;
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
                    baseProfitConverted += building.profitConverted(continent, rads, hasProject, this.land, num);
                    building.profit(continent, rads, -1L, hasProject, city, this.baseProfit, 12, num);
                }
            }
            this.numBuildings = (char) numBuildings;
            this.basePollution = (char) basePollution;
            this.food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;
            this.baseProfit[ResourceType.FOOD.ordinal()] -= food;
            baseProfitConverted -= ResourceType.convertedTotalNegative(ResourceType.FOOD, food);
            this.baseProfitConverted = baseProfitConverted;

            ADD_BUILDING = new Consumer[modIe - modIs];
            REMOVE_BUILDING = new Consumer[modIe - modIs];
            this.modifiableProfit = new double[modIe - modIs][];
            this.marginalProfit = new double[modIe - modIs][];

            for (int i = modIs, j = 0; i < modIe; i++, j++) {
                int finalJ = j;
                Building building = Buildings.get(i);
                int addCommerce = building.getCommerce();
                int addPollution = building.getPollution(hasProject);
                int cap = building.getCap(hasProject);
                if (cap <= 0 || cap > 200) {
                    throw new IllegalArgumentException("Building " + building.name() + " has no cap");
                }
                double[] profitConverted = new double[cap];
                for (int k = 0; k < cap; k++) {
                    profitConverted[k] = building.profitConverted(continent, rads, hasProject, this.land, k + 1);
                }
                double[] marginalProfitJ = new double[cap];
                double lastMarginal = 0;
                for (int k = 0; k < cap; k++) {
                    double profit = profitConverted[k];
                    marginalProfitJ[k] = profit - lastMarginal;
                    lastMarginal = profit;
                }

                modifiableProfit[j] = profitConverted;
                marginalProfit[j] = marginalProfitJ;

                if (addCommerce != 0) {
                    if (addPollution != 0) {
                        ADD_BUILDING[j] = (n) -> {
                            byte amt = n.modifiable[finalJ];
                            n.baseRevenue += marginalProfitJ[amt];

                            n.commerce += addCommerce;
                            n.pollution += addPollution;
                            n.numBuildings++;
                            n.modifiable[finalJ]++;
                            n.revenueFull = Double.MIN_VALUE;
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            byte amt = n.modifiable[finalJ];
                            n.baseRevenue -= marginalProfitJ[amt - 1];

                            n.commerce -= addCommerce;
                            n.pollution -= addPollution;
                            n.numBuildings--;
                            n.modifiable[finalJ]--;
                            n.revenueFull = Double.MIN_VALUE;
                        };
                    } else {
                        ADD_BUILDING[j] = (n) -> {
                            byte amt = n.modifiable[finalJ];
                            n.baseRevenue += marginalProfitJ[amt];

                            n.commerce += addCommerce;
                            n.numBuildings++;
                            n.modifiable[finalJ]++;
                            n.revenueFull = Double.MIN_VALUE;
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            byte amt = n.modifiable[finalJ];
                            n.baseRevenue -= marginalProfitJ[amt - 1];

                            n.commerce -= addCommerce;
                            n.numBuildings--;
                            n.modifiable[finalJ]--;
                            n.revenueFull = Double.MIN_VALUE;
                        };
                    }
                } else if (addPollution != 0) {
                    ADD_BUILDING[j] = (n) -> {
                        byte amt = n.modifiable[finalJ];
                        n.baseRevenue += marginalProfitJ[amt];

                        n.pollution += addPollution;
                        n.numBuildings++;
                        n.modifiable[finalJ]++;
                        n.revenueFull = Double.MIN_VALUE;
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        byte amt = n.modifiable[finalJ];
                        n.baseRevenue -= marginalProfitJ[amt - 1];

                        n.pollution -= addPollution;
                        n.numBuildings--;
                        n.modifiable[finalJ]--;
                        n.revenueFull = Double.MIN_VALUE;
                    };
                } else {
                    ADD_BUILDING[j] = (n) -> {
                        byte amt = n.modifiable[finalJ];
                        n.baseRevenue += marginalProfitJ[amt];

                        n.numBuildings++;
                        n.modifiable[finalJ]++;
                        n.revenueFull = Double.MIN_VALUE;
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        byte amt = n.modifiable[finalJ];
                        n.baseRevenue -= marginalProfitJ[amt - 1];

                        n.numBuildings--;
                        n.modifiable[finalJ]--;
                        n.revenueFull = Double.MIN_VALUE;
                    };
                }
            }
        }

        public CityNode create() {
            CityNode node = new CityNode(this, new byte[LENGTH], numBuildings, baseCommerce, basePollution, 0d, 0);
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

        public double getLand() {
            return land;
        }
    }

    public CityNode(CachedCity origin, byte[] modifiable, char numBuildings, byte commerce, char pollution, double baseRevenue, int index) {
        this.cached = origin;
        this.modifiable = modifiable;
        this.numBuildings = numBuildings;
        this.commerce = commerce;
        this.pollution = pollution;
        this.baseRevenue = baseRevenue;
        this.index = index;
    }

    public CityNode clone() {
        return new CityNode(cached, modifiable.clone(), numBuildings, commerce, pollution, baseRevenue, index);
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

    public final double[] getProfit(double[] profitBuffer) {
        System.arraycopy(cached.baseProfit, 0, profitBuffer, 0, profitBuffer.length);
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

    public final double getRevenueConverted() {
        if (revenueFull != Double.MIN_VALUE) {
            return revenueFull;
        }
        CachedCity c        = cached;
        byte[] mods         = modifiable;
        double[][] mProfit  = c.modifiableProfit;
        int pop             = calcPopulation();
        double rev          = c.baseProfitConverted + c.commerceIncome[commerce] * pop;
//        for (int i = 0, len = mods.length; i < len; i++) {
//            int amt = mods[i];
//            if (amt > 0) {
//                rev += mProfit[i][amt - 1];
//            }
//        }
        rev += baseRevenue;
        return revenueFull = rev;
    }

    @Override
    public int calcPopulation(Predicate<Project> hasProject) {
        return calcPopulation();
    }

    public final int calcPopulation() {
        double disease = calcDisease();
        double crime = calcCrime();
        return calcPopulation(disease, crime);
    }

    public final int calcPopulation(double disease, double crime) {
        double diseaseDeaths = ((disease * 0.01) * cached.basePopulation);
        double crimeDeaths = Math.max((crime * 0.1) * cached.basePopulation - 25, 0);
        return (int) Math.max(10, ((cached.basePopulation - diseaseDeaths - crimeDeaths) * cached.ageBonus));
    }

    @Override
    public double calcDisease(Predicate<Project> hasProject) {
        return calcDisease();
    }

    public final double calcDisease() {
        int hospitals        = modifiable[HOSPITAL_MOD_INDEX] & 0xFF;
        double pollutionMod  = pollution * 0.05;
        double base          = cached.baseDisease + pollutionMod;

        if (hospitals > 0) {
            base -= hospitals * cached.hospitalPct;
        }
        return Math.max(0, base);
    }

    @Override
    public double calcCrime(Predicate<Project> hasProject) {
        return calcCrime();
    }

    public double calcCrime() {
        int police           = modifiable[POLICE_MOD_INDEX] & 0xFF;
        double crimeVal      = cached.crimeCache[commerce];

        if (police > 0) {
            crimeVal -= police * cached.policePct;
            if (crimeVal < 0) crimeVal = 0;
        }
        return crimeVal;
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
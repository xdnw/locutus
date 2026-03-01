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
    private static final int POPULATION_DIRTY_SENTINEL = Integer.MIN_VALUE;

    private long packedBuildings;
    private double revenueFull = Double.NaN;
    private double diseaseValue = Double.NaN;
    private double crimeValue = Double.NaN;
    private int populationValue = POPULATION_DIRTY_SENTINEL;
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
            getNumBuildings[i] = (n, c) -> n.getPackedMutable(finalI - modIs);
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
        private final double[][] marginalProfit;
        private final int[] shift;
        private final long[] mask;
        private final int[] cap;
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

            this.shift = new int[LENGTH];
            this.mask = new long[LENGTH];
            this.cap = new int[LENGTH];

            int bitOffset = 0;
            for (int j = 0; j < LENGTH; j++) {
                Building building = Buildings.get(modIs + j);
                int capJ = building.getCap(hasProject);
                if (capJ <= 0 || capJ > 200) {
                    throw new IllegalArgumentException("Building " + building.name() + " has no cap");
                }
                int bits = bitsForCount(capJ);
                if (bitOffset + bits > 63) {
                    throw new IllegalStateException("Packed city mutable building layout exceeds 63 bits");
                }
                shift[j] = bitOffset;
                cap[j] = capJ;
                long localMask = bits == 63 ? -1L : ((1L << bits) - 1L);
                mask[j] = (localMask << bitOffset);
                bitOffset += bits;
            }

            ADD_BUILDING = new Consumer[modIe - modIs];
            REMOVE_BUILDING = new Consumer[modIe - modIs];
            this.marginalProfit = new double[modIe - modIs][];

            for (int i = modIs, j = 0; i < modIe; i++, j++) {
                int finalJ = j;
                Building building = Buildings.get(i);
                int addCommerce = building.getCommerce();
                int addPollution = building.getPollution(hasProject);
                int cap = this.cap[j];
                double[] marginalProfitJ = new double[cap];
                double lastMarginal = 0;
                for (int k = 0; k < cap; k++) {
                    double profit = building.profitConverted(continent, rads, hasProject, this.land, k + 1);
                    marginalProfitJ[k] = profit - lastMarginal;
                    lastMarginal = profit;
                }

                marginalProfit[j] = marginalProfitJ;

                if (addCommerce != 0) {
                    if (addPollution != 0) {
                        ADD_BUILDING[j] = (n) -> {
                            int amt = n.getPackedMutable(finalJ);
                            n.baseRevenue += marginalProfitJ[amt];

                            n.commerce += addCommerce;
                            n.pollution += addPollution;
                            n.numBuildings++;
                            n.setPackedMutable(finalJ, amt + 1);
                            n.invalidateDerivedCache();
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            int amt = n.getPackedMutable(finalJ);
                            n.baseRevenue -= marginalProfitJ[amt - 1];

                            n.commerce -= addCommerce;
                            n.pollution -= addPollution;
                            n.numBuildings--;
                            n.setPackedMutable(finalJ, amt - 1);
                            n.invalidateDerivedCache();
                        };
                    } else {
                        ADD_BUILDING[j] = (n) -> {
                            int amt = n.getPackedMutable(finalJ);
                            n.baseRevenue += marginalProfitJ[amt];

                            n.commerce += addCommerce;
                            n.numBuildings++;
                            n.setPackedMutable(finalJ, amt + 1);
                            n.invalidateDerivedCache();
                        };
                        REMOVE_BUILDING[j] = (n) -> {
                            int amt = n.getPackedMutable(finalJ);
                            n.baseRevenue -= marginalProfitJ[amt - 1];

                            n.commerce -= addCommerce;
                            n.numBuildings--;
                            n.setPackedMutable(finalJ, amt - 1);
                            n.invalidateDerivedCache();
                        };
                    }
                } else if (addPollution != 0) {
                    ADD_BUILDING[j] = (n) -> {
                        int amt = n.getPackedMutable(finalJ);
                        n.baseRevenue += marginalProfitJ[amt];

                        n.pollution += addPollution;
                        n.numBuildings++;
                        n.setPackedMutable(finalJ, amt + 1);
                        n.invalidateDerivedCache();
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        int amt = n.getPackedMutable(finalJ);
                        n.baseRevenue -= marginalProfitJ[amt - 1];

                        n.pollution -= addPollution;
                        n.numBuildings--;
                        n.setPackedMutable(finalJ, amt - 1);
                        n.invalidateDerivedCache();
                    };
                } else {
                    ADD_BUILDING[j] = (n) -> {
                        int amt = n.getPackedMutable(finalJ);
                        n.baseRevenue += marginalProfitJ[amt];

                        n.numBuildings++;
                        n.setPackedMutable(finalJ, amt + 1);
                        n.invalidateDerivedCache();
                    };
                    REMOVE_BUILDING[j] = (n) -> {
                        int amt = n.getPackedMutable(finalJ);
                        n.baseRevenue -= marginalProfitJ[amt - 1];

                        n.numBuildings--;
                        n.setPackedMutable(finalJ, amt - 1);
                        n.invalidateDerivedCache();
                    };
                }
            }
        }

        private static int bitsForCount(int cap) {
            int states = cap + 1;
            return Math.max(1, 32 - Integer.numberOfLeadingZeros(states - 1));
        }

        public CityNode create() {
            CityNode node = new CityNode(this, 0L, numBuildings, baseCommerce, basePollution, 0d, 0);
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

        public int getMaxSlots() {
            return maxSlots;
        }

        public double getOptimisticPopulationUpperBound() {
            return basePopulation * ageBonus;
        }

        public double getCommerceIncomeAt(int commerce) {
            int clamped = Math.max(0, Math.min(commerceIncome.length - 1, commerce));
            return commerceIncome[clamped];
        }

        public double getMarginalProfit(Building building, int currentAmt) {
            int ordinal = building.ordinal();
            if (ordinal < modIs || ordinal >= modIe) {
                return 0;
            }
            double[] marginal = marginalProfit[ordinal - modIs];
            if (currentAmt < 0 || currentAmt >= marginal.length) {
                return 0;
            }
            return marginal[currentAmt];
        }
    }

    public CityNode(CachedCity origin, byte[] modifiable, char numBuildings, byte commerce, char pollution, double baseRevenue, int index) {
        this(origin, 0L, numBuildings, commerce, pollution, baseRevenue, index);
        for (int i = 0; i < Math.min(modifiable.length, LENGTH); i++) {
            int amount = modifiable[i] & 0xFF;
            setPackedMutable(i, amount);
        }
        invalidateDerivedCache();
    }

    public CityNode(CachedCity origin, long packedBuildings, char numBuildings, byte commerce, char pollution, double baseRevenue, int index) {
        this.cached = origin;
        this.packedBuildings = packedBuildings;
        this.numBuildings = numBuildings;
        this.commerce = commerce;
        this.pollution = pollution;
        this.baseRevenue = baseRevenue;
        this.index = index;
    }

    public CityNode clone() {
        CityNode copy = new CityNode(cached, packedBuildings, numBuildings, commerce, pollution, baseRevenue, index);
        copy.revenueFull = revenueFull;
        copy.diseaseValue = diseaseValue;
        copy.crimeValue = crimeValue;
        copy.populationValue = populationValue;
        return copy;
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

    private int getPackedMutable(int modIndex) {
        int shift = cached.shift[modIndex];
        long mask = cached.mask[modIndex];
        return (int) ((packedBuildings & mask) >>> shift);
    }

    private void setPackedMutable(int modIndex, int value) {
        int shift = cached.shift[modIndex];
        long mask = cached.mask[modIndex];
        int clamped = Math.max(0, Math.min(value, cached.cap[modIndex]));
        long encoded = ((long) clamped << shift) & mask;
        packedBuildings = (packedBuildings & ~mask) | encoded;
    }

    private void invalidateDerivedCache() {
        revenueFull = Double.NaN;
        diseaseValue = Double.NaN;
        crimeValue = Double.NaN;
        populationValue = POPULATION_DIRTY_SENTINEL;
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
        double revenue = cached.baseProfitConverted + cached.commerceIncome[commerce & 0xFF] * population;
        profitBuffer[0] += revenue;

        for (int i = 0; i < LENGTH; i++) {
            int amt = getPackedMutable(i);
            if (amt == 0) continue;
            Building building = Buildings.get(i + modIs);
            building.profit(cached.continent, cached.rads, -1L, cached.hasProject, this, profitBuffer, 12, amt);
        }
        return profitBuffer;
    }

    public final double getRevenueConverted() {
        if (!Double.isNaN(revenueFull)) {
            return revenueFull;
        }
        CachedCity c        = cached;
        int pop             = calcPopulation();
        double rev          = c.baseProfitConverted + c.commerceIncome[commerce & 0xFF] * pop;
        rev += baseRevenue;
        revenueFull = rev;
        return rev;
    }

    @Override
    public int calcPopulation(Predicate<Project> hasProject) {
        return calcPopulation();
    }

    public final int calcPopulation() {
        if (populationValue != POPULATION_DIRTY_SENTINEL) {
            return populationValue;
        }
        double disease = calcDisease();
        double crime = calcCrime();
        populationValue = calcPopulation(disease, crime);
        return populationValue;
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
        if (!Double.isNaN(diseaseValue)) {
            return diseaseValue;
        }
        int hospitals        = getPackedMutable(HOSPITAL_MOD_INDEX);
        double pollutionMod  = pollution * 0.05;
        double base          = cached.baseDisease + pollutionMod;

        if (hospitals > 0) {
            base -= hospitals * cached.hospitalPct;
        }
        diseaseValue = Math.max(0, base);
        return diseaseValue;
    }

    @Override
    public double calcCrime(Predicate<Project> hasProject) {
        return calcCrime();
    }

    public double calcCrime() {
        if (!Double.isNaN(crimeValue)) {
            return crimeValue;
        }
        int police           = getPackedMutable(POLICE_MOD_INDEX);
        double crimeVal      = cached.crimeCache[commerce & 0xFF];

        if (police > 0) {
            crimeVal -= police * cached.policePct;
            if (crimeVal < 0) crimeVal = 0;
        }
        crimeValue = crimeVal;
        return crimeValue;
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

    public double optimisticRevenueUpperBound(Building[] orderedBuildings, int startIndex, double[] topGainScratch) {
        double scoreNow = getRevenueConverted();
        int freeSlots = getFreeSlots();
        if (freeSlots <= 0) {
            return scoreNow;
        }

        int scratchLen = Math.min(freeSlots, topGainScratch.length);
        if (scratchLen <= 0) {
            return scoreNow;
        }

        int heapSize = 0;

        int commerceGainPotential = 0;
        for (int i = Math.max(0, startIndex); i < orderedBuildings.length; i++) {
            Building building = orderedBuildings[i];
            int currentAmt = getBuilding(building);
            int cap = building.cap(cached.hasProject());
            int room = cap - currentAmt;
            if (room <= 0) {
                continue;
            }
            int addCommerce = building.getCommerce();
            if (addCommerce > 0) {
                commerceGainPotential += room * addCommerce;
            }

            for (int level = currentAmt; level < cap; level++) {
                double marginal = cached.getMarginalProfit(building, level);
                if (marginal <= 0) {
                    continue;
                }

                if (heapSize < scratchLen) {
                    topGainScratch[heapSize] = marginal;
                    siftUpMin(topGainScratch, heapSize);
                    heapSize++;
                } else if (marginal > topGainScratch[0]) {
                    topGainScratch[0] = marginal;
                    siftDownMin(topGainScratch, heapSize, 0);
                }
            }
        }

        double optimisticExtraProfit = 0d;
        for (int i = 0; i < heapSize; i++) {
            optimisticExtraProfit += topGainScratch[i];
        }

        int optimisticCommerce = Math.min(cached.getMaxCommerce(), getCommerce() + commerceGainPotential);
        double optimisticPop = cached.getOptimisticPopulationUpperBound();
        double optimisticCommerceIncome = cached.getCommerceIncomeAt(optimisticCommerce) * optimisticPop;
        double currentCommerceIncome = cached.getCommerceIncomeAt(getCommerce()) * calcPopulation();
        double optimisticCommerceDelta = Math.max(0d, optimisticCommerceIncome - currentCommerceIncome);

        return scoreNow + optimisticExtraProfit + optimisticCommerceDelta;
    }

    private static void siftUpMin(double[] heap, int index) {
        double value = heap[index];
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (heap[parent] <= value) {
                break;
            }
            heap[index] = heap[parent];
            index = parent;
        }
        heap[index] = value;
    }

    private static void siftDownMin(double[] heap, int size, int index) {
        double value = heap[index];
        int half = size >>> 1;
        while (index < half) {
            int left = (index << 1) + 1;
            int right = left + 1;
            int minChild = left;
            if (right < size && heap[right] < heap[left]) {
                minChild = right;
            }
            if (heap[minChild] >= value) {
                break;
            }
            heap[index] = heap[minChild];
            index = minChild;
        }
        heap[index] = value;
    }
}
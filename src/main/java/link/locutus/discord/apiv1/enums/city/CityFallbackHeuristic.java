package link.locutus.discord.apiv1.enums.city;


import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.db.entities.city.SimpleNationCity;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

final class CityFallbackHeuristic {
    private static final ForkJoinPool SHARED_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    private static final int BEAM_WIDTH = 8;
    private static final int PARALLEL_THRESHOLD = 64;
    private static final boolean EAGER_INCUMBENT_PUBLISH = true;
    private static final double PRE_SCORE_PRUNE_SLACK = 0d;

    private static final int HOSPITAL_ORDINAL = Buildings.HOSPITAL.ordinal();
    private static final int POLICE_ORDINAL = Buildings.POLICE_STATION.ordinal();

    private static final Building[] CIVILIAN_BUILDINGS = Arrays.stream(Buildings.values())
            .filter(f -> f.getType() != BuildingType.MILITARY && f.getType() != BuildingType.POWER)
            .toArray(Building[]::new);
    private static final int[] CIVILIAN_ORDINALS = Arrays.stream(CIVILIAN_BUILDINGS).mapToInt(Building::ordinal).toArray();

    private static final int NUM_CIVILIAN_BUILDINGS = CIVILIAN_BUILDINGS.length;

    // --- instance fields: fixed for the entire search ---
    private final Continent continent;
    private final Predicate<Project> hasProject;
    private final double targetInfra;
    private final double targetLand;
    private final int targetCivilianSlots;
    private final int[] targetMilitarySignature;
    private final int[] targetPowerSignature;
    private final ToDoubleFunction<INationCity> valueFunction;
    private final Predicate<INationCity> goal;
    private final BiConsumer<DBCity, double[]> getRevenue;
    private final ToDoubleFunction<DBCity> convertedFunc;
    private final DBCity origin;
    private final ICity targetSignature;

    // precomputed: which civilian buildings are legal on this continent and their caps
    private final double diseaseDeathFactor;
    private final double crimeDeathFactor;
    private final double incomeFactorA;
    private final double incomeFactorB;
    private final int[] civilianCaps;
    private final int[] civilianOrdinals;
    private final int[] ordinalToCivilianIndex;
    private final int[] civilianShift;
    private final int[] civilianBits;
    private final long[] civilianMask;
    private final long[] civilianStep;
    private final int[] civilianCommerce;
    private final int[] civilianPollution;
    private final int[] fixedBuildingCounts;
    private final int fixedBuildingTotal;
    private final double fixedRequiredInfra;
    private final double originRequiredInfra;
    private final double targetPoweredInfra;
    private final boolean hasSufficientPower;
    private final int maxFeasibleCivilianCount;
    private final int targetPoweredInfraInt;
    private final int maxCommerce;
    private final double hospitalPct;
    private final double policePct;
    private final boolean hasSpecializedPoliceTraining;
    private final double diseaseBase;
    private final double basePopulation;
    private final double ageBonus;
    private final double newPlayerBonus;
    private final int[] commerceContributionByOrdinal;
    private final int[] pollutionContributionByOrdinal;
    private final double[] flatCivilianProfitLookup;
    private final int[] profitLookupOffset;
    private final double[] crimeByCommerce;
    private final int fixedCommerce;
    private final int fixedPollution;
    private final double fixedRevenueConverted;
    private final double foodConverted;
    private final double incomeFactor;
    private final long originCreated;
    private final long evalDate;
    private final int numCities;
    private final double rads;
    private final double grossModifier;
    private final boolean debugCounters;
    private final int[] packedCivilianIndices;   // buildings actually encoded in packed state
    private final int[] addableCivilianIndices;  // buildings with cap > 0

    private volatile double bestValueSoFar = Double.NEGATIVE_INFINITY;

    private CityFallbackHeuristic(Continent continent,
                                  Predicate<Project> hasProject,
                                  double targetInfra,
                                  double targetLand,
                                  int targetCivilianSlots,
                                  int[] targetMilitarySignature,
                                  int[] targetPowerSignature,
                                  ToDoubleFunction<INationCity> valueFunction,
                                  Predicate<INationCity> goal,
                                  BiConsumer<DBCity, double[]> getRevenue,
                                  ToDoubleFunction<DBCity> convertedFunc,
                                  DBCity origin,
                                  ICity targetSignature,
                                  int numCities,
                                  double rads,
                                  double grossModifier,
                                  long evalDate) {
        this.continent = continent;
        this.hasProject = hasProject;
        this.targetInfra = targetInfra;
        this.targetLand = targetLand;
        this.targetCivilianSlots = targetCivilianSlots;
        this.targetMilitarySignature = targetMilitarySignature;
        this.targetPowerSignature = targetPowerSignature;
        this.valueFunction = valueFunction;
        this.goal = goal;
        this.getRevenue = getRevenue;
        this.convertedFunc = convertedFunc;
        this.origin = origin;
        this.targetSignature = targetSignature;
        this.numCities = numCities;
        this.rads = rads;
        this.grossModifier = grossModifier;
        this.evalDate = evalDate;
        this.debugCounters = Boolean.getBoolean("locutus.cityFallback.debugCounters");

        this.civilianCaps = new int[NUM_CIVILIAN_BUILDINGS];
        this.civilianOrdinals = new int[NUM_CIVILIAN_BUILDINGS];
        this.ordinalToCivilianIndex = new int[PW.City.Building.SIZE];
        this.civilianShift = new int[NUM_CIVILIAN_BUILDINGS];
        this.civilianBits = new int[NUM_CIVILIAN_BUILDINGS];
        this.civilianMask = new long[NUM_CIVILIAN_BUILDINGS];
        this.civilianStep = new long[NUM_CIVILIAN_BUILDINGS];
        this.civilianCommerce = new int[NUM_CIVILIAN_BUILDINGS];
        this.civilianPollution = new int[NUM_CIVILIAN_BUILDINGS];
        Arrays.fill(this.ordinalToCivilianIndex, -1);

        int bitOffset = 0;
        for (int i = 0; i < NUM_CIVILIAN_BUILDINGS; i++) {
            Building b = CIVILIAN_BUILDINGS[i];
            civilianCaps[i] = b.canBuild(continent) ? b.getCap(hasProject) : -1;
            civilianOrdinals[i] = b.ordinal();
            ordinalToCivilianIndex[b.ordinal()] = i;

            civilianCommerce[i] = b.getCommerce();
            civilianPollution[i] = b.pollution(hasProject);

            int cap = civilianCaps[i];
            int bits = cap >= 0 ? bitsForCount(cap) : 0;
            civilianBits[i] = bits;
            civilianShift[i] = bitOffset;
            if (bits == 0) {
                civilianMask[i] = 0L;
                civilianStep[i] = 0L;
                continue;
            }
            long localMask = bits == 63 ? -1L : ((1L << bits) - 1L);
            civilianMask[i] = localMask << bitOffset;
            civilianStep[i] = 1L << bitOffset; // Enables branchless O(1) addition step
            bitOffset += bits;
        }

        this.originCreated = origin.getCreated();
        this.fixedBuildingCounts = new int[PW.City.Building.SIZE];
        fixedBuildingCounts[Buildings.BARRACKS.ordinal()] = targetMilitarySignature[0];
        fixedBuildingCounts[Buildings.FACTORY.ordinal()] = targetMilitarySignature[1];
        fixedBuildingCounts[Buildings.HANGAR.ordinal()] = targetMilitarySignature[2];
        fixedBuildingCounts[Buildings.DRYDOCK.ordinal()] = targetMilitarySignature[3];
        fixedBuildingCounts[Buildings.COAL_POWER.ordinal()] = targetPowerSignature[0];
        fixedBuildingCounts[Buildings.OIL_POWER.ordinal()] = targetPowerSignature[1];
        fixedBuildingCounts[Buildings.NUCLEAR_POWER.ordinal()] = targetPowerSignature[2];
        fixedBuildingCounts[Buildings.WIND_POWER.ordinal()] = targetPowerSignature[3];

        this.fixedBuildingTotal = Arrays.stream(targetMilitarySignature).sum() + Arrays.stream(targetPowerSignature).sum();
        this.fixedRequiredInfra = (Arrays.stream(targetMilitarySignature).sum() + Arrays.stream(targetPowerSignature).sum()) * 50d;
        this.originRequiredInfra = origin.getRequiredInfra();
        this.targetPoweredInfra = targetSignature.getPoweredInfra();
        this.hasSufficientPower = targetPoweredInfra >= targetInfra;
        this.maxFeasibleCivilianCount = Math.max(0, (int) Math.floor((targetInfra - fixedRequiredInfra) / 50d));
        this.targetPoweredInfraInt = (int) Math.ceil(targetPoweredInfra);

        this.maxCommerce = getMaxCommerce(hasProject);
        this.hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5d : 2.5d;
        this.policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5d : 2.5d;
        this.hasSpecializedPoliceTraining = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM);
        this.commerceContributionByOrdinal = new int[PW.City.Building.SIZE];
        this.pollutionContributionByOrdinal = new int[PW.City.Building.SIZE];

        for (Building building : Buildings.values()) {
            commerceContributionByOrdinal[building.ordinal()] = building.getCommerce();
            pollutionContributionByOrdinal[building.ordinal()] = building.pollution(hasProject);
        }

        this.profitLookupOffset = new int[NUM_CIVILIAN_BUILDINGS];
        int totalCaps = 0;
        for (int i = 0; i < NUM_CIVILIAN_BUILDINGS; i++) {
            profitLookupOffset[i] = totalCaps;
            int cap = Math.max(0, civilianCaps[i]);
            totalCaps += (cap + 1);
        }
        this.flatCivilianProfitLookup = new double[totalCaps];

        for (int i = 0; i < NUM_CIVILIAN_BUILDINGS; i++) {
            int cap = Math.max(0, civilianCaps[i]);
            Building building = CIVILIAN_BUILDINGS[i];
            for (int amt = 1; amt <= cap; amt++) {
                flatCivilianProfitLookup[profitLookupOffset[i] + amt] = building.profitConverted(continent, rads, hasProject, targetLand, amt);
            }
        }

        int fixedCommerceAcc = hasSpecializedPoliceTraining ? 4 : 0;
        int fixedPollutionAcc = 0;
        double fixedRevenueAcc = 0d;
        int unpoweredInfra = (int) Math.ceil(targetInfra);
        for (int ordinal = 0; ordinal < PW.City.Building.SIZE; ordinal++) {
            if (ordinalToCivilianIndex[ordinal] >= 0) continue;

            int amt = fixedBuildingCounts[ordinal];
            if (amt == 0) continue;

            Building building = Buildings.get(ordinal);
            fixedCommerceAcc += amt * commerceContributionByOrdinal[ordinal];
            fixedPollutionAcc += amt * pollutionContributionByOrdinal[ordinal];

            if (ordinal < 4) {
                APowerBuilding powerBuilding = (APowerBuilding) building;
                for (int i = 0; i < amt; i++) {
                    if (unpoweredInfra > 0) {
                        fixedRevenueAcc += powerBuilding.consumptionConverted(unpoweredInfra);
                        unpoweredInfra -= powerBuilding.getInfraMax();
                    }
                }
            }
            fixedRevenueAcc += building.profitConverted(continent, rads, hasProject, targetLand, amt);
        }
        this.fixedCommerce = fixedCommerceAcc;
        this.fixedPollution = fixedPollutionAcc;
        this.fixedRevenueConverted = fixedRevenueAcc;

        this.basePopulation = targetInfra * 100d;
        this.diseaseBase = ((0.01 * MathMan.sqr((targetInfra * 100d) / (targetLand + 0.001d)) - 25d) * 0.01d) + (targetInfra * 0.001d);
        this.ageBonus = originCreated <= 0 || originCreated == Long.MAX_VALUE
                ? 1d
                : (1d + Math.log(Math.max(1d, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - originCreated))) * 0.06666666666666667d);
        this.newPlayerBonus = 1d + Math.max(1d - (numCities - 1) * 0.05d, 0d);
        this.incomeFactor = newPlayerBonus * grossModifier;

        this.diseaseDeathFactor = 0.01d * this.basePopulation;
        this.crimeDeathFactor = 0.1d * this.basePopulation;
        this.incomeFactorA = 0.0145d * this.incomeFactor;
        this.incomeFactorB = 0.725d * this.incomeFactor;

        double food = (basePopulation * basePopulation) / 125_000_000d + (basePopulation * (ageBonus - 1d)) / 850d;
        this.foodConverted = ResourceType.convertedTotalNegative(ResourceType.FOOD, food);
        this.crimeByCommerce = new double[maxCommerce + 1];

        for (int i = 0; i <= maxCommerce; i++) {
            crimeByCommerce[i] = Math.max(0d, (MathMan.sqr(103 - i) + basePopulation) * 0.000009d);
        }

        int packedCivilianCount = 0;
        int addableCivilianCount = 0;
        for (int i = 0; i < NUM_CIVILIAN_BUILDINGS; i++) {
            if (civilianBits[i] != 0) packedCivilianCount++;
            if (civilianCaps[i] > 0) addableCivilianCount++;
        }

        this.packedCivilianIndices = new int[packedCivilianCount];
        this.addableCivilianIndices = new int[addableCivilianCount];

        for (int i = 0, p = 0, a = 0; i < NUM_CIVILIAN_BUILDINGS; i++) {
            if (civilianBits[i] != 0) packedCivilianIndices[p++] = i;
            if (civilianCaps[i] > 0) addableCivilianIndices[a++] = i;
        }
    }

    static INationCity findBest(ICity source, Continent continent, int numCities,
                                ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal,
                                Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow,
                                Collection<DBCity> donors) {
        CityFallbackHeuristic ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
        return ctx == null ? null : ctx.search(donors, true);
    }

    static INationCity findBestExactSlotOnly(ICity source, Continent continent, int numCities,
                                             ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal,
                                             Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow,
                                             Collection<DBCity> donors) {
        CityFallbackHeuristic ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
        return ctx == null ? null : ctx.search(donors, false);
    }

    private static CityFallbackHeuristic create(ICity source, Continent continent, int numCities,
                                                ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal,
                                                Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow) {
        DBCity origin = new SimpleDBCity(source);
        double targetInfra = Objects.requireNonNullElseGet(infraLow, origin::getInfra);

        SimpleDBCity sig = new SimpleDBCity(origin);
        sig.setInfra(targetInfra);
        sig.setOptimalPower(continent);

        int[] milSig = getMilitarySignature(origin);
        int[] powSig = getPowerSignature(sig);
        int totalSlots = (int) (ArrayUtil.toCents(targetInfra) / 50_00);
        int fixedSlots = Arrays.stream(milSig).sum() + Arrays.stream(powSig).sum();
        int civilianSlots = totalSlots - fixedSlots;
        if (civilianSlots <= 0) return null;

        long date = System.currentTimeMillis();
        BiConsumer<DBCity, double[]> getRevenue = (city, buffer) ->
                PW.City.profit(continent, rads, date, hasProject, buffer, numCities, grossModifier, false, 12, city);
        ToDoubleFunction<DBCity> convertedFunc = city ->
                PW.City.profitConverted(continent, rads, hasProject, numCities, grossModifier, city);

        return new CityFallbackHeuristic(continent, hasProject, targetInfra, origin.getLand(), civilianSlots, milSig, powSig,
                valueFunction, goal, getRevenue, convertedFunc, origin, sig, numCities, rads, grossModifier, date);
    }

    private INationCity search(Collection<DBCity> donors, boolean useBeamSearch) {
        record PackedDonor(DBCity donor, long packed) {}

        LongOpenHashSet seenDonorStates = new LongOpenHashSet(donors.size() * 2);
        ObjectArrayList<PackedDonor> donorList = new ObjectArrayList<>(donors.size());
        for (DBCity donor : donors) {
            long packed = packCivilianState(donor);
            if (seenDonorStates.add(packed)) {
                donorList.add(new PackedDonor(donor, packed));
            }
        }

        int parallelism = SHARED_POOL.getParallelism();
        EvaluatedCandidate best;

        if (parallelism <= 1 || donorList.size() < PARALLEL_THRESHOLD) {
            best = null;
            DonorWorker worker = newDonorWorker();
            for (PackedDonor donor : donorList) {
                EvaluatedCandidate candidate = evaluateDonor(donor.donor(), donor.packed(), useBeamSearch, worker);
                best = chooseBetter(best, candidate);
                if (candidate != null) {
                    publishBestMaybe(candidate.value());
                }
            }
        } else {
            try {
                best = SHARED_POOL.submit(() -> donorList.parallelStream()
                        .collect(
                                () -> new ParallelAccumulator(newDonorWorker()),
                                (acc, donor) -> {
                                    EvaluatedCandidate candidate =
                                            evaluateDonor(donor.donor(), donor.packed(), useBeamSearch, acc.worker);
                                    acc.best = chooseBetter(acc.best, candidate);
                                    if (EAGER_INCUMBENT_PUBLISH && candidate != null) {
                                        publishBestMaybe(candidate.value());
                                    }
                                },
                                (left, right) -> {
                                    left.best = chooseBetter(left.best, right.best);
                                    if (left.best != null) {
                                        publishBestMaybe(left.best.value());
                                    }
                                }
                        ).best).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed during fallback donor evaluation", e);
            }
        }

        if (best == null) return null;

        SimpleNationCity materialized = new SimpleNationCity(best.donor(), getRevenue, convertedFunc);
        normalizeAndApplyInto(materialized, best.donor(), best.state());

        return materialized;
    }

    private EvaluatedCandidate evaluateDonor(DBCity donor, long donorState, boolean useBeamSearch, DonorWorker worker) {
        worker.localScoreCache().clear();

        long candidateState;
        PackedCandidateView view = worker.view();

        if (useBeamSearch) {
            long repairedState = donorState;
            int civilianCount = 0;

            for (int idx = 0; idx < packedCivilianIndices.length; idx++) {
                int bi = packedCivilianIndices[idx];
                int current = (int) ((repairedState & civilianMask[bi]) >>> civilianShift[bi]);
                if (current <= 0) continue;

                int cap = civilianCaps[bi];
                int clamped = Math.min(current, cap);
                if (clamped != current) {
                    repairedState = (repairedState & ~civilianMask[bi]) | (((long) clamped << civilianShift[bi]) & civilianMask[bi]);
                }
                civilianCount += clamped;
            }

            if (!isFastStructurallyFeasible(Math.min(civilianCount, targetCivilianSlots))) return null;

            double preScore = scoreState(repairedState, view, worker);
            if (Double.isFinite(preScore) && preScore + PRE_SCORE_PRUNE_SLACK < bestValueSoFar) return null;

            while (civilianCount > targetCivilianSlots) {
                long updated = dropLeastHarmfulCivilian(repairedState, view, worker);
                if (updated == Long.MIN_VALUE) return null;
                repairedState = updated;
                civilianCount--;
            }

            candidateState = (civilianCount == targetCivilianSlots)
                    ? repairedState
                    : fitCivilianSlots(repairedState, civilianCount, worker, view);

            if (candidateState == Long.MIN_VALUE) return null;
        } else {
            int postClampCount = estimatePostClampCivilianCount(donor);
            if (postClampCount != targetCivilianSlots) return null;
            if (getCivilianCount(donor) != targetCivilianSlots) return null;
            candidateState = donorState;
        }

        if (goal != null) {
            view.setCivilianState(candidateState);
            if (!goal.test(view)) return null;
        }

        double value = scoreState(candidateState, view, worker);
        if (!Double.isFinite(value)) return null;

        return new EvaluatedCandidate(donor, candidateState, value, donor.getId());
    }

    private int estimatePostClampCivilianCount(DBCity donor) {
        byte[] buildings = donor.getBuildings3();
        int total = 0;

        if (buildings.length == PW.City.Building.SIZE) {
            for (int idx = 0; idx < addableCivilianIndices.length; idx++) {
                int bi = addableCivilianIndices[idx];
                int current = buildings[civilianOrdinals[bi]];
                if (current > 0) {
                    total += Math.min(current, civilianCaps[bi]);
                }
            }
        } else {
            for (int idx = 0; idx < addableCivilianIndices.length; idx++) {
                int bi = addableCivilianIndices[idx];
                int current = donor.getBuildingOrdinal(civilianOrdinals[bi]);
                if (current > 0) {
                    total += Math.min(current, civilianCaps[bi]);
                }
            }
        }

        return total;
    }

    private EvaluatedCandidate chooseBetter(EvaluatedCandidate left, EvaluatedCandidate right) {
        if (left == null) return right;
        if (right == null) return left;
        if (right.value() > left.value()) return right;
        if (right.value() < left.value()) return left;
        return right.donorId() < left.donorId() ? right : left;
    }

    private long dropLeastHarmfulCivilian(long state, PackedCandidateView view, DonorWorker worker) {
        view.setCivilianState(state);

        int bestIndex = -1;
        double bestValue = Double.NEGATIVE_INFINITY;
        PackedCandidateView childView = worker.childView();
        Long2DoubleOpenHashMap localScoreCache = worker.localScoreCache();

        for (int idx = 0; idx < addableCivilianIndices.length; idx++) {
            int bi = addableCivilianIndices[idx];
            int ordinal = civilianOrdinals[bi];
            int current = view.unpacked[ordinal];
            if (current <= 0) continue;

            long probeState = state - civilianStep[bi];

            double value = localScoreCache.get(probeState);
            if (Double.isNaN(value)) {
                view.unpacked[ordinal] = current - 1;

                childView.setCivilianStateIncremental(view, bi, current, current - 1);
                value = valueFunction.applyAsDouble(childView);
                localScoreCache.put(probeState, value);

                view.unpacked[ordinal] = current;
            }

            if (!Double.isFinite(value)) continue;
            if (value > bestValue) {
                bestValue = value;
                bestIndex = bi;
            }
        }

        if (bestIndex < 0) return Long.MIN_VALUE;
        return state - civilianStep[bestIndex];
    }

    private boolean isFastStructurallyFeasible(int civilianCount) {
        return civilianCount <= maxFeasibleCivilianCount;
    }

    private DonorWorker newDonorWorker() {
        Long2DoubleOpenHashMap localScoreCache = new Long2DoubleOpenHashMap(4096);
        localScoreCache.defaultReturnValue(Double.NaN);
        LongOpenHashSet beamSet = new LongOpenHashSet(BEAM_WIDTH * NUM_CIVILIAN_BUILDINGS * 2);
        return new DonorWorker(new PackedCandidateView(), new PackedCandidateView(),
                new long[BEAM_WIDTH], new long[BEAM_WIDTH], new double[BEAM_WIDTH],
                localScoreCache, beamSet);
    }

    private void normalizeAndApplyInto(SimpleNationCity target, DBCity donor, long state) {
        target.set(donor, true);
        target.setPowered(true);
        target.setNuke_turn(0);
        target.setLand(targetLand);
        target.setInfra(targetInfra);
        target.setDateCreated(origin.getCreated());
        target.setMilitaryBuildings(origin);
        target.setPowerBuildings(targetSignature);
        for (int bi = 0; bi < NUM_CIVILIAN_BUILDINGS; bi++) {
            target.setBuilding(CIVILIAN_BUILDINGS[bi], getPackedCount(state, bi));
        }
    }

    private long fitCivilianSlots(long startState, int startCivilianCount, DonorWorker worker, PackedCandidateView view) {
        if (!hasSufficientPower) return Long.MIN_VALUE;
        int steps = Math.abs(targetCivilianSlots - startCivilianCount);
        if (steps == 0) return startState;

        boolean addMode = targetCivilianSlots > startCivilianCount;
        long[] beamStates = worker.beamStates();
        long[] nextBeam = worker.nextBeam();
        double[] topScores = worker.topScores();
        int beamSize = 1;
        beamStates[0] = startState;

        PackedCandidateView childView = worker.childView();
        Long2DoubleOpenHashMap localScoreCache = worker.localScoreCache();
        LongOpenHashSet beamSet = worker.beamSet();

        for (int i = 0; i < steps; i++) {
            if (i > 0 && !addMode && topScores[0] + PRE_SCORE_PRUNE_SLACK < bestValueSoFar) return Long.MIN_VALUE;
            beamSet.clear();
            int stepCivilianCount = addMode ? (startCivilianCount + i + 1) : (startCivilianCount - i - 1);
            if (!isFastStructurallyFeasible(stepCivilianCount)) return Long.MIN_VALUE;

            for (int k = 0; k < BEAM_WIDTH; k++) topScores[k] = Double.NEGATIVE_INFINITY;
            int keep = 0;

            if (addMode) {
                for (int beamIndex = 0; beamIndex < beamSize; beamIndex++) {
                    long parentState = beamStates[beamIndex];
                    view.setCivilianState(parentState);

                    for (int idx = 0; idx < addableCivilianIndices.length; idx++) {
                        int bi = addableCivilianIndices[idx];
                        int ordinal = civilianOrdinals[bi];
                        int amt = view.unpacked[ordinal];
                        int cap = civilianCaps[bi];
                        if (amt >= cap) continue;

                        long childState = parentState + civilianStep[bi];
                        if (!beamSet.add(childState)) continue;

                        double value = localScoreCache.get(childState);

                        if (Double.isNaN(value)) {
                            view.unpacked[ordinal] = amt + 1;
                            childView.setCivilianStateIncremental(view, bi, amt, amt + 1);
                            value = valueFunction.applyAsDouble(childView);
                            localScoreCache.put(childState, value);
                            view.unpacked[ordinal] = amt;
                        }

                        if (!Double.isFinite(value)) continue;

                        if (keep < BEAM_WIDTH || value > topScores[BEAM_WIDTH - 1]) {
                            int pos = Math.min(keep, BEAM_WIDTH - 1);
                            while (pos > 0 && value > topScores[pos - 1]) pos--;
                            int shift = Math.min(keep, BEAM_WIDTH - 1);
                            for (int k = shift; k > pos; k--) {
                                topScores[k] = topScores[k - 1];
                                nextBeam[k] = nextBeam[k - 1];
                            }
                            topScores[pos] = value;
                            nextBeam[pos] = childState;
                            if (keep < BEAM_WIDTH) keep++;
                        }
                    }
                }
            } else {
                for (int beamIndex = 0; beamIndex < beamSize; beamIndex++) {
                    long parentState = beamStates[beamIndex];
                    view.setCivilianState(parentState);

                    for (int idx = 0; idx < addableCivilianIndices.length; idx++) {
                        int bi = addableCivilianIndices[idx];
                        int ordinal = civilianOrdinals[bi];
                        int amt = view.unpacked[ordinal];
                        if (amt <= 0) continue;

                        long childState = parentState - civilianStep[bi];
                        if (!beamSet.add(childState)) continue;

                        double value = localScoreCache.get(childState);

                        if (Double.isNaN(value)) {
                            view.unpacked[ordinal] = amt - 1;
                            childView.setCivilianStateIncremental(view, bi, amt, amt - 1);
                            value = valueFunction.applyAsDouble(childView);
                            localScoreCache.put(childState, value);
                            view.unpacked[ordinal] = amt;
                        }

                        if (!Double.isFinite(value)) continue;

                        if (keep < BEAM_WIDTH || value > topScores[BEAM_WIDTH - 1]) {
                            int pos = Math.min(keep, BEAM_WIDTH - 1);
                            while (pos > 0 && value > topScores[pos - 1]) pos--;
                            int shift = Math.min(keep, BEAM_WIDTH - 1);
                            for (int k = shift; k > pos; k--) {
                                topScores[k] = topScores[k - 1];
                                nextBeam[k] = nextBeam[k - 1];
                            }
                            topScores[pos] = value;
                            nextBeam[pos] = childState;
                            if (keep < BEAM_WIDTH) keep++;
                        }
                    }
                }
            }

            if (keep == 0) return Long.MIN_VALUE;

            beamSize = keep;

            long[] temp = beamStates;
            beamStates = nextBeam;
            nextBeam = temp;
        }

        return beamStates[0];
    }

    private static int bitsForCount(int cap) {
        if (cap <= 1) return 1;
        return 32 - Integer.numberOfLeadingZeros(cap);
    }

    private long packCivilianState(DBCity city) {
        byte[] buildings = city.getBuildings3();
        long state = 0L;
        if (buildings.length != PW.City.Building.SIZE) {
            for (int idx = 0; idx < packedCivilianIndices.length; idx++) {
                int bi = packedCivilianIndices[idx];
                long value = city.getBuildingOrdinal(civilianOrdinals[bi]);
                state |= (value << civilianShift[bi]) & civilianMask[bi];
            }
        } else {
            for (int idx = 0; idx < packedCivilianIndices.length; idx++) {
                int bi = packedCivilianIndices[idx];
                long value = buildings[civilianOrdinals[bi]];
                state |= (value << civilianShift[bi]) & civilianMask[bi];
            }
        }
        return state;
    }

    private int getPackedCount(long state, int bi) {
        return (int) ((state & civilianMask[bi]) >>> civilianShift[bi]);
    }

    private double scoreState(long state, PackedCandidateView view, DonorWorker worker) {
        Long2DoubleOpenHashMap local = worker.localScoreCache();
        double cached = local.get(state);
        if (!Double.isNaN(cached)) return cached;

        view.setCivilianState(state);
        double computed = valueFunction.applyAsDouble(view);
        local.put(state, computed);
        return computed;
    }

    private int getCivilianCount(DBCity city) {
        byte[] buildings = city.getBuildings3();
        int total = 0;
        if (buildings.length == PW.City.Building.SIZE) {
            for (int i = 0; i < CIVILIAN_ORDINALS.length; i++) {
                total += buildings[CIVILIAN_ORDINALS[i]];
            }
        } else {
            for (int i = 0; i < CIVILIAN_ORDINALS.length; i++) {
                total += city.getBuildingOrdinal(CIVILIAN_ORDINALS[i]);
            }
        }
        return total;
    }

    private int getPackedCivilianCount(long packed) {
        int total = 0;
        for (int idx = 0; idx < packedCivilianIndices.length; idx++) {
            int bi = packedCivilianIndices[idx];
            total += (int) ((packed & civilianMask[bi]) >>> civilianShift[bi]);
        }
        return total;
    }

    private static int[] getMilitarySignature(ICity city) {
        return new int[]{
                city.getBuilding(Buildings.BARRACKS), city.getBuilding(Buildings.FACTORY),
                city.getBuilding(Buildings.HANGAR), city.getBuilding(Buildings.DRYDOCK)
        };
    }

    private static int[] getPowerSignature(ICity city) {
        return new int[]{
                city.getBuilding(Buildings.COAL_POWER), city.getBuilding(Buildings.OIL_POWER),
                city.getBuilding(Buildings.NUCLEAR_POWER), city.getBuilding(Buildings.WIND_POWER)
        };
    }

    private static int getMaxCommerce(Predicate<Project> hasProject) {
        if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
            if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) return 125;
            return 115;
        }
        return 100;
    }

    private void publishBestMaybe(double value) {
        if (value > bestValueSoFar) {
            bestValueSoFar = value;
        }
    }

    private final class PackedCandidateView implements INationCity {
        private long lastSetState = Long.MIN_VALUE;
        private int[] unpacked;
        private int cachedCommerce;
        private int cachedPollution;
        private double cachedDisease;
        private double cachedCrime;
        private int cachedPopulation;
        private double cachedCivilianRevenue;
        private double cachedRevenueConverted;
        private int cachedNumBuildings;
        private int uncappedCommerce;

        public PackedCandidateView() {
            this.unpacked = Arrays.copyOf(fixedBuildingCounts, fixedBuildingCounts.length);
        }

        void setCivilianState(long state) {
            if (state == lastSetState) return;
            lastSetState = state;

            int comm = fixedCommerce;
            int pol = fixedPollution;
            double civilianRevenue = 0d;
            int civilians = 0;

            for (int idx = 0; idx < packedCivilianIndices.length; idx++) {
                int i = packedCivilianIndices[idx];
                int amt = (int) ((state & civilianMask[i]) >>> civilianShift[i]);
                unpacked[civilianOrdinals[i]] = amt;

                civilians += amt;
                comm += amt * civilianCommerce[i];
                pol += amt * civilianPollution[i];
                civilianRevenue += flatCivilianProfitLookup[profitLookupOffset[i] + amt];
            }

            this.cachedNumBuildings = fixedBuildingTotal + civilians;
            this.uncappedCommerce = comm;
            this.cachedCommerce = Math.min(comm, maxCommerce);
            this.cachedPollution = pol;

            int hospitals = unpacked[HOSPITAL_ORDINAL];
            double hospitalMod = hospitals > 0 ? hospitals * hospitalPct : 0d;
            double disease = diseaseBase - hospitalMod + pol * 0.05d;
            this.cachedDisease = disease > 0d ? disease : 0d;

            int police = unpacked[POLICE_ORDINAL];
            double policeMod = police > 0 ? police * policePct : 0d;
            double crime = crimeByCommerce[this.cachedCommerce] - policeMod;
            this.cachedCrime = crime > 0d ? crime : 0d;

            double diseaseDeaths = this.cachedDisease * diseaseDeathFactor;
            double crimeDeaths = this.cachedCrime * crimeDeathFactor - 25d;
            if (crimeDeaths < 0d) crimeDeaths = 0d;

            double pop = (basePopulation - diseaseDeaths - crimeDeaths) * ageBonus;
            this.cachedPopulation = (int) (pop > 10d ? pop + 0.5d : 10.5d);

            double income = (incomeFactorA * this.cachedCommerce + incomeFactorB) * this.cachedPopulation;
            this.cachedCivilianRevenue = civilianRevenue;
            this.cachedRevenueConverted = fixedRevenueConverted + civilianRevenue + income - foodConverted;
        }

        void setCivilianStateIncremental(PackedCandidateView parent, int changedBi, int oldAmt, int newAmt) {
            this.unpacked = parent.unpacked;

            this.uncappedCommerce = parent.uncappedCommerce + (newAmt - oldAmt) * civilianCommerce[changedBi];
            int newComm = Math.min(this.uncappedCommerce, maxCommerce);

            int newPol = parent.cachedPollution + (newAmt - oldAmt) * civilianPollution[changedBi];

            int offset = profitLookupOffset[changedBi];
            double newCivilianRevenue = parent.cachedCivilianRevenue
                    + flatCivilianProfitLookup[offset + newAmt]
                    - flatCivilianProfitLookup[offset + oldAmt];

// Safely reads already mutated shared array without any conditional checks
            int hospitals = this.unpacked[HOSPITAL_ORDINAL];
            int police    = this.unpacked[POLICE_ORDINAL];

            double disease = diseaseBase - (hospitals > 0 ? hospitals * hospitalPct : 0d) + newPol * 0.05d;
            double newDisease = disease > 0d ? disease : 0d;

            double crime = crimeByCommerce[newComm] - (police > 0 ? police * policePct : 0d);
            double newCrime = crime > 0d ? crime : 0d;

            double diseaseDeaths = newDisease * diseaseDeathFactor; // Uses precalculated factor logic consistency
            double crimeDeaths = newCrime * crimeDeathFactor - 25d;
            if (crimeDeaths < 0d) crimeDeaths = 0d;

            double pop = (basePopulation - diseaseDeaths - crimeDeaths) * ageBonus;
            int newPop = (int) (pop > 10d ? pop + 0.5d : 10.5d);

            double newIncome = (incomeFactorA * newComm + incomeFactorB) * newPop;

            this.cachedNumBuildings      = parent.cachedNumBuildings + (newAmt - oldAmt);
            this.cachedCommerce          = newComm;
            this.cachedPollution         = newPol;
            this.cachedDisease           = newDisease;
            this.cachedCrime             = newCrime;
            this.cachedPopulation        = newPop;
            this.cachedCivilianRevenue   = newCivilianRevenue;
            this.cachedRevenueConverted  = fixedRevenueConverted + newCivilianRevenue + newIncome - foodConverted;
        }

        @Override
        public double getRevenueConverted() { return cachedRevenueConverted; }

        @Override
        public double[] getProfit(double[] buffer) {
            return PW.City.profit(continent, rads, evalDate, hasProject, buffer, numCities, grossModifier, false, 12, this);
        }

        @Override
        public Boolean getPowered() { return Boolean.TRUE; }

        @Override
        public int getPoweredInfra() { return targetPoweredInfraInt; }

        @Override
        public double getInfra() { return targetInfra; }

        @Override
        public double getLand() { return targetLand; }

        @Override
        public int getBuilding(Building building) { return unpacked[building.ordinal()]; }

        @Override
        public int getBuildingOrdinal(int ordinal) { return unpacked[ordinal]; }

        @Override
        public int calcCommerce(Predicate<Project> hasProject) { return cachedCommerce; }

        @Override
        public int calcPopulation(Predicate<Project> hasProject) { return cachedPopulation; }

        @Override
        public double calcDisease(Predicate<Project> hasProject) { return cachedDisease; }

        @Override
        public double calcCrime(Predicate<Project> hasProject) { return cachedCrime; }

        @Override
        public int calcPollution(Predicate<Project> hasProject) { return cachedPollution; }

        @Override
        public long getCreated() { return originCreated; }

        @Override
        public int getNuke_turn() { return 0; }

        @Override
        public int getNumBuildings() { return cachedNumBuildings; }
    }

    private record DonorWorker(
            PackedCandidateView view,
            PackedCandidateView childView,
            long[] beamStates,
            long[] nextBeam,
            double[] topScores,
            Long2DoubleOpenHashMap localScoreCache,
            LongOpenHashSet beamSet
    ) {}

    private static final class ParallelAccumulator {
        private final DonorWorker worker;
        private EvaluatedCandidate best;

        private ParallelAccumulator(DonorWorker worker) {
            this.worker = worker;
        }
    }

    private record EvaluatedCandidate(DBCity donor, long state, double value, int donorId) { }
}
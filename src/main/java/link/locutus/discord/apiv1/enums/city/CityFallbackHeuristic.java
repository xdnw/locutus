package link.locutus.discord.apiv1.enums.city;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.db.entities.city.SimpleNationCity;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.Arrays;

final class CityFallbackHeuristic {
    private static final int BEAM_WIDTH = 8;

    private static final Building[] CIVILIAN_BUILDINGS = Arrays.stream(Buildings.values())
            .filter(f -> f.getType() != BuildingType.MILITARY && f.getType() != BuildingType.POWER)
            .toArray(Building[]::new);

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
    private final int[] civilianCaps; // parallel to CIVILIAN_BUILDINGS; -1 = unbuildable
    private final int[] civilianOrdinals;
    private final int[] ordinalToCivilianIndex;
    private final int[] civilianShift;
    private final int[] civilianBits;
    private final long[] civilianMask;
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
    private final int[] commerceOrdinals;
    private final int[] pollutionOrdinals;
    private final int[] commerceContributionByOrdinal;
    private final int[] pollutionContributionByOrdinal;
    private final double[] crimeByCommerce;
    private final long originCreated;
    private final long evalDate;
    private final int numCities;
    private final double rads;
    private final double grossModifier;
    private final boolean debugCounters;
    private final LongAdder valueFunctionCalls = new LongAdder();
    private final LongAdder generatedChildren = new LongAdder();
    private final LongAdder dedupSurvivors = new LongAdder();
    private final LongAdder normalizeNanos = new LongAdder();
    private final LongAdder repairNanos = new LongAdder();
    private final LongAdder fitNanos = new LongAdder();
    private final LongAdder validateNanos = new LongAdder();
    private final LongAdder goalNanos = new LongAdder();
    private final LongAdder scoreNanos = new LongAdder();
    private final LongAdder repairCalls = new LongAdder();
    private final LongAdder dropCalls = new LongAdder();
    private final LongAdder dropCandidateEvals = new LongAdder();
    private final LongAdder dropCandidateEvalNanos = new LongAdder();
    private final LongAdder fitCalls = new LongAdder();
    private final LongAdder fitApplyStateCalls = new LongAdder();
    private final LongAdder fitApplyStateNanos = new LongAdder();
    private final LongAdder fitChildScoreCalls = new LongAdder();
    private final LongAdder fitChildScoreNanos = new LongAdder();
    private final LongAdder fitFinalScoreCalls = new LongAdder();
    private final LongAdder fitFinalScoreNanos = new LongAdder();
    private final int maxBeamChildren;
    private final boolean enableLocalScoreCache;
    private final int localScoreCacheMaxSize;
    private final LongAdder scoreStateCalls = new LongAdder();
    private final LongAdder scoreStateCacheHitCalls = new LongAdder();
    private final LongAdder scoreStateCacheMissCalls = new LongAdder();
    private final LongAdder scoreStateLookupNanos = new LongAdder();
    private final LongAdder viewGetBuildingOrdinalCalls = new LongAdder();
    private final LongAdder viewGetBuildingOrdinalNanos = new LongAdder();
    private final LongAdder viewCalcCommerceCalls = new LongAdder();
    private final LongAdder viewCalcCommerceNanos = new LongAdder();
    private final LongAdder viewCalcPollutionCalls = new LongAdder();
    private final LongAdder viewCalcPollutionNanos = new LongAdder();
    private final LongAdder viewCalcDiseaseCalls = new LongAdder();
    private final LongAdder viewCalcDiseaseNanos = new LongAdder();
    private final LongAdder viewCalcCrimeCalls = new LongAdder();
    private final LongAdder viewCalcCrimeNanos = new LongAdder();
    private final LongAdder viewCalcPopulationCalls = new LongAdder();
    private final LongAdder viewCalcPopulationNanos = new LongAdder();

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
        // precompute per-building caps once
        this.civilianCaps = new int[CIVILIAN_BUILDINGS.length];
        this.civilianOrdinals = new int[CIVILIAN_BUILDINGS.length];
        this.ordinalToCivilianIndex = new int[PW.City.Building.SIZE];
        this.civilianShift = new int[CIVILIAN_BUILDINGS.length];
        this.civilianBits = new int[CIVILIAN_BUILDINGS.length];
        this.civilianMask = new long[CIVILIAN_BUILDINGS.length];
        Arrays.fill(this.ordinalToCivilianIndex, -1);
        int bitOffset = 0;
        for (int i = 0; i < CIVILIAN_BUILDINGS.length; i++) {
            Building b = CIVILIAN_BUILDINGS[i];
            civilianCaps[i] = b.canBuild(continent) ? b.getCap(hasProject) : -1;
            civilianOrdinals[i] = b.ordinal();
            ordinalToCivilianIndex[b.ordinal()] = i;

            int cap = civilianCaps[i];
            int bits = cap >= 0 ? bitsForCount(cap) : 0;
            civilianBits[i] = bits;
            civilianShift[i] = bitOffset;
            if (bits == 0) {
                civilianMask[i] = 0L;
                continue;
            }
            if (bitOffset + bits > 63) {
                throw new IllegalStateException("Packed civilian building layout exceeds 63 bits");
            }
            long localMask = bits == 63 ? -1L : ((1L << bits) - 1L);
            civilianMask[i] = localMask << bitOffset;
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
        this.hospitalPct = hasProject.test(link.locutus.discord.apiv1.enums.city.project.Projects.CLINICAL_RESEARCH_CENTER) ? 3.5d : 2.5d;
        this.policePct = hasProject.test(link.locutus.discord.apiv1.enums.city.project.Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5d : 2.5d;
        this.hasSpecializedPoliceTraining = hasProject.test(link.locutus.discord.apiv1.enums.city.project.Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM);
        this.commerceOrdinals = Arrays.stream(Buildings.COMMERCE_BUILDINGS).mapToInt(Building::ordinal).toArray();
        this.pollutionOrdinals = Arrays.stream(Buildings.POLLUTION_BUILDINGS).mapToInt(Building::ordinal).toArray();
        this.commerceContributionByOrdinal = new int[PW.City.Building.SIZE];
        this.pollutionContributionByOrdinal = new int[PW.City.Building.SIZE];
        for (Building building : Buildings.values()) {
            commerceContributionByOrdinal[building.ordinal()] = building.getCommerce();
            pollutionContributionByOrdinal[building.ordinal()] = building.pollution(hasProject);
        }
        this.basePopulation = targetInfra * 100d;
        this.diseaseBase = ((0.01 * MathMan.sqr((targetInfra * 100d) / (targetLand + 0.001d)) - 25d) * 0.01d) + (targetInfra * 0.001d);
        this.ageBonus = originCreated <= 0 || originCreated == Long.MAX_VALUE
            ? 1d
            : (1d + Math.log(Math.max(1d, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - originCreated))) * 0.06666666666666667d);
        this.newPlayerBonus = 1d + Math.max(1d - (numCities - 1) * 0.05d, 0d);
        this.crimeByCommerce = new double[maxCommerce + 1];
        for (int i = 0; i <= maxCommerce; i++) {
            crimeByCommerce[i] = Math.max(0d, (MathMan.sqr(103 - i) + basePopulation) * 0.000009d);
        }
        this.maxBeamChildren = Math.max(1, BEAM_WIDTH * CIVILIAN_BUILDINGS.length);
        this.enableLocalScoreCache = Boolean.getBoolean("locutus.cityFallback.localScoreCache");
        this.localScoreCacheMaxSize = Integer.getInteger("locutus.cityFallback.localScoreCacheMax", 262_144);
    }

    // --- public static entry points (unchanged signatures) ---

    static INationCity findBest(ICity source,
                                Continent continent,
                                int numCities,
                                ToDoubleFunction<INationCity> valueFunction,
                                Predicate<INationCity> goal,
                                Predicate<Project> hasProject,
                                double rads,
                                double grossModifier,
                                Double infraLow,
                                Iterable<DBCity> donors) {
        CityFallbackHeuristic ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
        return ctx == null ? null : ctx.search(donors, true);
    }

    static INationCity findBestExactSlotOnly(ICity source,
                                             Continent continent,
                                             int numCities,
                                             ToDoubleFunction<INationCity> valueFunction,
                                             Predicate<INationCity> goal,
                                             Predicate<Project> hasProject,
                                             double rads,
                                             double grossModifier,
                                             Double infraLow,
                                             Iterable<DBCity> donors) {
        CityFallbackHeuristic ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
        return ctx == null ? null : ctx.search(donors, false);
    }

    // --- shared setup ---

    private static CityFallbackHeuristic create(ICity source,
                                                 Continent continent,
                                                 int numCities,
                                                 ToDoubleFunction<INationCity> valueFunction,
                                                 Predicate<INationCity> goal,
                                                 Predicate<Project> hasProject,
                                                 double rads,
                                                 double grossModifier,
                                                 Double infraLow) {
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

        return new CityFallbackHeuristic(
                continent, hasProject, targetInfra, origin.getLand(),
                civilianSlots, milSig, powSig,
                valueFunction, goal, getRevenue, convertedFunc, origin, sig,
                numCities, rads, grossModifier, date);
    }

    // --- unified search loop ---

    private INationCity search(Iterable<DBCity> donors, boolean useBeamSearch) {
        long searchStart = debugCounters ? System.nanoTime() : 0L;
        ObjectArrayList<DBCity> donorList = new ObjectArrayList<>();
        for (DBCity donor : donors) {
            donorList.add(donor);
        }
        if (donorList.isEmpty()) {
            return null;
        }

        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), donorList.size());

        EvaluatedCandidate best;
        if (parallelism <= 1) {
            best = null;
            DonorWorker worker = newDonorWorker();
            for (DBCity donor : donorList) {
                EvaluatedCandidate candidate = evaluateDonor(donor, useBeamSearch, worker);
                best = chooseBetter(best, candidate);
            }
        } else {
            ThreadLocal<DonorWorker> workers = ThreadLocal.withInitial(this::newDonorWorker);
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            try {
                best = pool.submit(() -> donorList.parallelStream()
                        .map(donor -> evaluateDonor(donor, useBeamSearch, workers.get()))
                        .reduce(null, this::chooseBetter, this::chooseBetter)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while evaluating fallback donors", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed during fallback donor evaluation", e.getCause());
            } finally {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (debugCounters) {
            System.out.println("CityFallbackHeuristic counters: valueCalls=" + valueFunctionCalls.sum()
                    + ", scoreStateCalls=" + scoreStateCalls.sum()
                    + ", scoreCacheHits=" + scoreStateCacheHitCalls.sum()
                    + ", scoreCacheMisses=" + scoreStateCacheMissCalls.sum()
                    + ", localScoreCacheEnabled=" + enableLocalScoreCache
                    + ", generatedChildren=" + generatedChildren.sum()
                    + ", dedupSurvivors=" + dedupSurvivors.sum()
                    + ", normalizeMs=" + nanosToMillis(normalizeNanos.sum())
                    + ", repairMs=" + nanosToMillis(repairNanos.sum())
                    + ", fitMs=" + nanosToMillis(fitNanos.sum())
                    + ", validateMs=" + nanosToMillis(validateNanos.sum())
                    + ", goalMs=" + nanosToMillis(goalNanos.sum())
                    + ", scoreMs=" + nanosToMillis(scoreNanos.sum())
                    + ", scoreLookupMs=" + nanosToMillis(scoreStateLookupNanos.sum())
                    + ", viewGetBuildingMs=" + nanosToMillis(viewGetBuildingOrdinalNanos.sum())
                    + ", viewCommerceMs=" + nanosToMillis(viewCalcCommerceNanos.sum())
                    + ", viewPollutionMs=" + nanosToMillis(viewCalcPollutionNanos.sum())
                    + ", viewDiseaseMs=" + nanosToMillis(viewCalcDiseaseNanos.sum())
                    + ", viewCrimeMs=" + nanosToMillis(viewCalcCrimeNanos.sum())
                    + ", viewPopulationMs=" + nanosToMillis(viewCalcPopulationNanos.sum()));
            printHotspotSummary(System.nanoTime() - searchStart);
        }

        if (best == null) {
            return null;
        }
        SimpleNationCity materialized = new SimpleNationCity(best.donor(), getRevenue, convertedFunc);
        normalizeInto(materialized, best.donor());
        applyPackedState(materialized, best.state());
        return materialized;
    }

    private EvaluatedCandidate evaluateDonor(DBCity donor, boolean useBeamSearch, DonorWorker worker) {
        long t0 = debugCounters ? System.nanoTime() : 0L;
        long donorState = packCivilianState(donor);
        if (debugCounters) {
            normalizeNanos.add(System.nanoTime() - t0);
        }
        long candidateState;
        int candidateCivilianCount;
        PackedCandidateView view = worker.view();
        view.setCivilianState(donorState);

        if (useBeamSearch) {
            t0 = debugCounters ? System.nanoTime() : 0L;
            long repairedState = repairBeamBaseline(view.civilianState(), view, worker);
            if (debugCounters) {
                repairNanos.add(System.nanoTime() - t0);
            }
            if (repairedState == Long.MIN_VALUE) {
                return null;
            }
            int repairedCount = getPackedCivilianCount(repairedState);

            t0 = debugCounters ? System.nanoTime() : 0L;
            candidateState = fitCivilianSlots(repairedState, repairedCount, worker, view);
            if (debugCounters) {
                fitNanos.add(System.nanoTime() - t0);
            }
            if (candidateState == Long.MIN_VALUE) {
                return null;
            }
            view.setCivilianState(candidateState);
            t0 = debugCounters ? System.nanoTime() : 0L;
            boolean valid = isValidCandidate(view);
            if (debugCounters) {
                validateNanos.add(System.nanoTime() - t0);
            }
            if (!valid) {
                return null;
            }
            candidateCivilianCount = targetCivilianSlots;
        } else {
            if (getPackedCivilianCount(view.civilianState()) != targetCivilianSlots) {
                return null;
            }
            candidateState = view.civilianState();
            candidateCivilianCount = targetCivilianSlots;
            t0 = debugCounters ? System.nanoTime() : 0L;
            boolean valid = isValidCandidate(view);
            if (debugCounters) {
                validateNanos.add(System.nanoTime() - t0);
            }
            if (!valid) {
                return null;
            }
        }

        if (candidateCivilianCount != targetCivilianSlots) {
            return null;
        }

        t0 = debugCounters ? System.nanoTime() : 0L;
        view.setCivilianState(candidateState);
        boolean goalPassed = goal == null || goal.test(view);
        if (debugCounters) {
            goalNanos.add(System.nanoTime() - t0);
        }
        if (!goalPassed) {
            return null;
        }
        double value = scoreState(candidateState, view, worker);
        if (!Double.isFinite(value)) {
            return null;
        }
        return new EvaluatedCandidate(donor, candidateState, value, donor.getId());
    }

    private EvaluatedCandidate chooseBetter(EvaluatedCandidate left, EvaluatedCandidate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (right.value() > left.value()) {
            return right;
        }
        if (right.value() < left.value()) {
            return left;
        }
        return right.donorId() < left.donorId() ? right : left;
    }

    private long repairBeamBaseline(long startState, PackedCandidateView view, DonorWorker worker) {
        if (debugCounters) {
            repairCalls.increment();
        }
        int civilianCount = 0;
        long repairedState = startState;

        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            int current = getPackedCount(repairedState, bi);
            if (current <= 0) {
                continue;
            }

            int cap = civilianCaps[bi];
            int clamped = cap < 0 ? 0 : Math.min(current, cap);
            if (clamped != current) {
                repairedState = setPackedCount(repairedState, bi, clamped);
            }
            civilianCount += clamped;
        }

        int overflow = civilianCount - targetCivilianSlots;
        while (overflow > 0) {
            if (debugCounters) {
                dropCalls.increment();
            }
            long updated = dropLeastHarmfulCivilian(repairedState, view, worker);
            if (updated == Long.MIN_VALUE) {
                return Long.MIN_VALUE;
            }
            repairedState = updated;
            overflow--;
        }

        return repairedState;
    }

    private long dropLeastHarmfulCivilian(long state, PackedCandidateView view, DonorWorker worker) {
        int bestIndex = -1;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            int current = getPackedCount(state, bi);
            if (current <= 0) {
                continue;
            }

            long evalStart = debugCounters ? System.nanoTime() : 0L;
            long probeState = setPackedCount(state, bi, current - 1);
            double value = scoreState(probeState, view, worker);
            if (debugCounters) {
                dropCandidateEvals.increment();
                dropCandidateEvalNanos.add(System.nanoTime() - evalStart);
            }
            if (!Double.isFinite(value)) {
                continue;
            }

            if (value > bestValue) {
                bestValue = value;
                bestIndex = bi;
            }
        }

        if (bestIndex < 0) {
            return Long.MIN_VALUE;
        }

        return setPackedCount(state, bestIndex, getPackedCount(state, bestIndex) - 1);
    }

    private boolean isStructurallyFeasible(ICity candidate) {
        if (!candidate.canBuild(continent, hasProject, false)) return false;
        if (candidate.getRequiredInfra() > targetInfra) return false;
        return candidate.getPoweredInfra() >= targetInfra;
    }

    private boolean isFastStructurallyFeasible(int civilianCount) {
        return hasSufficientPower && civilianCount <= maxFeasibleCivilianCount;
    }

    // --- normalization ---

    private DonorWorker newDonorWorker() {
        Long2DoubleOpenHashMap localScoreCache = enableLocalScoreCache ? new Long2DoubleOpenHashMap(1 << 12) : null;
        return new DonorWorker(new PackedCandidateView(),
                new long[BEAM_WIDTH],
                new int[BEAM_WIDTH],
                new long[maxBeamChildren],
                new int[maxBeamChildren],
            new double[maxBeamChildren],
            localScoreCache);
    }

    private void normalizeInto(SimpleNationCity target, DBCity donor) {
        target.set(donor, true);
        target.setNuke_turn(0);
        target.setLand(targetLand);
        target.setInfra(targetInfra);
        target.setDateCreated(origin.getCreated());
        target.setMilitaryBuildings(origin);
        target.setPowerBuildings(targetSignature);
    }

    // --- beam search ---

    private long fitCivilianSlots(long startState, int startCivilianCount, DonorWorker worker, PackedCandidateView view) {
        if (debugCounters) {
            fitCalls.increment();
        }
        int steps = Math.abs(targetCivilianSlots - startCivilianCount);
        if (steps == 0) return startState;

        boolean addMode = targetCivilianSlots > startCivilianCount;
        long[] beamStates = worker.beamStates();
        int[] beamCounts = worker.beamCounts();
        long[] childStates = worker.childStates();
        int[] childCounts = worker.childCounts();
        double[] childScores = worker.childScores();
        int beamSize = 1;
        beamStates[0] = startState;
        beamCounts[0] = startCivilianCount;

        for (int i = 0; i < steps; i++) {
            int stepCivilianCount = addMode ? (startCivilianCount + i + 1) : (startCivilianCount - i - 1);
            if (!isFastStructurallyFeasible(stepCivilianCount)) {
                return Long.MIN_VALUE;
            }
            int childSize = 0;
            for (int beamIndex = 0; beamIndex < beamSize; beamIndex++) {
                long parentState = beamStates[beamIndex];
                int parentCivilianCount = beamCounts[beamIndex];
                if (!isFastStructurallyFeasible(parentCivilianCount)) {
                    continue;
                }

                for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
                    int cap = civilianCaps[bi];
                    int amt = getPackedCount(parentState, bi);
                    if (addMode) {
                        if (cap < 0 || amt >= cap) continue;
                    } else {
                        if (amt <= 0) continue;
                    }

                    int newAmt = addMode ? amt + 1 : amt - 1;
                    int childCivilianCount = addMode ? parentCivilianCount + 1 : parentCivilianCount - 1;
                    // childCivilianCount feasibility is hoisted at step level

                    if (debugCounters) {
                        generatedChildren.increment();
                    }
                    long childScoreStart = debugCounters ? System.nanoTime() : 0L;
                    long childState = setPackedCount(parentState, bi, newAmt);
                    double value = scoreState(childState, view, worker);
                    if (debugCounters) {
                        fitChildScoreCalls.increment();
                        fitChildScoreNanos.add(System.nanoTime() - childScoreStart);
                    }

                    if (!Double.isFinite(value)) continue;

                    int existingIdx = -1;
                    for (int ci = 0; ci < childSize; ci++) {
                        if (childStates[ci] == childState) {
                            existingIdx = ci;
                            break;
                        }
                    }
                    if (existingIdx >= 0) {
                        if (value > childScores[existingIdx]) {
                            childScores[existingIdx] = value;
                        }
                    } else {
                        childStates[childSize] = childState;
                        childCounts[childSize] = childCivilianCount;
                        childScores[childSize] = value;
                        childSize++;
                    }
                }
            }

            if (childSize == 0) return Long.MIN_VALUE;

            if (debugCounters) {
                dedupSurvivors.add(childSize);
            }

            int keep = Math.min(BEAM_WIDTH, childSize);
            for (int slot = 0; slot < keep; slot++) {
                int bestIdx = slot;
                double bestScore = childScores[slot];
                for (int ci = slot + 1; ci < childSize; ci++) {
                    if (childScores[ci] > bestScore) {
                        bestScore = childScores[ci];
                        bestIdx = ci;
                    }
                }
                if (bestIdx != slot) {
                    long swapState = childStates[slot];
                    childStates[slot] = childStates[bestIdx];
                    childStates[bestIdx] = swapState;

                    int swapCount = childCounts[slot];
                    childCounts[slot] = childCounts[bestIdx];
                    childCounts[bestIdx] = swapCount;

                    double swapScore = childScores[slot];
                    childScores[slot] = childScores[bestIdx];
                    childScores[bestIdx] = swapScore;
                }
            }

            beamSize = keep;
            for (int j = 0; j < keep; j++) {
                beamStates[j] = childStates[j];
                beamCounts[j] = childCounts[j];
            }
        }

        // pick highest-value candidate that hit the target count
        long bestState = Long.MIN_VALUE;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < beamSize; i++) {
            long state = beamStates[i];
            if (getPackedCivilianCount(state) != targetCivilianSlots) continue;
            view.setCivilianState(state);
            if (!isValidCandidate(view)) continue;
            long finalScoreStart = debugCounters ? System.nanoTime() : 0L;
            double value = scoreState(state, view, worker);
            if (debugCounters) {
                fitFinalScoreCalls.increment();
                fitFinalScoreNanos.add(System.nanoTime() - finalScoreStart);
            }
            if (!Double.isFinite(value)) continue;
            if (value > bestValue) {
                bestValue = value;
                bestState = state;
            }
        }
        return bestState;
    }

    // --- validation ---

    private boolean isValidCandidate(ICity candidate) {
        return isStructurallyFeasible(candidate);
    }

    private double score(INationCity city) {
        if (!debugCounters) {
            return valueFunction.applyAsDouble(city);
        }
        long start = System.nanoTime();
        try {
            valueFunctionCalls.increment();
            return valueFunction.applyAsDouble(city);
        } finally {
            scoreNanos.add(System.nanoTime() - start);
        }
    }

    private static String nanosToMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000d);
    }

    private void printHotspotSummary(long searchNanos) {
        long wall = Math.max(1L, searchNanos);
        long normalize = normalizeNanos.sum();
        long repair = repairNanos.sum();
        long fit = fitNanos.sum();
        long validate = validateNanos.sum();
        long goal = goalNanos.sum();
        long topLevelTotal = Math.max(1L, normalize + repair + fit + validate + goal);

        ArrayList<Hotspot> topLevel = new ArrayList<>();
        topLevel.add(new Hotspot("repairBeamBaseline", repair, repairCalls.sum()));
        topLevel.add(new Hotspot("fitCivilianSlots", fit, fitCalls.sum()));
        topLevel.add(new Hotspot("normalize", normalize, 0));
        topLevel.add(new Hotspot("validate", validate, 0));
        topLevel.add(new Hotspot("goal", goal, 0));
        topLevel.sort(Comparator.comparingLong(Hotspot::nanos).reversed());

        System.out.printf(Locale.ROOT,
                "CityFallbackHeuristic hotspots: wall=%.3f ms, topLevelCpu=%.3f ms%n",
                wall / 1_000_000d,
                topLevelTotal / 1_000_000d);

        int rank = 1;
        for (Hotspot hotspot : topLevel) {
            if (hotspot.nanos() <= 0) continue;
            double pct = (hotspot.nanos() * 100d) / topLevelTotal;
            if (hotspot.calls() > 0) {
                double avgMicros = hotspot.nanos() / (double) hotspot.calls() / 1_000d;
                System.out.printf(Locale.ROOT,
                        "  #%d %s: %.3f ms (%.2f%% of top-level), calls=%d, avg=%.3f us%n",
                        rank++, hotspot.name(), hotspot.nanos() / 1_000_000d, pct, hotspot.calls(), avgMicros);
            } else {
                System.out.printf(Locale.ROOT,
                        "  #%d %s: %.3f ms (%.2f%% of top-level)%n",
                        rank++, hotspot.name(), hotspot.nanos() / 1_000_000d, pct);
            }
        }

        long repairEval = dropCandidateEvalNanos.sum();
        long fitApply = fitApplyStateNanos.sum();
        long fitChildScore = fitChildScoreNanos.sum();
        long fitFinalScore = fitFinalScoreNanos.sum();
        long scoreTotal = scoreNanos.sum();
        System.out.printf(Locale.ROOT,
                "  repair breakdown: dropEval=%.3f ms, dropCalls=%d, dropCandidates=%d%n",
                repairEval / 1_000_000d,
                dropCalls.sum(),
                dropCandidateEvals.sum());
        System.out.printf(Locale.ROOT,
                "  fit breakdown: applyState=%.3f ms (calls=%d), childScore=%.3f ms (calls=%d), finalScore=%.3f ms (calls=%d)%n",
                fitApply / 1_000_000d,
                fitApplyStateCalls.sum(),
                fitChildScore / 1_000_000d,
                fitChildScoreCalls.sum(),
                fitFinalScore / 1_000_000d,
                fitFinalScoreCalls.sum());
        System.out.printf(Locale.ROOT,
                "  valueFunction total: %.3f ms (calls=%d, avg=%.3f us)%n",
                scoreTotal / 1_000_000d,
                valueFunctionCalls.sum(),
                valueFunctionCalls.sum() > 0 ? (scoreTotal / (double) valueFunctionCalls.sum()) / 1_000d : 0d);
    }

    private record Hotspot(String name, long nanos, long calls) {
    }

    // --- signature helpers ---

    private static int bitsForCount(int cap) {
        if (cap <= 1) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(cap);
    }

    private long packCivilianState(ICity city) {
        long state = 0L;
        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            int value = city.getBuilding(CIVILIAN_BUILDINGS[bi]);
            state = setPackedCount(state, bi, value);
        }
        return state;
    }

    private int getPackedCount(long state, int bi) {
        return (int) ((state & civilianMask[bi]) >>> civilianShift[bi]);
    }

    private long setPackedCount(long state, int bi, int newAmt) {
        long mask = civilianMask[bi];
        long shifted = ((long) newAmt << civilianShift[bi]) & mask;
        return (state & ~mask) | shifted;
    }

    private double scoreState(long state, PackedCandidateView view, DonorWorker worker) {
        if (debugCounters) {
            scoreStateCalls.increment();
        }
        long lookupStart = debugCounters ? System.nanoTime() : 0L;
        Long2DoubleOpenHashMap local = worker.localScoreCache();
        if (local != null && local.containsKey(state)) {
            if (debugCounters) {
                scoreStateCacheHitCalls.increment();
                scoreStateLookupNanos.add(System.nanoTime() - lookupStart);
            }
            return local.get(state);
        }
        if (debugCounters) {
            scoreStateCacheMissCalls.increment();
            scoreStateLookupNanos.add(System.nanoTime() - lookupStart);
        }

        view.setCivilianState(state);
        double computed = score(view);
        if (local != null) {
            local.put(state, computed);
            if (local.size() > localScoreCacheMaxSize) {
                local.clear();
            }
        }
        return computed;
    }

    private int getPackedCivilianCount(long state) {
        int total = 0;
        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            total += getPackedCount(state, bi);
        }
        return total;
    }

    private void applyPackedState(SimpleNationCity city, long state) {
        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            city.setBuilding(CIVILIAN_BUILDINGS[bi], getPackedCount(state, bi));
        }
    }

    private static int[] getMilitarySignature(ICity city) {
        return new int[]{
                city.getBuilding(Buildings.BARRACKS),
                city.getBuilding(Buildings.FACTORY),
                city.getBuilding(Buildings.HANGAR),
                city.getBuilding(Buildings.DRYDOCK)
        };
    }

    private static int[] getPowerSignature(ICity city) {
        return new int[]{
                city.getBuilding(Buildings.COAL_POWER),
                city.getBuilding(Buildings.OIL_POWER),
                city.getBuilding(Buildings.NUCLEAR_POWER),
                city.getBuilding(Buildings.WIND_POWER)
        };
    }

    private static int getMaxCommerce(Predicate<Project> hasProject) {
        if (hasProject.test(link.locutus.discord.apiv1.enums.city.project.Projects.INTERNATIONAL_TRADE_CENTER)) {
            if (hasProject.test(link.locutus.discord.apiv1.enums.city.project.Projects.TELECOMMUNICATIONS_SATELLITE)) {
                return 125;
            }
            return 115;
        }
        return 100;
    }

    private final class PackedCandidateView implements INationCity {
        private long civilianState;

        void setCivilianState(long civilianState) {
            this.civilianState = civilianState;
        }

        long civilianState() {
            return civilianState;
        }

        @Override
        public double getRevenueConverted() {
            return PW.City.profitConverted(continent, rads, hasProject, numCities, grossModifier, this);
        }

        @Override
        public double[] getProfit(double[] buffer) {
            return PW.City.profit(continent, rads, evalDate, hasProject, buffer, numCities, grossModifier, false, 12, this);
        }

        @Override
        public Boolean getPowered() {
            return Boolean.TRUE;
        }

        @Override
        public int getPoweredInfra() {
            return targetPoweredInfraInt;
        }

        @Override
        public double getInfra() {
            return targetInfra;
        }

        @Override
        public double getLand() {
            return targetLand;
        }

        @Override
        public int getBuilding(Building building) {
            return getBuildingOrdinal(building.ordinal());
        }

        @Override
        public int getBuildingOrdinal(int ordinal) {
            long start = debugCounters ? System.nanoTime() : 0L;
            int civilianIndex = ordinalToCivilianIndex[ordinal];
            int value;
            if (civilianIndex >= 0) {
                value = getPackedCount(civilianState, civilianIndex);
            } else {
                value = fixedBuildingCounts[ordinal];
            }
            if (debugCounters) {
                viewGetBuildingOrdinalCalls.increment();
                viewGetBuildingOrdinalNanos.add(System.nanoTime() - start);
            }
            return value;
        }

        @Override
        public int calcCommerce(Predicate<Project> hasProject) {
            long start = debugCounters ? System.nanoTime() : 0L;
            int commerce = hasSpecializedPoliceTraining ? 4 : 0;
            for (int ordinal : commerceOrdinals) {
                int amt = getBuildingOrdinal(ordinal);
                if (amt == 0) continue;
                commerce += amt * commerceContributionByOrdinal[ordinal];
            }
            if (commerce > maxCommerce) {
                commerce = maxCommerce;
            }
            if (debugCounters) {
                viewCalcCommerceCalls.increment();
                viewCalcCommerceNanos.add(System.nanoTime() - start);
            }
            return commerce;
        }

        @Override
        public int calcPopulation(Predicate<Project> hasProject) {
            long start = debugCounters ? System.nanoTime() : 0L;
            double disease = calcDisease(hasProject);
            double crime = calcCrime(hasProject);
            double diseaseDeaths = (disease * 0.01d) * basePopulation;
            double crimeDeaths = Math.max((crime * 0.1d) * basePopulation - 25d, 0d);
            int population = (int) Math.round(Math.max(10d, (basePopulation - diseaseDeaths - crimeDeaths) * ageBonus));
            if (debugCounters) {
                viewCalcPopulationCalls.increment();
                viewCalcPopulationNanos.add(System.nanoTime() - start);
            }
            return population;
        }

        @Override
        public double calcDisease(Predicate<Project> hasProject) {
            long start = debugCounters ? System.nanoTime() : 0L;
            int hospitals = getBuildingOrdinal(Buildings.HOSPITAL.ordinal());
            int pollution = calcPollution(hasProject);
            double hospitalModifier = hospitals > 0 ? hospitals * hospitalPct : 0d;
            double disease = Math.max(0d, diseaseBase - hospitalModifier + pollution * 0.05d);
            if (debugCounters) {
                viewCalcDiseaseCalls.increment();
                viewCalcDiseaseNanos.add(System.nanoTime() - start);
            }
            return disease;
        }

        @Override
        public double calcCrime(Predicate<Project> hasProject) {
            long start = debugCounters ? System.nanoTime() : 0L;
            int commerce = calcCommerce(hasProject);
            int police = getBuildingOrdinal(Buildings.POLICE_STATION.ordinal());
            double policeModifier = police > 0 ? police * policePct : 0d;
            double crime = Math.max(0d, crimeByCommerce[commerce] - policeModifier);
            if (debugCounters) {
                viewCalcCrimeCalls.increment();
                viewCalcCrimeNanos.add(System.nanoTime() - start);
            }
            return crime;
        }

        @Override
        public int calcPollution(Predicate<Project> hasProject) {
            long start = debugCounters ? System.nanoTime() : 0L;
            int pollution = 0;
            for (int ordinal : pollutionOrdinals) {
                int amt = getBuildingOrdinal(ordinal);
                if (amt == 0) continue;
                pollution += amt * pollutionContributionByOrdinal[ordinal];
            }
            int adjusted = Math.max(0, pollution);
            if (debugCounters) {
                viewCalcPollutionCalls.increment();
                viewCalcPollutionNanos.add(System.nanoTime() - start);
            }
            return adjusted;
        }

        @Override
        public long getCreated() {
            return originCreated;
        }

        @Override
        public int getNuke_turn() {
            return 0;
        }

        @Override
        public int getNumBuildings() {
            return fixedBuildingTotal + getPackedCivilianCount(civilianState);
        }
    }

    private record DonorWorker(PackedCandidateView view,
                               long[] beamStates,
                               int[] beamCounts,
                               long[] childStates,
                               int[] childCounts,
                               double[] childScores,
                               Long2DoubleOpenHashMap localScoreCache) {
    }

    private record EvaluatedCandidate(DBCity donor, long state, double value, int donorId) {
    }
}

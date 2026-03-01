package link.locutus.discord.apiv1.enums.city;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.db.entities.city.SimpleNationCity;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.Arrays;

final class CityFallbackHeuristicOld {
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
    private final int[] civilianShift;
    private final int[] civilianBits;
    private final long[] civilianMask;
    private final double fixedRequiredInfra;
    private final double originRequiredInfra;
    private final double targetPoweredInfra;
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

    private CityFallbackHeuristicOld(Continent continent,
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
                                  ICity targetSignature) {
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
        this.debugCounters = Boolean.getBoolean("locutus.cityFallback.debugCounters");
        // precompute per-building caps once
        this.civilianCaps = new int[CIVILIAN_BUILDINGS.length];
        this.civilianOrdinals = new int[CIVILIAN_BUILDINGS.length];
        this.civilianShift = new int[CIVILIAN_BUILDINGS.length];
        this.civilianBits = new int[CIVILIAN_BUILDINGS.length];
        this.civilianMask = new long[CIVILIAN_BUILDINGS.length];
        int bitOffset = 0;
        for (int i = 0; i < CIVILIAN_BUILDINGS.length; i++) {
            Building b = CIVILIAN_BUILDINGS[i];
            civilianCaps[i] = b.canBuild(continent) ? b.getCap(hasProject) : -1;
            civilianOrdinals[i] = b.ordinal();

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
        this.fixedRequiredInfra = (Arrays.stream(targetMilitarySignature).sum() + Arrays.stream(targetPowerSignature).sum()) * 50d;
        this.originRequiredInfra = origin.getRequiredInfra();
        this.targetPoweredInfra = targetSignature.getPoweredInfra();
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
        CityFallbackHeuristicOld ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
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
        CityFallbackHeuristicOld ctx = create(source, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow);
        return ctx == null ? null : ctx.search(donors, false);
    }

    // --- shared setup ---

    private static CityFallbackHeuristicOld create(ICity source,
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

        return new CityFallbackHeuristicOld(
                continent, hasProject, targetInfra, origin.getLand(),
                civilianSlots, milSig, powSig,
                valueFunction, goal, getRevenue, convertedFunc, origin, sig);
    }

    // --- unified search loop ---

    private INationCity search(Iterable<DBCity> donors, boolean useBeamSearch) {
        ObjectArrayList<DBCity> donorList = new ObjectArrayList<>();
        for (DBCity donor : donors) {
            donorList.add(donor);
        }
        if (donorList.isEmpty()) {
            return null;
        }

        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), donorList.size());
        ThreadLocal<LongOpenHashSet> localSeen = useBeamSearch
                ? ThreadLocal.withInitial(() -> new LongOpenHashSet(Math.max(16, BEAM_WIDTH * CIVILIAN_BUILDINGS.length)))
                : null;

        EvaluatedCandidate best;
        if (parallelism <= 1) {
            best = null;
            for (DBCity donor : donorList) {
                EvaluatedCandidate candidate = evaluateDonor(donor, useBeamSearch, localSeen);
                best = chooseBetter(best, candidate);
            }
        } else {
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            try {
                best = pool.submit(() -> donorList.parallelStream()
                        .map(donor -> evaluateDonor(donor, useBeamSearch, localSeen))
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
            System.out.println("CityFallbackHeuristicOld counters: valueCalls=" + valueFunctionCalls.sum()
                    + ", generatedChildren=" + generatedChildren.sum()
                    + ", dedupSurvivors=" + dedupSurvivors.sum()
                    + ", normalizeMs=" + nanosToMillis(normalizeNanos.sum())
                    + ", repairMs=" + nanosToMillis(repairNanos.sum())
                    + ", fitMs=" + nanosToMillis(fitNanos.sum())
                    + ", validateMs=" + nanosToMillis(validateNanos.sum())
                    + ", goalMs=" + nanosToMillis(goalNanos.sum())
                    + ", scoreMs=" + nanosToMillis(scoreNanos.sum()));
        }

        return best == null ? null : best.candidate();
    }

    private EvaluatedCandidate evaluateDonor(DBCity donor, boolean useBeamSearch, ThreadLocal<LongOpenHashSet> localSeen) {
        long t0 = System.nanoTime();
        SimpleNationCity normalized = normalize(donor);
        normalizeNanos.add(System.nanoTime() - t0);
        SimpleNationCity candidate;

        if (useBeamSearch) {
            t0 = System.nanoTime();
            SimpleNationCity repaired = repairBeamBaseline(normalized);
            repairNanos.add(System.nanoTime() - t0);
            if (repaired == null) {
                return null;
            }

            LongOpenHashSet seen = localSeen.get();
            long civSig = packCivilianState(repaired);
            if (!seen.add(civSig)) {
                return null;
            }

            t0 = System.nanoTime();
            candidate = fitCivilianSlots(repaired);
            fitNanos.add(System.nanoTime() - t0);
            if (candidate == null) {
                return null;
            }
            t0 = System.nanoTime();
            boolean valid = isValidCandidate(candidate);
            validateNanos.add(System.nanoTime() - t0);
            if (!valid) {
                return null;
            }
        } else {
            if (getCivilianCount(normalized) != targetCivilianSlots) {
                return null;
            }
            t0 = System.nanoTime();
            boolean valid = isValidCandidate(normalized);
            validateNanos.add(System.nanoTime() - t0);
            if (!valid) {
                return null;
            }
            candidate = normalized;
        }

        t0 = System.nanoTime();
        boolean goalPassed = goal == null || goal.test(candidate);
        goalNanos.add(System.nanoTime() - t0);
        if (!goalPassed) {
            return null;
        }
        double value = score(candidate);
        if (!Double.isFinite(value)) {
            return null;
        }
        return new EvaluatedCandidate(candidate, value, donor.getId());
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

    private SimpleNationCity repairBeamBaseline(SimpleNationCity source) {
        SimpleNationCity repaired = new SimpleNationCity(source, getRevenue, convertedFunc);
        int civilianCount = 0;

        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            Building building = CIVILIAN_BUILDINGS[bi];
            int current = repaired.getBuilding(building);
            if (current <= 0) {
                continue;
            }

            int cap = civilianCaps[bi];
            int clamped = cap < 0 ? 0 : Math.min(current, cap);
            if (clamped != current) {
                repaired.setBuilding(building, clamped);
            }
            civilianCount += clamped;
        }

        long packedState = packCivilianState(repaired);
        int overflow = civilianCount - targetCivilianSlots;
        while (overflow > 0) {
            long updated = dropLeastHarmfulCivilian(repaired, packedState);
            if (updated == Long.MIN_VALUE) {
                return null;
            }
            packedState = updated;
            overflow--;
        }

        return repaired;
    }

    private long dropLeastHarmfulCivilian(SimpleNationCity candidate, long state) {
        applyPackedState(candidate, state);
        int bestIndex = -1;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            Building building = CIVILIAN_BUILDINGS[bi];
            int current = candidate.getBuilding(building);
            if (current <= 0) {
                continue;
            }

            candidate.setBuilding(building, current - 1);
            double value = score(candidate);
            candidate.setBuilding(building, current);
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

        Building selected = CIVILIAN_BUILDINGS[bestIndex];
        candidate.setBuilding(selected, candidate.getBuilding(selected) - 1);
        return setPackedCount(state, bestIndex, getPackedCount(state, bestIndex) - 1);
    }

    private boolean isStructurallyFeasible(ICity candidate) {
        if (!candidate.canBuild(continent, hasProject, false)) return false;
        if (candidate.getRequiredInfra() > targetInfra) return false;
        return candidate.getPoweredInfra() >= targetInfra;
    }

    private boolean isFastStructurallyFeasible(int civilianCount) {
        if (targetPoweredInfra < targetInfra) {
            return false;
        }
        return fixedRequiredInfra + (civilianCount * 50d) <= targetInfra;
    }

    // --- normalization ---

    private SimpleNationCity normalize(DBCity donor) {
        SimpleNationCity copy = new SimpleNationCity(donor, getRevenue, convertedFunc);
        copy.setNuke_turn(0);
        copy.setLand(targetLand);
        copy.setInfra(targetInfra);
        copy.setDateCreated(origin.getCreated());
        copy.setMilitaryBuildings(origin);
        copy.setPowerBuildings(targetSignature);
        return copy;
    }

    // --- beam search ---

    private SimpleNationCity fitCivilianSlots(SimpleNationCity start) {
        int current = getCivilianCount(start);
        int steps = Math.abs(targetCivilianSlots - current);
        if (steps == 0) return start;

        boolean addMode = targetCivilianSlots > current;
        long[] beamStates = new long[BEAM_WIDTH];
        int beamSize = 1;
        beamStates[0] = packCivilianState(start);

        Long2ObjectOpenHashMap<PackedScoredCandidate> deduped = new Long2ObjectOpenHashMap<>(BEAM_WIDTH * CIVILIAN_BUILDINGS.length);
        ArrayList<PackedScoredCandidate> ordered = new ArrayList<>(BEAM_WIDTH * CIVILIAN_BUILDINGS.length);
        SimpleNationCity working = new SimpleNationCity(start, getRevenue, convertedFunc);

        for (int i = 0; i < steps; i++) {
            deduped.clear();
            for (int beamIndex = 0; beamIndex < beamSize; beamIndex++) {
                long parentState = beamStates[beamIndex];
                int parentCivilianCount = getPackedCivilianCount(parentState);
                if (!isFastStructurallyFeasible(parentCivilianCount)) {
                    continue;
                }

                applyPackedState(working, parentState);
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
                    if (!isFastStructurallyFeasible(childCivilianCount)) {
                        continue;
                    }

                    Building building = CIVILIAN_BUILDINGS[bi];
                    working.setBuilding(building, newAmt);
                    generatedChildren.increment();
                    double value = score(working);
                    working.setBuilding(building, amt);

                    if (!Double.isFinite(value)) continue;

                    long childState = setPackedCount(parentState, bi, newAmt);
                    PackedScoredCandidate existing = deduped.get(childState);
                    if (existing == null || value > existing.value()) {
                        deduped.put(childState, new PackedScoredCandidate(childState, value));
                    }
                }
            }

            if (deduped.isEmpty()) return null;

            dedupSurvivors.add(deduped.size());
            ordered.clear();
            for (Long2ObjectMap.Entry<PackedScoredCandidate> entry : deduped.long2ObjectEntrySet()) {
                ordered.add(entry.getValue());
            }
            ordered.sort(Comparator.comparingDouble(PackedScoredCandidate::value).reversed());

            int keep = Math.min(BEAM_WIDTH, ordered.size());
            beamSize = keep;
            for (int j = 0; j < keep; j++) {
                beamStates[j] = ordered.get(j).state();
            }
        }

        // pick highest-value candidate that hit the target count
        long bestState = Long.MIN_VALUE;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < beamSize; i++) {
            long state = beamStates[i];
            if (getPackedCivilianCount(state) != targetCivilianSlots) continue;

            applyPackedState(working, state);
            if (!isValidCandidate(working)) continue;
            double value = score(working);
            if (!Double.isFinite(value)) continue;
            if (value > bestValue) {
                bestValue = value;
                bestState = state;
            }
        }
        if (bestState == Long.MIN_VALUE) {
            return null;
        }
        SimpleNationCity best = new SimpleNationCity(start, getRevenue, convertedFunc);
        applyPackedState(best, bestState);
        return best;
    }

    // --- validation ---

    private boolean isValidCandidate(ICity candidate) {
        return isStructurallyFeasible(candidate);
    }

    private double score(INationCity city) {
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
            int bits = civilianBits[bi];
            if (bits == 0) {
                continue;
            }
            int value = city.getBuilding(CIVILIAN_BUILDINGS[bi]);
            state = setPackedCount(state, bi, value);
        }
        return state;
    }

    private int getPackedCount(long state, int bi) {
        int bits = civilianBits[bi];
        if (bits == 0) {
            return 0;
        }
        return (int) ((state & civilianMask[bi]) >>> civilianShift[bi]);
    }

    private long setPackedCount(long state, int bi, int newAmt) {
        int bits = civilianBits[bi];
        if (bits == 0) {
            if (newAmt != 0) {
                throw new IllegalArgumentException("Cannot set unbuildable civilian index " + bi + " to " + newAmt);
            }
            return state;
        }
        long mask = civilianMask[bi];
        long shifted = ((long) newAmt << civilianShift[bi]) & mask;
        return (state & ~mask) | shifted;
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

    private static int getCivilianCount(ICity city) {
        int total = 0;
        for (Building building : CIVILIAN_BUILDINGS) {
            total += city.getBuilding(building);
        }
        return total;
    }

    private record PackedScoredCandidate(long state, double value) {
    }

    private record EvaluatedCandidate(SimpleNationCity candidate, double value, int donorId) {
    }
}

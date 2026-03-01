package link.locutus.discord.apiv1.enums.city;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        // precompute per-building caps once
        this.civilianCaps = new int[CIVILIAN_BUILDINGS.length];
        for (int i = 0; i < CIVILIAN_BUILDINGS.length; i++) {
            Building b = CIVILIAN_BUILDINGS[i];
            civilianCaps[i] = b.canBuild(continent) ? b.getCap(hasProject) : -1;
        }
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
                valueFunction, goal, getRevenue, convertedFunc, origin, sig);
    }

    // --- unified search loop ---

    private INationCity search(Iterable<DBCity> donors, boolean useBeamSearch) {
        double bestValue = Double.NEGATIVE_INFINITY;
        INationCity best = null;
        Set<String> seenSignatures = useBeamSearch ? new HashSet<>() : null;

        for (DBCity donor : donors) {
            SimpleNationCity normalized = normalize(donor);

            if (useBeamSearch) {
                SimpleNationCity repaired = repairBeamBaseline(normalized);
                if (repaired == null) continue;

                String civSig = civilianSignature(repaired);
                if (!seenSignatures.add(civSig)) continue;
                SimpleNationCity fitted = fitCivilianSlots(repaired);
                if (fitted == null) continue;
                if (!isValidCandidate(fitted)) continue;
                if (goal != null && !goal.test(fitted)) continue;
                double value = valueFunction.applyAsDouble(fitted);
                if (!Double.isFinite(value)) continue;
                if (value > bestValue || (value == bestValue && donor.getId() < getCityId(best))) {
                    bestValue = value;
                    best = fitted;
                }
            } else {
                if (getCivilianCount(normalized) != targetCivilianSlots) continue;
                if (!isValidCandidate(normalized)) continue;
                if (goal != null && !goal.test(normalized)) continue;
                double value = valueFunction.applyAsDouble(normalized);
                if (!Double.isFinite(value)) continue;
                if (value > bestValue || (value == bestValue && donor.getId() < getCityId(best))) {
                    bestValue = value;
                    best = normalized;
                }
            }
        }
        return best;
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

        int overflow = civilianCount - targetCivilianSlots;
        while (overflow > 0) {
            if (!dropLeastHarmfulCivilian(repaired)) {
                return null;
            }
            overflow--;
        }

        return repaired;
    }

    private boolean dropLeastHarmfulCivilian(SimpleNationCity candidate) {
        int bestIndex = -1;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
            Building building = CIVILIAN_BUILDINGS[bi];
            int current = candidate.getBuilding(building);
            if (current <= 0) {
                continue;
            }

            SimpleNationCity probe = new SimpleNationCity(candidate, getRevenue, convertedFunc);
            probe.setBuilding(building, current - 1);
            if (!isStructurallyFeasible(probe)) {
                continue;
            }

            double value = valueFunction.applyAsDouble(probe);
            if (!Double.isFinite(value)) {
                continue;
            }

            if (value > bestValue) {
                bestValue = value;
                bestIndex = bi;
            }
        }

        if (bestIndex < 0) {
            return false;
        }

        Building selected = CIVILIAN_BUILDINGS[bestIndex];
        candidate.setBuilding(selected, candidate.getBuilding(selected) - 1);
        return true;
    }

    private boolean isStructurallyFeasible(ICity candidate) {
        if (!candidate.canBuild(continent, hasProject, false)) return false;
        if (candidate.getRequiredInfra() > targetInfra) return false;
        return candidate.getPoweredInfra() >= targetInfra;
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
        List<SimpleNationCity> beam = new ArrayList<>();
        beam.add(start);

        for (int i = 0; i < steps; i++) {
            Map<String, ScoredCandidate> deduped = new HashMap<>();
            for (SimpleNationCity candidate : beam) {
                for (int bi = 0; bi < CIVILIAN_BUILDINGS.length; bi++) {
                    int cap = civilianCaps[bi];
                    int amt = candidate.getBuilding(CIVILIAN_BUILDINGS[bi]);
                    if (addMode) {
                        if (cap < 0 || amt >= cap) continue;
                    } else {
                        if (amt <= 0) continue;
                    }

                    int newAmt = addMode ? amt + 1 : amt - 1;
                    // check infra requirement before cloning: each building needs 50 infra
                    if (addMode && candidate.getRequiredInfra() + 50 > targetInfra) continue;

                    SimpleNationCity next = new SimpleNationCity(candidate, getRevenue, convertedFunc);
                    next.setBuilding(CIVILIAN_BUILDINGS[bi], newAmt);

                    double value = valueFunction.applyAsDouble(next);
                    if (!Double.isFinite(value)) continue;

                    String key = civilianSignature(next);
                    ScoredCandidate existing = deduped.get(key);
                    if (existing == null || value > existing.value()) {
                        deduped.put(key, new ScoredCandidate(next, value));
                    }
                }
            }

            if (deduped.isEmpty()) return null;

            List<ScoredCandidate> ordered = new ArrayList<>(deduped.values());
            ordered.sort(Comparator.comparingDouble(ScoredCandidate::value).reversed());

            int keep = Math.min(BEAM_WIDTH, ordered.size());
            beam = new ArrayList<>(keep);
            for (int j = 0; j < keep; j++) {
                beam.add(ordered.get(j).candidate());
            }
        }

        // pick highest-value candidate that hit the target count
        SimpleNationCity best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (SimpleNationCity candidate : beam) {
            if (getCivilianCount(candidate) != targetCivilianSlots) continue;
            if (!isValidCandidate(candidate)) continue;
            double value = valueFunction.applyAsDouble(candidate);
            if (!Double.isFinite(value)) continue;
            if (value > bestValue) {
                bestValue = value;
                best = candidate;
            }
        }
        return best;
    }

    // --- validation ---

    private boolean isValidCandidate(ICity candidate) {
        return isStructurallyFeasible(candidate);
    }

    // --- signature helpers ---

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

    private static String civilianSignature(ICity city) {
        StringBuilder sb = new StringBuilder(CIVILIAN_BUILDINGS.length);
        for (Building building : CIVILIAN_BUILDINGS) {
            sb.append((char) city.getBuilding(building));
        }
        return sb.toString();
    }

    private static int getCityId(INationCity city) {
        if (city instanceof DBCity dbCity) {
            return dbCity.getId();
        }
        return Integer.MAX_VALUE;
    }

    private record ScoredCandidate(SimpleNationCity candidate, double value) {
    }
}

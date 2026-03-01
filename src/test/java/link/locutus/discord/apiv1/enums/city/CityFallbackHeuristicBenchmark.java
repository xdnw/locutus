package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class CityFallbackHeuristicBenchmark {
    private static final Predicate<Project> NO_PROJECTS = p -> false;
    private static final Continent CONTINENT = Continent.NORTH_AMERICA;
    private static final int NUM_CITIES = 20;
    private static final double RADS = 3_000d;
    private static final double GROSS_MODIFIER = 0d;

    private static final int DEFAULT_SCENARIOS = 6;
    private static final int DEFAULT_DONORS_PER_SCENARIO = 400;
    private static final int DEFAULT_WARMUP_ROUNDS = 2;
    private static final int DEFAULT_MEASURE_ROUNDS = 5;

    private CityFallbackHeuristicBenchmark() {
    }

    public static void main(String[] args) {
        int scenarioCount = Integer.getInteger("cityFallbackBenchScenarios", DEFAULT_SCENARIOS);
        int donorsPerScenario = Integer.getInteger("cityFallbackBenchDonors", DEFAULT_DONORS_PER_SCENARIO);
        int warmupRounds = Integer.getInteger("cityFallbackBenchWarmups", DEFAULT_WARMUP_ROUNDS);
        int measureRounds = Integer.getInteger("cityFallbackBenchRounds", DEFAULT_MEASURE_ROUNDS);
        long seed = Long.getLong("cityFallbackBenchSeed", 20260302L);

        long scenarioStart = System.nanoTime();
        List<Scenario> scenarios = createScenarios(scenarioCount, donorsPerScenario, seed);
        long scenarioNanos = System.nanoTime() - scenarioStart;
        ToDoubleFunction<INationCity> valueFunction = CityFallbackHeuristicBenchmark::scoreCity;

        System.out.printf(Locale.ROOT,
            "prepared scenarios in %.3f ms (scenarios=%d, donors=%d)%n",
            nanosToMillis(scenarioNanos),
            scenarioCount,
            donorsPerScenario);

        runRounds("warmup", scenarios, valueFunction, warmupRounds);
        Metrics measured = runRounds("measure", scenarios, valueFunction, measureRounds);

        double oldMs = nanosToMillis(measured.oldTotalNanos);
        double currentMs = nanosToMillis(measured.currentTotalNanos);
        double speedup = currentMs > 0 ? oldMs / currentMs : Double.POSITIVE_INFINITY;

        System.out.println("=== CityFallbackHeuristic benchmark ===");
        System.out.println("scenarios=" + scenarioCount
                + ", donorsPerScenario=" + donorsPerScenario
                + ", warmups=" + warmupRounds
                + ", rounds=" + measureRounds
                + ", seed=" + seed);
        System.out.printf(Locale.ROOT, "oldTotalMs=%.3f, currentTotalMs=%.3f, speedup=%.3fx%n", oldMs, currentMs, speedup);
        System.out.println("qualityRegressions=" + measured.qualityRegressions + "/" + measured.comparisons);
        if (measured.qualityRegressions > 0) {
            System.out.printf(Locale.ROOT, "maxQualityDrop=%.6f%n", measured.maxQualityDrop);
        }

        boolean forceExit = Boolean.parseBoolean(System.getProperty("cityFallbackBenchForceExit", "true"));
        if (forceExit) {
            System.exit(0);
        }
    }

    private static Metrics runRounds(String phase,
                                     List<Scenario> scenarios,
                                     ToDoubleFunction<INationCity> valueFunction,
                                     int rounds) {
        Metrics total = new Metrics();
        if (rounds <= 0) {
            return total;
        }
        for (int round = 0; round < rounds; round++) {
            Metrics roundMetrics = runSingleRound(scenarios, valueFunction);
            total.add(roundMetrics);

            if (!"warmup".equals(phase)) {
                System.out.printf(Locale.ROOT,
                        "%s round %d/%d: oldMs=%.3f, currentMs=%.3f, regressions=%d/%d%n",
                        phase,
                        round + 1,
                        rounds,
                        nanosToMillis(roundMetrics.oldTotalNanos),
                        nanosToMillis(roundMetrics.currentTotalNanos),
                        roundMetrics.qualityRegressions,
                        roundMetrics.comparisons);
            }
        }
        return total;
    }

    private static Metrics runSingleRound(List<Scenario> scenarios,
                                          ToDoubleFunction<INationCity> valueFunction) {
        Metrics metrics = new Metrics();
        for (Scenario scenario : scenarios) {
            long oldStart = System.nanoTime();
            INationCity oldBest = CityFallbackHeuristicOld.findBest(
                    scenario.source,
                    CONTINENT,
                    NUM_CITIES,
                    valueFunction,
                    null,
                    NO_PROJECTS,
                    RADS,
                    GROSS_MODIFIER,
                    null,
                    scenario.donors);
            metrics.oldTotalNanos += System.nanoTime() - oldStart;

            long currentStart = System.nanoTime();
            INationCity currentBest = CityFallbackHeuristic.findBest(
                    scenario.source,
                    CONTINENT,
                    NUM_CITIES,
                    valueFunction,
                    null,
                    NO_PROJECTS,
                    RADS,
                    GROSS_MODIFIER,
                    null,
                    scenario.donors);
            metrics.currentTotalNanos += System.nanoTime() - currentStart;

            double oldValue = oldBest == null ? Double.NEGATIVE_INFINITY : valueFunction.applyAsDouble(oldBest);
            double currentValue = currentBest == null ? Double.NEGATIVE_INFINITY : valueFunction.applyAsDouble(currentBest);

            metrics.comparisons++;
            if (currentValue + 1e-9 < oldValue) {
                metrics.qualityRegressions++;
                metrics.maxQualityDrop = Math.max(metrics.maxQualityDrop, oldValue - currentValue);
            }
        }
        return metrics;
    }

    private static List<Scenario> createScenarios(int scenarioCount, int donorsPerScenario, long seed) {
        Random random = new Random(seed);
        ArrayList<Scenario> scenarios = new ArrayList<>(scenarioCount);
        double[] infraOptions = {500d, 750d, 1_000d, 1_500d, 2_000d, 2_500d};

        for (int i = 0; i < scenarioCount; i++) {
            double infra = infraOptions[i % infraOptions.length];
            JavaCity source = new JavaCity()
                    .setInfra(infra)
                    .setLand(1_500d + (i * 120d))
                    .setAge(600 + (i * 100));
            source.setOptimalPower(CONTINENT);

            ArrayList<DBCity> donors = new ArrayList<>(donorsPerScenario);
            for (int d = 0; d < donorsPerScenario; d++) {
                JavaCity donor = new JavaCity()
                        .setInfra(infra)
                        .setLand(source.getLand())
                        .setAge(source.getAgeDays());
                donor.setOptimalPower(CONTINENT);
                fillCivilianBuildings(donor, random);
                donors.add(new SimpleDBCity(donor));
            }

            scenarios.add(new Scenario(source, donors));
        }
        return scenarios;
    }

    private static void fillCivilianBuildings(JavaCity donor, Random random) {
        for (Building building : Buildings.values()) {
            if (building.getType() == BuildingType.MILITARY || building.getType() == BuildingType.POWER) {
                continue;
            }
            int cap = building.cap(NO_PROJECTS);
            int amount = random.nextInt(cap + 1);
            if (random.nextDouble() < 0.08d) {
                amount = cap + 1 + random.nextInt(3);
            }
            donor.setBuilding(building, amount);
        }
    }

    private static double scoreCity(INationCity city) {
        return city.getRevenueConverted()
                + (city.getBuilding(Buildings.STADIUM) * 1_250d)
                + (city.getBuilding(Buildings.MALL) * 650d)
                + (city.getBuilding(Buildings.BANK) * 500d)
                + (city.getBuilding(Buildings.SUPERMARKET) * 300d)
                + (city.getBuilding(Buildings.HOSPITAL) * 120d)
                + (city.getBuilding(Buildings.POLICE_STATION) * 120d);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000d;
    }

    private record Scenario(JavaCity source, List<DBCity> donors) {
    }

    private static final class Metrics {
        long oldTotalNanos;
        long currentTotalNanos;
        int qualityRegressions;
        int comparisons;
        double maxQualityDrop;

        void add(Metrics other) {
            this.oldTotalNanos += other.oldTotalNanos;
            this.currentTotalNanos += other.currentTotalNanos;
            this.qualityRegressions += other.qualityRegressions;
            this.comparisons += other.comparisons;
            this.maxQualityDrop = Math.max(this.maxQualityDrop, other.maxQualityDrop);
        }
    }
}
package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.util.search.BFSUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class CityBranchBfsBenchmark {
    private static final Predicate<Project> NO_PROJECTS = p -> false;
    private static final Continent CONTINENT = Continent.NORTH_AMERICA;
    private static final int NUM_CITIES = 20;
    private static final double RADS = 3_000d;
    private static final double GROSS_MODIFIER = 1d;

    private static final int DEFAULT_SCENARIOS = 48;
    private static final int DEFAULT_WARMUPS = 2;
    private static final int DEFAULT_ROUNDS = 5;
    private static final long DEFAULT_TIMEOUT_MS = 1500L;
    private static final long DEFAULT_SEED = 20260302L;
    private static final int DEFAULT_QUALITY_SCENARIOS = 8;
    private static final int DEFAULT_QUALITY_DONORS = 1500;

    private CityBranchBfsBenchmark() {
    }

    public static void main(String[] args) {
        int scenarioCount = Integer.getInteger("cityBranchBenchScenarios", DEFAULT_SCENARIOS);
        int warmups = Integer.getInteger("cityBranchBenchWarmups", DEFAULT_WARMUPS);
        int rounds = Integer.getInteger("cityBranchBenchRounds", DEFAULT_ROUNDS);
        long timeoutMs = Long.getLong("cityBranchBenchTimeoutMs", DEFAULT_TIMEOUT_MS);
        long seed = Long.getLong("cityBranchBenchSeed", DEFAULT_SEED);
        int qualityScenarios = Integer.getInteger("cityBranchBenchQualityScenarios", DEFAULT_QUALITY_SCENARIOS);
        int qualityDonors = Integer.getInteger("cityBranchBenchQualityDonors", DEFAULT_QUALITY_DONORS);

        long scenarioStart = System.nanoTime();
        List<Scenario> scenarios = createScenarios(scenarioCount, seed);
        long scenarioNanos = System.nanoTime() - scenarioStart;

        ToDoubleFunction<INationCity> objective = CityBranchBfsBenchmark::scoreCity;

        System.out.printf(Locale.ROOT,
                "prepared CityBranch scenarios in %.3f ms (scenarios=%d, timeoutMs=%d)%n",
                nanosToMillis(scenarioNanos),
                scenarioCount,
                timeoutMs);

        runRounds("warmup", scenarios, objective, warmups, timeoutMs);
        Metrics measured = runRounds("measure", scenarios, objective, rounds, timeoutMs);

        double totalMs = nanosToMillis(measured.totalNanos);
        double avgPerRoundMs = rounds > 0 ? totalMs / rounds : 0d;
        double avgPerScenarioMs = measured.totalRuns > 0 ? totalMs / measured.totalRuns : 0d;
        double scenariosPerSecond = measured.totalRuns > 0 && totalMs > 0d ? (measured.totalRuns * 1000d) / totalMs : 0d;
        double nodesPerSecond = measured.expandedNodes > 0 && totalMs > 0d ? (measured.expandedNodes * 1000d) / totalMs : 0d;

        QualityMetrics quality = runQualityCheck(scenarios, objective, timeoutMs, qualityScenarios, qualityDonors, seed);

        System.out.println("=== CityBranch/BFS benchmark ===");
        System.out.println("scenarios=" + scenarioCount
                + ", warmups=" + warmups
                + ", rounds=" + rounds
                + ", timeoutMs=" + timeoutMs
                + ", seed=" + seed);
        System.out.printf(Locale.ROOT,
            "totalMs=%.3f, avgRoundMs=%.3f, avgScenarioMs=%.3f, throughput=%.3f scenarios/s, nodesPerSec=%.3f%n",
                totalMs,
                avgPerRoundMs,
                avgPerScenarioMs,
            scenariosPerSecond,
            nodesPerSecond);
        System.out.printf(Locale.ROOT,
            "resultChecksum=%d, objectiveSum=%.6f, expandedNodes=%d, nullResults=%d/%d%n",
                measured.checksum,
                measured.objectiveSum,
            measured.expandedNodes,
                measured.nullResults,
                measured.totalRuns);
        System.out.printf(Locale.ROOT,
            "qualityComparisons=%d, qualityRegressions=%d, maxQualityDrop=%.6f (fallback donors=%d)%n",
            quality.comparisons,
            quality.regressions,
            quality.maxDrop,
            qualityDonors);

        if (Boolean.parseBoolean(System.getProperty("cityBranchBenchForceExit", "true"))) {
            System.exit(0);
        }
    }

    private static Metrics runRounds(String phase,
                                     List<Scenario> scenarios,
                                     ToDoubleFunction<INationCity> objective,
                                     int rounds,
                                     long timeoutMs) {
        Metrics total = new Metrics();
        if (rounds <= 0) {
            return total;
        }

        for (int round = 0; round < rounds; round++) {
            Metrics roundMetrics = runSingleRound(scenarios, objective, timeoutMs);
            total.add(roundMetrics);
            if (!"warmup".equals(phase)) {
                System.out.printf(Locale.ROOT,
                        "%s round %d/%d: roundMs=%.3f, avgScenarioMs=%.3f, nodes=%d, null=%d/%d%n",
                        phase,
                        round + 1,
                        rounds,
                        nanosToMillis(roundMetrics.totalNanos),
                        roundMetrics.totalRuns == 0 ? 0d : nanosToMillis(roundMetrics.totalNanos) / roundMetrics.totalRuns,
                        roundMetrics.expandedNodes,
                        roundMetrics.nullResults,
                        roundMetrics.totalRuns);
            }
        }
        return total;
    }

    private static Metrics runSingleRound(List<Scenario> scenarios,
                                          ToDoubleFunction<INationCity> objective,
                                          long timeoutMs) {
        Metrics metrics = new Metrics();
        BFSUtil.consumeExpandedNodeCount();
        for (Scenario scenario : scenarios) {
            JavaCity source = new JavaCity(scenario.source);
            long start = System.nanoTime();
            JavaCity optimized = source.optimalBuild(
                    CONTINENT,
                    NUM_CITIES,
                    objective,
                    null,
                    NO_PROJECTS,
                    timeoutMs,
                    RADS,
                    false,
                    false,
                    GROSS_MODIFIER,
                    null);
            metrics.totalNanos += (System.nanoTime() - start);
            metrics.totalRuns++;

            if (optimized == null) {
                metrics.nullResults++;
                continue;
            }

            metrics.objectiveSum += scoreJavaCity(optimized);
            metrics.checksum = mixChecksum(metrics.checksum, optimized);
        }
        metrics.expandedNodes = BFSUtil.consumeExpandedNodeCount();
        return metrics;
    }

    private static QualityMetrics runQualityCheck(List<Scenario> scenarios,
                                                  ToDoubleFunction<INationCity> objective,
                                                  long timeoutMs,
                                                  int qualityScenarios,
                                                  int qualityDonors,
                                                  long seed) {
        int comparisons = 0;
        int regressions = 0;
        double maxDrop = 0d;

        int limit = Math.max(0, Math.min(qualityScenarios, scenarios.size()));
        if (limit == 0 || qualityDonors <= 0) {
            return new QualityMetrics(comparisons, regressions, maxDrop);
        }

        Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (int i = 0; i < limit; i++) {
            Scenario scenario = scenarios.get(i);
            JavaCity source = new JavaCity(scenario.source);
            JavaCity optimized = source.optimalBuild(
                    CONTINENT,
                    NUM_CITIES,
                    objective,
                    null,
                    NO_PROJECTS,
                    timeoutMs,
                    RADS,
                    false,
                    false,
                    GROSS_MODIFIER,
                    null);

            ArrayList<DBCity> donors = createDonorsForScenario(scenario.source, qualityDonors, random);
            INationCity fallbackBest = source.findBestFromDonors(
                    CONTINENT,
                    NUM_CITIES,
                    objective,
                    null,
                    NO_PROJECTS,
                    RADS,
                    GROSS_MODIFIER,
                    null,
                    donors);

            double optimizedScore = optimized == null ? Double.NEGATIVE_INFINITY : scoreJavaCity(optimized);
            double fallbackScore = fallbackBest == null ? Double.NEGATIVE_INFINITY : objective.applyAsDouble(fallbackBest);
            comparisons++;
            if (optimizedScore + 1e-9 < fallbackScore) {
                regressions++;
                maxDrop = Math.max(maxDrop, fallbackScore - optimizedScore);
            }
        }

        return new QualityMetrics(comparisons, regressions, maxDrop);
    }

    private static ArrayList<DBCity> createDonorsForScenario(JavaCity source, int donorCount, Random random) {
        ArrayList<DBCity> donors = new ArrayList<>(donorCount);
        for (int i = 0; i < donorCount; i++) {
            JavaCity donor = new JavaCity()
                    .setInfra(source.getInfra())
                    .setLand(source.getLand())
                    .setAge(source.getAgeDays());
            donor.setOptimalPower(CONTINENT);
            fillCivilianBuildings(donor, random);
            donors.add(new SimpleDBCity(donor));
        }
        return donors;
    }

    private static List<Scenario> createScenarios(int scenarioCount, long seed) {
        Random random = new Random(seed);
        double[] infraOptions = {500d, 750d, 1000d, 1250d, 1500d, 2000d, 2500d};
        List<Scenario> scenarios = new ArrayList<>(scenarioCount);

        for (int i = 0; i < scenarioCount; i++) {
            double infra = infraOptions[i % infraOptions.length];
            JavaCity source = new JavaCity()
                    .setInfra(infra)
                    .setLand(1400d + (i * 35d))
                    .setAge(450 + i * 15);
            source.setOptimalPower(CONTINENT);
            fillCivilianBuildings(source, random);
            scenarios.add(new Scenario(source));
        }
        return scenarios;
    }

    private static void fillCivilianBuildings(JavaCity city, Random random) {
        for (Building building : Buildings.values()) {
            BuildingType type = building.getType();
            if (type == BuildingType.POWER || type == BuildingType.MILITARY) {
                continue;
            }
            int cap = building.cap(NO_PROJECTS);
            int amount = cap == 0 ? 0 : random.nextInt(cap + 1);
            city.setBuilding(building, amount);
        }
    }

    private static long mixChecksum(long checksum, JavaCity city) {
        long mixed = checksum * 1_000_003L
                + Math.round(city.profitConvertedCached(CONTINENT, RADS, NO_PROJECTS, NUM_CITIES, GROSS_MODIFIER) * 1000d);
        for (Building building : Buildings.values()) {
            mixed = mixed * 257L + city.getBuilding(building);
        }
        return mixed;
    }

    private static double scoreCity(INationCity city) {
        return city.getRevenueConverted();
    }

    private static double scoreJavaCity(JavaCity city) {
        return city.profitConvertedCached(CONTINENT, RADS, NO_PROJECTS, NUM_CITIES, GROSS_MODIFIER);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000d;
    }

    private record Scenario(JavaCity source) {
    }

    private static final class Metrics {
        long totalNanos;
        int totalRuns;
        int nullResults;
        double objectiveSum;
        long checksum;
        long expandedNodes;

        void add(Metrics other) {
            this.totalNanos += other.totalNanos;
            this.totalRuns += other.totalRuns;
            this.nullResults += other.nullResults;
            this.objectiveSum += other.objectiveSum;
            this.checksum = this.checksum * 31L + other.checksum;
            this.expandedNodes += other.expandedNodes;
        }
    }

    private record QualityMetrics(int comparisons, int regressions, double maxDrop) {
    }
}
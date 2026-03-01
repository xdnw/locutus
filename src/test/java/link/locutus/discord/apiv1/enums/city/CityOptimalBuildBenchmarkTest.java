package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.info.optimal.CityBranch;
import link.locutus.discord.db.entities.CityNode;
import link.locutus.discord.util.search.BFSUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class CityOptimalBuildBenchmarkTest {

    private static final Predicate<Project> NO_PROJECTS = project -> false;
    private static final Predicate<INationCity> BENCHMARK_GOAL = city -> city.getFreeSlots() <= 0
            || (city instanceof CityNode node && node.getIndex() >= node.getCached().getMaxIndex() - 1);
    private static final Continent CONTINENT = Continent.NORTH_AMERICA;
    private static final ToDoubleFunction<INationCity> VALUE_FUNCTION = INationCity::getRevenueConverted;
    private static final long DEFAULT_TIMEOUT_MS = 1_500L;
    private static final double EPSILON = 1e-6;

    private static final String SCENARIOS_PROPERTY = "locutus.city.benchmark.scenarios";
    private static final String SCENARIOS_ENV = "LOCUTUS_CITY_BENCHMARK_SCENARIOS";
    private static final String TIMEOUT_PROPERTY = "locutus.city.benchmark.timeoutMs";
    private static final String TIMEOUT_ENV = "LOCUTUS_CITY_BENCHMARK_TIMEOUT_MS";
    private static final String ORACLE_PROPERTY = "locutus.city.benchmark.oracle";
    private static final String ORACLE_ENV = "LOCUTUS_CITY_BENCHMARK_ORACLE";
    private static final String ENFORCE_BASELINE_PROPERTY = "locutus.city.benchmark.enforceBaseline";
    private static final String ENFORCE_BASELINE_ENV = "LOCUTUS_CITY_BENCHMARK_ENFORCE_BASELINE";
    private static final String SUITE_PROPERTY = "locutus.city.benchmark.suite";
    private static final String SUITE_ENV = "LOCUTUS_CITY_BENCHMARK_SUITE";
    private static final String ARTIFACT_PATH_PROPERTY = "locutus.city.benchmark.artifactPath";
    private static final String ARTIFACT_PATH_ENV = "LOCUTUS_CITY_BENCHMARK_ARTIFACT_PATH";
    private static final String BASELINE_ARTIFACT_PROPERTY = "locutus.city.benchmark.baselineArtifact";
    private static final String BASELINE_ARTIFACT_ENV = "LOCUTUS_CITY_BENCHMARK_BASELINE_ARTIFACT";
    private static final String ENFORCE_GATES_PROPERTY = "locutus.city.benchmark.enforceGates";
    private static final String ENFORCE_GATES_ENV = "LOCUTUS_CITY_BENCHMARK_ENFORCE_GATES";
    private static final String QUICK_THROUGHPUT_GAIN_PROPERTY = "locutus.city.benchmark.quickThroughputMinGain";
    private static final String QUICK_THROUGHPUT_GAIN_ENV = "LOCUTUS_CITY_BENCHMARK_QUICK_THROUGHPUT_MIN_GAIN";
    private static final String FULL_THROUGHPUT_GAIN_PROPERTY = "locutus.city.benchmark.fullThroughputMinGain";
    private static final String FULL_THROUGHPUT_GAIN_ENV = "LOCUTUS_CITY_BENCHMARK_FULL_THROUGHPUT_MIN_GAIN";
    private static final String STABILITY_MAX_REGRESSION_PROPERTY = "locutus.city.benchmark.stabilityMaxRegression";
    private static final String STABILITY_MAX_REGRESSION_ENV = "LOCUTUS_CITY_BENCHMARK_STABILITY_MAX_REGRESSION";
    private static final String PARITY_TOLERANCE_PROPERTY = "locutus.city.benchmark.parityTolerance";
    private static final String PARITY_TOLERANCE_ENV = "LOCUTUS_CITY_BENCHMARK_PARITY_TOLERANCE";
    private static final String STABILITY_WAIVERS_PROPERTY = "locutus.city.benchmark.stabilityWaivers";
    private static final String STABILITY_WAIVERS_ENV = "LOCUTUS_CITY_BENCHMARK_STABILITY_WAIVERS";
    private static final boolean ENFORCE_BASELINE = readFlag(ENFORCE_BASELINE_PROPERTY, ENFORCE_BASELINE_ENV, false);
    private static final List<String> ALL_SCENARIOS = List.of("balanced", "commerce", "manufacturing");

    private static final Map<String, BaselineSnapshot> BASELINE_SNAPSHOTS = Map.of(
            "balanced", new BaselineSnapshot(1_095_991.1535841422d, "impNuclearpower=2|impCoalmine=10|impIronmine=10|impAluminumrefinery=4|impSubway=1|impMall=4|impStadium=2|impBank=4|impSupermarket=4|impPolicestation=2|impHospital=5|impRecyclingcenter=1|impBarracks=5|impFactory=3|impHangars=2|impDrydock=1"),
            "commerce", new BaselineSnapshot(1_098_540.7985841422d, "impNuclearpower=2|impCoalmine=10|impIronmine=10|impAluminumrefinery=5|impSubway=1|impMall=4|impStadium=2|impBank=4|impSupermarket=4|impPolicestation=2|impHospital=5|impRecyclingcenter=1|impBarracks=4|impFactory=2|impHangars=1|impDrydock=1"),
                "manufacturing", new BaselineSnapshot(1_098_540.7985841422d, "impNuclearpower=2|impCoalmine=10|impIronmine=10|impAluminumrefinery=5|impSubway=1|impMall=4|impStadium=2|impBank=4|impSupermarket=4|impPolicestation=2|impHospital=5|impRecyclingcenter=1|impBarracks=4|impFactory=3|impHangars=1")
    );

    @Test
    void sixtySlotScenariosMatchBaselineSnapshotsAndReportBenchmarks() {
        System.setProperty("locutus.bfs.profile", "false");
        BenchmarkConfig config = BenchmarkConfig.read();
        runScenarioBenchmarks(config);
    }

    @Test
    void reducedInstanceOracleMatchesOptimizedSolver() {
        BenchmarkConfig config = BenchmarkConfig.read();
        if (!config.oracleEnabled) {
            System.out.printf(Locale.ROOT,
                    "benchmark oracle-skipped oracleEnabled=%s%n",
                    config.oracleEnabled);
            return;
        }

        assertReducedOracleEquality(reducedScenario(350, 1_400, city -> {
            city.setBuilding(Buildings.FARM, 1);
            city.setBuilding(Buildings.SUBWAY, 1);
            city.setBuilding(Buildings.BARRACKS, 1);
            city.setOptimalPower(CONTINENT);
        }), false, config.timeoutMs);

        assertReducedOracleEquality(reducedScenario(450, 1_500, city -> {
            city.setBuilding(Buildings.COAL_MINE, 2);
            city.setBuilding(Buildings.IRON_MINE, 1);
            city.setBuilding(Buildings.STEEL_MILL, 1);
            city.setBuilding(Buildings.HOSPITAL, 1);
            city.setBuilding(Buildings.BARRACKS, 1);
            city.setOptimalPower(CONTINENT);
        }), true, config.timeoutMs);
    }

    void runConfiguredHarness() {
        BenchmarkConfig config = BenchmarkConfig.read();
        System.setProperty("locutus.bfs.profile", "false");
        runScenarioBenchmarks(config);
        if (config.oracleEnabled) {
            reducedInstanceOracleMatchesOptimizedSolver();
        }
    }

    @Test
    void deterministicRepeatabilityForSameInputs() {
        BenchmarkConfig config = BenchmarkConfig.read();
        if (config.scenarios.isEmpty()) {
            fail("No benchmark scenarios configured");
        }

        String scenarioName = config.scenarios.get(0);
        ScenarioBuilder builder = scenarioBuilder(scenarioName);

        runScenarioOnce(scenarioName, builder, config.timeoutMs, false);

        ScenarioResult first = runScenarioOnce(scenarioName, builder, config.timeoutMs, config.enforceBaseline);
        ScenarioResult second = runScenarioOnce(scenarioName, builder, config.timeoutMs, config.enforceBaseline);

        assertEquals(first.signature, second.signature, "Scenario signature should be deterministic");
        assertEquals(first.bestScore, second.bestScore, config.parityTolerance, "Scenario bestScore should be deterministic");
        assertEquals(first.metrics.lastStage, second.metrics.lastStage, "Scenario stage should be deterministic");
    }

    private void runScenarioBenchmarks(BenchmarkConfig config) {
        List<ScenarioResult> results = new ArrayList<>();
        for (String name : config.scenarios) {
            ScenarioBuilder builder = scenarioBuilder(name);
            results.add(runScenarioOnce(name, builder, config.timeoutMs, config.enforceBaseline));
        }

        persistArtifact(config, results);
        validateAgainstBaselineGates(config, results);

        if (!results.isEmpty()) {
            double convergenceAucMedian = median(results.stream().mapToDouble(ScenarioResult::convergenceAuc).toArray());
            double thresholdRatioMedian = median(results.stream().mapToDouble(ScenarioResult::timeToThresholdRatio).toArray());
            System.out.printf(Locale.ROOT,
                    "benchmark-summary suite=%s scenarios=%d medianConvergenceAuc=%.6f medianTimeToThresholdRatio=%.6f%n",
                    config.suite,
                    results.size(),
                    convergenceAucMedian,
                    thresholdRatioMedian);
        }
    }

    private ScenarioResult runScenarioOnce(
            String name,
            ScenarioBuilder builder,
            long timeoutMs,
            boolean enforceBaseline
    ) {
        ResourceType.resetCachedMarketPricesToFallback();
        JavaCity origin = builder.build();
        JavaCity optimized = origin.optimalBuild(
                CONTINENT,
                20,
                VALUE_FUNCTION,
                BENCHMARK_GOAL,
                NO_PROJECTS,
                timeoutMs,
                0,
                false,
                false,
                1,
                null
        );

        assertNotNull(optimized, "Expected optimized city for scenario " + name);

        BFSUtil.SearchMetrics metrics = JavaCity.getLastOptimizationMetrics();
        assertNotNull(metrics, "Expected search metrics for scenario " + name);
        assertTrue(metrics.elapsedMs >= 0, "elapsedMs should be non-negative");
        assertTrue(metrics.expandedNodes > 0, "expanded nodes should be > 0");
        assertTrue(metrics.enqueuedNodes > 0, "enqueued nodes should be > 0");
        assertTrue(metrics.nodesPerSecond >= 0, "nodesPerSecond should be non-negative");

        double bestScore = metrics.bestScore;
        String signature = citySignature(optimized);

        double auc = convergenceAuc(metrics, timeoutMs, bestScore);
        double thresholdRatio = timeToThresholdRatio(metrics, timeoutMs, bestScore, 0.99d);

        System.out.printf(
                Locale.ROOT,
                "benchmark scenario=%s elapsedMs=%d expanded=%d enqueued=%d prunedByBound=%d nodesPerSecond=%.2f bestScore=%.6f reprioritizations=%d reprioritizeMs=%.3f stage=%s checkpoint25=%.6f checkpoint50=%.6f checkpoint75=%.6f checkpoint100=%.6f auc=%.6f thresholdRatio=%.6f%n",
                name,
                metrics.elapsedMs,
                metrics.expandedNodes,
                metrics.enqueuedNodes,
                metrics.prunedByBound,
                metrics.nodesPerSecond,
                bestScore,
                metrics.queueReprioritizations,
                metrics.reprioritizeTimeNs / 1_000_000d,
                metrics.lastStage,
                sanitizeCheckpoint(metrics.checkpoint25Score, bestScore),
                sanitizeCheckpoint(metrics.checkpoint50Score, bestScore),
                sanitizeCheckpoint(metrics.checkpoint75Score, bestScore),
                sanitizeCheckpoint(metrics.checkpoint100Score, bestScore),
                auc,
                thresholdRatio
        );

        BaselineSnapshot baseline = BASELINE_SNAPSHOTS.get(name);
        if (enforceBaseline) {
            if (baseline == null || Double.isNaN(baseline.bestScore)) {
                fail("Populate baseline snapshot for scenario '" + name + "' with bestScore/signature from benchmark output");
            }

            assertEquals(baseline.bestScore, bestScore, EPSILON, "Score regression for scenario " + name);
            assertEquals(baseline.signature, signature, "Build signature regression for scenario " + name);
        }

        return new ScenarioResult(name, signature, bestScore, metrics, auc, thresholdRatio);
    }

    private static double sanitizeCheckpoint(double checkpointScore, double fallback) {
        return checkpointScore == Double.NEGATIVE_INFINITY ? fallback : checkpointScore;
    }

    private static double convergenceAuc(BFSUtil.SearchMetrics metrics, long timeoutMs, double bestScore) {
        if (timeoutMs <= 0 || bestScore == Double.NEGATIVE_INFINITY) {
            return 0d;
        }

        double c25 = sanitizeCheckpoint(metrics.checkpoint25Score, bestScore);
        double c50 = sanitizeCheckpoint(metrics.checkpoint50Score, bestScore);
        double c75 = sanitizeCheckpoint(metrics.checkpoint75Score, bestScore);
        double c100 = sanitizeCheckpoint(metrics.checkpoint100Score, bestScore);

        double baseline = Math.min(c25, Math.min(c50, Math.min(c75, c100)));
        double denom = Math.max(EPSILON, Math.abs(bestScore - baseline));
        double n25 = (c25 - baseline) / denom;
        double n50 = (c50 - baseline) / denom;
        double n75 = (c75 - baseline) / denom;
        double n100 = (c100 - baseline) / denom;

        return (n25 + n50 + n75 + n100) / 4d;
    }

    private static double timeToThresholdRatio(BFSUtil.SearchMetrics metrics, long timeoutMs, double bestScore, double thresholdRatio) {
        if (timeoutMs <= 0 || bestScore == Double.NEGATIVE_INFINITY) {
            return 1d;
        }
        double threshold = bestScore * thresholdRatio;
        if (sanitizeCheckpoint(metrics.checkpoint25Score, bestScore) >= threshold) {
            return 0.25d;
        }
        if (sanitizeCheckpoint(metrics.checkpoint50Score, bestScore) >= threshold) {
            return 0.50d;
        }
        if (sanitizeCheckpoint(metrics.checkpoint75Score, bestScore) >= threshold) {
            return 0.75d;
        }
        return 1d;
    }

    private void persistArtifact(BenchmarkConfig config, List<ScenarioResult> results) {
        if (results.isEmpty()) {
            return;
        }

        Path artifactPath = Paths.get(config.artifactPath);
        try {
            Path parent = artifactPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StringBuilder csv = new StringBuilder();
            csv.append("suite,scenario,elapsedMs,nodesPerSecond,expandedNodes,enqueuedNodes,prunedByBound,queueReprioritizations,reprioritizeTimeNs,enqueueTimeNs,branchTimeNs,bestScore,lastStage,checkpoint25,checkpoint50,checkpoint75,checkpoint100,convergenceAuc,timeToThresholdRatio,reprioritizeMode,schedulerMode,signature\n");
            for (ScenarioResult result : results) {
                BFSUtil.SearchMetrics metrics = result.metrics;
                csv.append(config.suite).append(',')
                        .append(result.scenario).append(',')
                        .append(metrics.elapsedMs).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", metrics.nodesPerSecond)).append(',')
                        .append(metrics.expandedNodes).append(',')
                        .append(metrics.enqueuedNodes).append(',')
                        .append(metrics.prunedByBound).append(',')
                        .append(metrics.queueReprioritizations).append(',')
                        .append(metrics.reprioritizeTimeNs).append(',')
                        .append(metrics.enqueueTimeNs).append(',')
                        .append(metrics.branchTimeNs).append(',')
                        .append(String.format(Locale.ROOT, "%.12f", result.bestScore)).append(',')
                        .append(metrics.lastStage).append(',')
                        .append(String.format(Locale.ROOT, "%.12f", sanitizeCheckpoint(metrics.checkpoint25Score, result.bestScore))).append(',')
                        .append(String.format(Locale.ROOT, "%.12f", sanitizeCheckpoint(metrics.checkpoint50Score, result.bestScore))).append(',')
                        .append(String.format(Locale.ROOT, "%.12f", sanitizeCheckpoint(metrics.checkpoint75Score, result.bestScore))).append(',')
                        .append(String.format(Locale.ROOT, "%.12f", sanitizeCheckpoint(metrics.checkpoint100Score, result.bestScore))).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", result.convergenceAuc)).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", result.timeToThresholdRatio)).append(',')
                        .append(metrics.reprioritizeMode).append(',')
                        .append(metrics.schedulerMode).append(',')
                        .append('"').append(result.signature.replace("\"", "\"\"")).append('"')
                        .append('\n');
            }

            Files.writeString(artifactPath, csv.toString(), StandardCharsets.UTF_8);
            System.out.printf(Locale.ROOT, "benchmark-artifact path=%s rows=%d%n", artifactPath.toAbsolutePath(), results.size());
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to persist benchmark artifact at " + artifactPath, ioe);
        }
    }

    private void validateAgainstBaselineGates(BenchmarkConfig config, List<ScenarioResult> results) {
        if (!config.enforceGates || results.isEmpty() || config.baselineArtifactPath == null || config.baselineArtifactPath.isBlank()) {
            return;
        }

        Map<String, BaselineMetricsRow> baselineByScenario = parseBaselineArtifact(Paths.get(config.baselineArtifactPath));
        if (baselineByScenario.isEmpty()) {
            fail("No baseline rows found in artifact " + config.baselineArtifactPath);
        }

        List<Double> throughputDeltas = new ArrayList<>();
        for (ScenarioResult result : results) {
            BaselineMetricsRow baseline = baselineByScenario.get(result.scenario);
            assertNotNull(baseline, "Missing baseline scenario in artifact: " + result.scenario);

                assertTrue(result.bestScore + config.parityTolerance >= baseline.bestScore,
                    () -> String.format(Locale.ROOT,
                        "Quality non-regression gate failed for scenario %s expected>=%.12f actual=%.12f tolerance=%.12f",
                        result.scenario,
                        baseline.bestScore,
                        result.bestScore,
                        config.parityTolerance));

            double throughputDelta = (result.metrics.nodesPerSecond - baseline.nodesPerSecond) / Math.max(EPSILON, baseline.nodesPerSecond);
            throughputDeltas.add(throughputDelta);

            boolean waived = config.stabilityWaivers.contains(result.scenario);
            if (!waived) {
                assertTrue(throughputDelta >= -config.stabilityMaxRegression,
                        () -> String.format(Locale.ROOT,
                                "Stability gate failed for scenario=%s throughputDelta=%.4f maxRegression=%.4f",
                                result.scenario,
                                throughputDelta,
                                config.stabilityMaxRegression));
            }
        }

        double medianGain = median(throughputDeltas.stream().mapToDouble(Double::doubleValue).toArray());
        double requiredGain = "quick".equalsIgnoreCase(config.suite)
                ? config.quickThroughputMinGain
                : config.fullThroughputMinGain;

        assertTrue(medianGain >= requiredGain,
                () -> String.format(Locale.ROOT,
                        "Throughput gate failed suite=%s medianGain=%.4f required=%.4f",
                        config.suite,
                        medianGain,
                        requiredGain));
    }

    private static Map<String, BaselineMetricsRow> parseBaselineArtifact(Path artifactPath) {
        Map<String, BaselineMetricsRow> rows = new HashMap<>();
        try {
            if (!Files.exists(artifactPath)) {
                return rows;
            }
            List<String> lines = Files.readAllLines(artifactPath, StandardCharsets.UTF_8);
            if (lines.size() <= 1) {
                return rows;
            }

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                List<String> fields = splitCsvLine(line);
                if (fields.size() < 12) {
                    continue;
                }
                String scenario = fields.get(1);
                double nodesPerSecond = parseDoubleSafe(fields.get(3), 0d);
                double bestScore = parseDoubleSafe(fields.get(11), Double.NaN);
                rows.put(scenario, new BaselineMetricsRow(nodesPerSecond, bestScore));
            }
            return rows;
        } catch (IOException ioe) {
            throw new RuntimeException("Failed reading baseline artifact " + artifactPath, ioe);
        }
    }

    private static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
                continue;
            }
            if (c == ',' && !inQuote) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    private static double parseDoubleSafe(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0d;
        }
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int mid = copy.length / 2;
        if ((copy.length & 1) == 0) {
            return (copy[mid - 1] + copy[mid]) / 2d;
        }
        return copy[mid];
    }

    private void assertReducedOracleEquality(JavaCity origin, boolean selfSufficient, long timeoutMs) {
        ResourceType.resetCachedMarketPricesToFallback();
        JavaCity optimized = origin.optimalBuild(
                CONTINENT,
                15,
                VALUE_FUNCTION,
                BENCHMARK_GOAL,
                NO_PROJECTS,
                timeoutMs,
                0,
                selfSufficient,
                false,
                1,
                null
        );
        assertNotNull(optimized, "Expected optimized city for reduced oracle scenario");

        BFSUtil.SearchMetrics metrics = JavaCity.getLastOptimizationMetrics();
        assertNotNull(metrics, "Expected metrics for reduced oracle scenario");

        double optimizedScore = metrics.bestScore;
        double oracleScore = exhaustiveOptimalScore(origin, selfSufficient);

        assertEquals(oracleScore, optimizedScore, EPSILON, "Optimized solver should match exhaustive oracle score");
    }

    private ScenarioBuilder scenarioBuilder(String name) {
        return switch (name) {
            case "balanced" -> this::sixtySlotBalancedScenario;
            case "commerce" -> this::sixtySlotCommerceScenario;
            case "manufacturing" -> this::sixtySlotManufacturingScenario;
            default -> throw new IllegalArgumentException("Unknown benchmark scenario: " + name);
        };
    }

    private double exhaustiveOptimalScore(JavaCity origin, boolean selfSufficient) {
        CityNode.CachedCity cached = new CityNode.CachedCity(origin, CONTINENT, selfSufficient, NO_PROJECTS, 15, 1, 0, null);
        CityBranch branch = new CityBranch(cached);
        CityNode init = branch.createInitialNode();

        Predicate<CityNode> goal = node -> node.getFreeSlots() <= 0 || node.getIndex() >= node.getCached().getMaxIndex() - 1;
        double best = Double.NEGATIVE_INFINITY;

        ArrayDeque<CityNode> stack = new ArrayDeque<>();
        stack.push(init);

        while (!stack.isEmpty()) {
            CityNode node = stack.pop();
            if (goal.test(node)) {
                double score = node.getRevenueConverted();
                if (score > best) {
                    best = score;
                }
                continue;
            }
            branch.accept(node, stack::push);
        }

        return best;
    }

    private JavaCity sixtySlotBalancedScenario() {
        JavaCity city = new JavaCity();
        city.setInfra(3_000);
        city.setLand(3_000d);
        city.setDateCreated(1_700_000_000_000L);
        city.setBuilding(Buildings.FARM, 4);
        city.setBuilding(Buildings.COAL_MINE, 2);
        city.setBuilding(Buildings.OIL_WELL, 2);
        city.setBuilding(Buildings.IRON_MINE, 2);
        city.setBuilding(Buildings.BAUXITE_MINE, 2);
        city.setBuilding(Buildings.BARRACKS, 5);
        city.setBuilding(Buildings.FACTORY, 3);
        city.setBuilding(Buildings.HANGAR, 2);
        city.setBuilding(Buildings.DRYDOCK, 1);
        city.setBuilding(Buildings.HOSPITAL, 1);
        city.setBuilding(Buildings.POLICE_STATION, 1);
        city.setOptimalPower(CONTINENT);
        return city;
    }

    private JavaCity sixtySlotCommerceScenario() {
        JavaCity city = new JavaCity();
        city.setInfra(3_000);
        city.setLand(3_000d);
        city.setDateCreated(1_700_000_000_000L);
        city.setBuilding(Buildings.SUBWAY, 4);
        city.setBuilding(Buildings.SUPERMARKET, 4);
        city.setBuilding(Buildings.BANK, 4);
        city.setBuilding(Buildings.MALL, 2);
        city.setBuilding(Buildings.STADIUM, 1);
        city.setBuilding(Buildings.BARRACKS, 4);
        city.setBuilding(Buildings.FACTORY, 2);
        city.setBuilding(Buildings.HANGAR, 1);
        city.setBuilding(Buildings.DRYDOCK, 1);
        city.setBuilding(Buildings.HOSPITAL, 1);
        city.setOptimalPower(CONTINENT);
        return city;
    }

    private JavaCity sixtySlotManufacturingScenario() {
        JavaCity city = new JavaCity();
        city.setInfra(3_000);
        city.setLand(3_000d);
        city.setDateCreated(1_700_000_000_000L);
        city.setBuilding(Buildings.COAL_MINE, 4);
        city.setBuilding(Buildings.IRON_MINE, 4);
        city.setBuilding(Buildings.BAUXITE_MINE, 4);
        city.setBuilding(Buildings.LEAD_MINE, 4);
        city.setBuilding(Buildings.OIL_WELL, 3);
        city.setBuilding(Buildings.FARM, 3);
        city.setBuilding(Buildings.STEEL_MILL, 2);
        city.setBuilding(Buildings.MUNITIONS_FACTORY, 2);
        city.setBuilding(Buildings.ALUMINUM_REFINERY, 2);
        city.setBuilding(Buildings.BARRACKS, 4);
        city.setBuilding(Buildings.FACTORY, 3);
        city.setBuilding(Buildings.HANGAR, 1);
        city.setBuilding(Buildings.HOSPITAL, 1);
        city.setOptimalPower(CONTINENT);
        return city;
    }

    private JavaCity reducedScenario(double infra, double land, java.util.function.Consumer<JavaCity> setup) {
        JavaCity city = new JavaCity();
        city.setInfra(infra);
        city.setLand(land);
        city.setDateCreated(1_700_000_000_000L);
        setup.accept(city);
        return city;
    }

    private static String citySignature(ICity city) {
        StringBuilder sb = new StringBuilder();
        for (Building building : Buildings.values()) {
            int amount = city.getBuilding(building);
            if (amount <= 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(building.name()).append('=').append(amount);
        }
        return sb.toString();
    }

    private record BaselineSnapshot(double bestScore, String signature) {
    }

    private static boolean readFlag(String propertyKey, String envKey, boolean defaultValue) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            return Boolean.parseBoolean(envValue);
        }
        return defaultValue;
    }

    private static String readString(String propertyKey, String envKey, String defaultValue) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private static int readInt(String propertyKey, String envKey, int defaultValue) {
        String raw = readString(propertyKey, envKey, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid integer for " + propertyKey + " / " + envKey + ": " + raw, nfe);
        }
    }

    private static double readDouble(String propertyKey, String envKey, double defaultValue) {
        String raw = readString(propertyKey, envKey, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid decimal for " + propertyKey + " / " + envKey + ": " + raw, nfe);
        }
    }

    private static Set<String> parseScenarioSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(values::add);
        return Set.copyOf(values);
    }

    private static List<String> parseScenarios(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("balanced");
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(ordered::add);

        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("No scenarios provided in " + SCENARIOS_PROPERTY + " / " + SCENARIOS_ENV);
        }

        for (String name : ordered) {
            if (!ALL_SCENARIOS.contains(name)) {
                throw new IllegalArgumentException("Unknown scenario '" + name + "'. Supported: " + ALL_SCENARIOS);
            }
        }

        return List.copyOf(ordered);
    }

    private record BenchmarkConfig(
            List<String> scenarios,
            long timeoutMs,
            boolean oracleEnabled,
            boolean enforceBaseline,
            String suite,
            String artifactPath,
            String baselineArtifactPath,
            boolean enforceGates,
            double quickThroughputMinGain,
            double fullThroughputMinGain,
            double stabilityMaxRegression,
            double parityTolerance,
            Set<String> stabilityWaivers
    ) {
        static BenchmarkConfig read() {
            List<String> scenarios = parseScenarios(readString(SCENARIOS_PROPERTY, SCENARIOS_ENV, null));
            long timeoutMs = readInt(TIMEOUT_PROPERTY, TIMEOUT_ENV, (int) DEFAULT_TIMEOUT_MS);
            boolean oracle = readFlag(ORACLE_PROPERTY, ORACLE_ENV, false);
            boolean enforceBaseline = ENFORCE_BASELINE;
            String suite = readString(SUITE_PROPERTY, SUITE_ENV, "quick").trim().toLowerCase(Locale.ROOT);
            String artifactPath = readString(ARTIFACT_PATH_PROPERTY, ARTIFACT_PATH_ENV,
                "build/reports/city-bench/" + suite + "-metrics.csv");
            String baselineArtifactPath = readString(BASELINE_ARTIFACT_PROPERTY, BASELINE_ARTIFACT_ENV, null);
            boolean enforceGates = readFlag(ENFORCE_GATES_PROPERTY, ENFORCE_GATES_ENV, false);
            double quickThroughputMinGain = readDouble(QUICK_THROUGHPUT_GAIN_PROPERTY, QUICK_THROUGHPUT_GAIN_ENV, 0d);
            double fullThroughputMinGain = readDouble(FULL_THROUGHPUT_GAIN_PROPERTY, FULL_THROUGHPUT_GAIN_ENV, 0d);
            double stabilityMaxRegression = readDouble(STABILITY_MAX_REGRESSION_PROPERTY, STABILITY_MAX_REGRESSION_ENV, 0.05d);
            double parityTolerance = readDouble(PARITY_TOLERANCE_PROPERTY, PARITY_TOLERANCE_ENV, EPSILON);
            Set<String> stabilityWaivers = parseScenarioSet(readString(STABILITY_WAIVERS_PROPERTY, STABILITY_WAIVERS_ENV, null));

            System.out.printf(Locale.ROOT,
                "benchmark-config suite=%s scenarios=%s timeoutMs=%d oracle=%s enforceBaseline=%s enforceGates=%s artifactPath=%s baselineArtifact=%s%n",
                suite,
                    String.join(",", scenarios),
                    timeoutMs,
                    oracle,
                enforceBaseline,
                enforceGates,
                artifactPath,
                baselineArtifactPath
            );

            return new BenchmarkConfig(
                scenarios,
                timeoutMs,
                oracle,
                enforceBaseline,
                suite,
                artifactPath,
                baselineArtifactPath,
                enforceGates,
                quickThroughputMinGain,
                fullThroughputMinGain,
                stabilityMaxRegression,
                parityTolerance,
                stabilityWaivers
            );
        }
    }

        private record ScenarioResult(
            String scenario,
            String signature,
            double bestScore,
            BFSUtil.SearchMetrics metrics,
            double convergenceAuc,
            double timeToThresholdRatio
        ) {
        }

        private record BaselineMetricsRow(double nodesPerSecond, double bestScore) {
        }

    @FunctionalInterface
    private interface ScenarioBuilder {
        JavaCity build();
    }
}

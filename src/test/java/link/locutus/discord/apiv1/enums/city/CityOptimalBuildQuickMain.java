package link.locutus.discord.apiv1.enums.city;

public final class CityOptimalBuildQuickMain {
    private static final String BENCH_TIMEOUT_PROPERTY = "locutus.city.benchmark.timeoutMs";
    private static final String BENCH_TIMEOUT_ENV = "LOCUTUS_CITY_BENCHMARK_TIMEOUT_MS";
    private static final String BFS_STRICT_PROPERTY = "locutus.bfs.strictTimeout";
    private static final String BFS_STRICT_ENV = "LOCUTUS_BFS_STRICT_TIMEOUT";
    private static final String BFS_MAX_TIMEOUT_PROPERTY = "locutus.bfs.maxTimeoutMs";
    private static final String BFS_MAX_TIMEOUT_ENV = "LOCUTUS_BFS_MAX_TIMEOUT_MS";
    private static final String BENCH_SUITE_PROPERTY = "locutus.city.benchmark.suite";
    private static final String BENCH_SUITE_ENV = "LOCUTUS_CITY_BENCHMARK_SUITE";

    private CityOptimalBuildQuickMain() {
    }

    public static void main(String[] args) {
        int exitCode = 0;
        if (System.getProperty(BFS_STRICT_PROPERTY) == null
                && System.getenv(BFS_STRICT_ENV) == null) {
            System.setProperty(BFS_STRICT_PROPERTY, "true");
        }
        if (System.getProperty(BFS_MAX_TIMEOUT_PROPERTY) == null
                && System.getenv(BFS_MAX_TIMEOUT_ENV) == null) {
            long benchmarkTimeoutMs = readPositiveLong(BENCH_TIMEOUT_PROPERTY, BENCH_TIMEOUT_ENV, 1500L);
            long maxTimeoutMs = Math.max(benchmarkTimeoutMs + 500L, benchmarkTimeoutMs * 2L);
            System.setProperty(BFS_MAX_TIMEOUT_PROPERTY, Long.toString(maxTimeoutMs));
        }
        if (System.getProperty(BENCH_SUITE_PROPERTY) == null
                && System.getenv(BENCH_SUITE_ENV) == null) {
            System.setProperty(BENCH_SUITE_PROPERTY, "quick");
        }
        try {
            CityOptimalBuildBenchmarkTest harness = new CityOptimalBuildBenchmarkTest();
            harness.runConfiguredHarness();
        } catch (RuntimeException runtimeException) {
            exitCode = 1;
            throw runtimeException;
        } finally {
            System.exit(exitCode);
        }
    }

    private static long readPositiveLong(String propertyKey, String envKey, long defaultValue) {
        String fromProperty = System.getProperty(propertyKey);
        if (fromProperty != null) {
            return parsePositiveLongOrDefault(fromProperty, defaultValue);
        }
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null) {
            return parsePositiveLongOrDefault(fromEnv, defaultValue);
        }
        return defaultValue;
    }

    private static long parsePositiveLongOrDefault(String raw, long defaultValue) {
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }
}

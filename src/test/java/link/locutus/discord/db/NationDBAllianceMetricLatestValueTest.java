package link.locutus.discord.db;

import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.config.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class NationDBAllianceMetricLatestValueTest {
    @Test
    void getAllianceMetricsReturnsLatestValuePerAllianceUpToTurn() throws Exception {
        Path tempDir = Files.createTempDirectory("nationdb-alliance-metrics-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB()) {
            DBAlliance aa1 = nationDb.getOrCreateAlliance(1);
            DBAlliance aa2 = nationDb.getOrCreateAlliance(2);

            nationDb.addAllianceMetric(aa1, AllianceMetric.SCORE, 5L, 5d, false);
            nationDb.addAllianceMetric(aa1, AllianceMetric.SCORE, 10L, 10d, false);
            nationDb.addAllianceMetric(aa1, AllianceMetric.SCORE, 11L, 11d, false);
            nationDb.addAllianceMetric(aa2, AllianceMetric.SCORE, 3L, 3d, false);
            nationDb.addAllianceMetric(aa2, AllianceMetric.SCORE, 9L, 9d, false);
            nationDb.addAllianceMetric(aa2, AllianceMetric.SCORE, 12L, 12d, false);

            Map<Integer, Double> latestByAlliance = new LinkedHashMap<>();
            nationDb.getAllianceMetrics(Set.of(1, 2), AllianceMetric.SCORE, 10L).forEach((alliance, byMetric) ->
                    latestByAlliance.put(
                            alliance.getAlliance_id(),
                            byMetric.get(AllianceMetric.SCORE).values().iterator().next()
                    )
            );

            assertEquals(Map.of(1, 10d, 2, 9d), latestByAlliance);
        } finally {
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}

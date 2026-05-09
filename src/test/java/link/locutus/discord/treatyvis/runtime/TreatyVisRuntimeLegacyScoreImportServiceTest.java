package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeLegacyScoreImportServiceTest {
    private final ObjectMapper msgpack = new ObjectMapper(new MessagePackFactory());

    @TempDir
    Path tempDir;

    @Test
    void importsStagedHistoricalScoreWindowsIntoRuntimeTable() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        writeScoreWindow(
                stagedImportRoot.resolve(Path.of("public", "data", "alliance_scores_v2_window_2025-01.msgpack")),
                List.of("2025-01-01", "2025-01-02"),
                List.of(
                        List.of(List.of(881, 175210), List.of(4729, 182340), List.of(9123, 170500)),
                        List.of(List.of(9123, 171000), List.of(4729, 183100))
                )
        );

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyScoreImportService service = new TreatyVisRuntimeLegacyScoreImportService(
                    stagedImportRoot,
                    repository,
                    msgpack
            );

            TreatyVisRuntimeLegacyScoreImportService.ScoreImportResult result = service.importHistoricalScores(false);

            assertEquals(2, result.importedDayCount());
            assertEquals(5, result.importedRowCount());
            assertEquals(Math.toIntExact(java.time.LocalDate.parse("2025-01-01").toEpochDay()), result.minDay());
            assertEquals(Math.toIntExact(java.time.LocalDate.parse("2025-01-02").toEpochDay()), result.maxDay());

            Map<Integer, byte[]> storedRows = repository.loadTopNScoreRows();
            assertEquals(2, storedRows.size());
            assertArrayEquals(
                    TreatyVisRuntimeLegacyScoreImportService.encodeRows(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(9123, 170500)
                    )),
                    storedRows.values().iterator().next()
            );

            TreatyVisRuntimeRepository.RuntimeBootstrapState state = repository.loadBootstrapState();
            assertTrue(state.scoreImportComplete());
            assertEquals(result.minDay(), state.importedScoreMinDay());
            assertEquals(result.maxDay(), state.importedScoreMaxDay());
        }
    }

    @Test
    void requantizesLegacyHistoricalScoreWindowsIntoRuntimeScale() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-requantized");
        writeScoreWindow(
                stagedImportRoot.resolve(Path.of("public", "data", "alliance_scores_v2_window_2025-01.msgpack")),
                1000,
                List.of("2025-01-01"),
                List.of(
                        List.of(List.of(881, 1752100), List.of(4729, 1823400), List.of(9123, 1705000))
                )
        );

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-requantized.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyScoreImportService service = new TreatyVisRuntimeLegacyScoreImportService(
                    stagedImportRoot,
                    repository,
                    msgpack
            );

            TreatyVisRuntimeLegacyScoreImportService.ScoreImportResult result = service.importHistoricalScores(false);

            assertEquals(1, result.importedDayCount());
            assertEquals(3, result.importedRowCount());
            Map<Integer, byte[]> storedRows = repository.loadTopNScoreRows();
            assertEquals(1, storedRows.size());
            assertArrayEquals(
                    TreatyVisRuntimeLegacyScoreImportService.encodeRows(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(9123, 170500)
                    )),
                    storedRows.values().iterator().next()
            );
        }
    }

    @Test
    void refusesToOverwriteImportedHistoricalScoresWithoutReplace() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        writeScoreWindow(
                stagedImportRoot.resolve(Path.of("public", "data", "alliance_scores_v2_window_2025-01.msgpack")),
                List.of("2025-01-01"),
                List.of(List.of(List.of(4729, 182340)))
        );

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyScoreImportService service = new TreatyVisRuntimeLegacyScoreImportService(
                    stagedImportRoot,
                    repository,
                    msgpack
            );

            service.importHistoricalScores(false);

            assertThrows(IOException.class, () -> service.importHistoricalScores(false));
            assertEquals(1, repository.countTopNScoreRows());
        }
    }

        private void writeScoreWindow(Path path, List<String> dayKeys, List<List<List<Integer>>> days) throws IOException {
                writeScoreWindow(path, TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION, dayKeys, days);
        }

        private void writeScoreWindow(Path path, int quantizationScale, List<String> dayKeys, List<List<List<Integer>>> days) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Map<String, Object> payload = Map.of(
                "schema_version", 2,
                                "quantization_scale", quantizationScale,
                "day_keys", dayKeys,
                "days", days
        );
        msgpack.writeValue(path.toFile(), payload);
    }
}
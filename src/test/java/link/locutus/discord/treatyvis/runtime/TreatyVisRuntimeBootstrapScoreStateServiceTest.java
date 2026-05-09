package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreatyVisRuntimeBootstrapScoreStateServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void bootstrapsLiveScoreHelperStateAndWritesBootstrapDayWhenTailDiffers() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.replaceTopNScoreRows(Map.of(
                    Math.toIntExact(LocalDate.parse("2025-01-02").toEpochDay()),
                    TreatyVisRuntimeLegacyScoreImportService.encodeRows(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    ))
            ));

            TreatyVisRuntimeBootstrapScoreStateService service = new TreatyVisRuntimeBootstrapScoreStateService(
                    repository,
                    () -> List.of(
                            new TreatyVisRuntimeBootstrapScoreStateService.LiveAllianceScore(881, 175210),
                            new TreatyVisRuntimeBootstrapScoreStateService.LiveAllianceScore(4729, 183100),
                            new TreatyVisRuntimeBootstrapScoreStateService.LiveAllianceScore(9123, 0)
                    )
            );

            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult result = service.bootstrapCurrentScores(false);

            assertEquals(3, result.helperAllianceCount());
            assertEquals(2, result.topAllianceCount());
            assertEquals(Math.toIntExact(LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()), result.bootstrapDay());
            assertEquals(true, result.wroteBootstrapDay());

            assertEquals(Map.of(881, 175210, 4729, 183100, 9123, 0), repository.loadLastAllianceScores());
            Map<Integer, byte[]> storedRows = repository.loadTopNScoreRows();
            assertArrayEquals(
                    TreatyVisRuntimeLegacyScoreImportService.encodeRows(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    )),
                    storedRows.get(result.bootstrapDay())
            );

            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResetResult resetResult = service.resetBootstrappedCurrentScores();
            assertEquals(3, resetResult.deletedHelperRowCount());
            assertEquals(0, repository.countLastAllianceScores());
        }
    }

    @Test
    void refusesToBootstrapWithoutHistoricalScoreImport() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-empty.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeBootstrapScoreStateService service = new TreatyVisRuntimeBootstrapScoreStateService(
                    repository,
                    List::of
            );

            assertThrows(IOException.class, () -> service.bootstrapCurrentScores(false));
        }
    }
}
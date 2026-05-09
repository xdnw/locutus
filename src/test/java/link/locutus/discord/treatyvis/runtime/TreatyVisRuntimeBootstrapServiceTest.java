package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.TreatyChangeAction;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeBootstrapServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void stagesLegacyArtifactsAndWritesValidationReport() throws IOException {
        Path sourceRoot = tempDir.resolve("treaty-vis");
        writeFile(sourceRoot.resolve("data/treaties.json"));
        writeFile(sourceRoot.resolve("data/treaties_archive.json"));
        writeFile(sourceRoot.resolve("data/incremental_state.json"));
        writeFile(sourceRoot.resolve("data/flags_day_cache.msgpack"));
        writeFile(sourceRoot.resolve("data/flag_download_state.json"));
        writeFile(sourceRoot.resolve("data/legacy_flags.csv"));
        writeFile(sourceRoot.resolve("data/flag_cache/hash-a"));
        writeFile(sourceRoot.resolve("public/data/treaty_changes_reconciled_index.msgpack"));
        writeFile(sourceRoot.resolve("public/data/treaty_changes_reconciled_delta.msgpack"));
        writeFile(sourceRoot.resolve("public/data/treaty_changes_reconciled_summary.msgpack"));
        writeFile(sourceRoot.resolve("public/data/treaty_changes_reconciled_window_2025-01.msgpack"));
        writeFile(sourceRoot.resolve("public/data/alliance_scores_v2_index.msgpack"));
        writeFile(sourceRoot.resolve("public/data/alliance_scores_v2_delta.msgpack"));
        writeFile(sourceRoot.resolve("public/data/alliance_scores_v2_window_2025-01.msgpack"));
        writeFile(sourceRoot.resolve("public/data/alliance_score_ranks_daily.msgpack"));
        writeFile(sourceRoot.resolve("public/data/flags_index.msgpack"));
        writeFile(sourceRoot.resolve("public/data/flags_delta.msgpack"));
        writeFile(sourceRoot.resolve("public/data/flags_window_2025-01.msgpack"));
        writeFile(sourceRoot.resolve("public/data/flag_assets.msgpack"));
        writeFile(sourceRoot.resolve("public/data/manifest.json"));

        Path importRoot = tempDir.resolve("staged-import");
        Path reportPath = tempDir.resolve("runtime_validate_report.json");
        TreatyVisRuntimeBootstrapService service = new TreatyVisRuntimeBootstrapService(
                importRoot,
                reportPath,
                new ObjectMapper()
        );

        TreatyVisRuntimeBootstrapService.ImportResult importResult = service.importLegacyArtifacts(sourceRoot, false);

        assertTrue(Files.isRegularFile(importRoot.resolve("data/treaties.json")));
        assertTrue(Files.isRegularFile(importRoot.resolve("public/data/treaty_changes_reconciled_window_2025-01.msgpack")));
        assertTrue(Files.isRegularFile(importRoot.resolve("data/flag_cache/hash-a")));
        assertEquals(1, importResult.flagCacheFileCount());
        assertEquals(1, importResult.requiredGroupCounts().get("public/data/treaty_changes_reconciled_window_*.msgpack"));

        TreatyVisRuntimeBootstrapService.ValidationResult validationResult = service.validateImportedLegacyArtifacts();

        assertTrue(validationResult.valid());
        assertEquals(1, validationResult.flagCacheFileCount());
        assertTrue(Files.isRegularFile(reportPath));
        assertEquals(1, validationResult.requiredGroupCounts().get("public/data/alliance_scores_v2_window_*.msgpack"));
    }

    @Test
    void resetRemovesStagedAndGeneratedArtifacts() throws IOException {
        Path importRoot = tempDir.resolve("staged-import");
        Path reportPath = tempDir.resolve("runtime_validate_report.json");
        writeFile(importRoot.resolve("data/treaties.json"));
        writeFile(reportPath);

        TreatyVisRuntimeBootstrapService service = new TreatyVisRuntimeBootstrapService(
                importRoot,
                reportPath,
                new ObjectMapper()
        );

        TreatyVisRuntimeBootstrapService.ResetResult result = service.resetBootstrapArtifacts();

        assertFalse(Files.exists(importRoot));
        assertFalse(Files.exists(reportPath));
        assertEquals(2, result.deletedPaths().size());
    }

        @Test
        void resetImportedRuntimeClearsRuntimeTablesStateAndArtifacts() throws Exception {
        Path importRoot = tempDir.resolve("staged-import");
        Path reportPath = tempDir.resolve("runtime_validate_report.json");
        Path runtimeFlagCacheRoot = tempDir.resolve("runtime-flag-cache");
        writeFile(importRoot.resolve("data/treaties.json"));
        writeFile(reportPath);
        writeFile(runtimeFlagCacheRoot.resolve("hash-a"));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
                repository.replaceUnifiedTreatyChanges(List.of(
                    new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(1_700_000_000_000L, TreatyChangeAction.SIGNED, TreatyType.MDP, 1, 2, 12)
            ));
            repository.markHistoricalTreatyImportComplete(100, 100);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(100, 1_700_000_000_000L, List.of(
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, 12)
            )));
            repository.replaceTopNScoreRows(Map.of(100, TreatyVisRuntimeScoreRowCodec.encode(List.of(
                new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(1, 1234)
            ))));
            repository.markHistoricalScoreImportComplete(100, 100);
            repository.replaceLastAllianceScores(List.of(new TreatyVisRuntimeRepository.LastAllianceScoreRow(1, 1234)));
            repository.replaceFlagChanges(List.of(new TreatyVisRuntimeRepository.FlagChangeRow(1, 100, new byte[] {1})));
            repository.replaceFlagIcons(List.of(new TreatyVisRuntimeRepository.FlagIconRow(new byte[] {1}, new byte[] {2})));
            repository.replaceFlagAtlasState(List.of(new TreatyVisRuntimeRepository.FlagAtlasStateRow(new byte[] {1}, 1)));
            repository.replaceLastFlagUrls(List.of(new TreatyVisRuntimeRepository.LastFlagUrlRow(1, "https://example.com/flag.png", new byte[] {1})));
            repository.markHistoricalFlagImportComplete(100, 100);
            repository.setValidationComplete(true);

            TreatyVisRuntimeBootstrapService service = new TreatyVisRuntimeBootstrapService(
                importRoot,
                reportPath,
                new ObjectMapper(),
                repository,
                new TreatyVisRuntimeLegacyTreatyImportService(importRoot, repository, new ObjectMapper(new MessagePackFactory())),
                new TreatyVisRuntimeLegacyTreatyCheckpointImportService(importRoot, repository, new ObjectMapper()),
                new TreatyVisRuntimeLegacyScoreImportService(importRoot, repository, new ObjectMapper(new MessagePackFactory())),
                new TreatyVisRuntimeBootstrapScoreStateService(repository, () -> List.of()),
                new TreatyVisRuntimeLegacyFlagImportService(importRoot, runtimeFlagCacheRoot, repository, new ObjectMapper(new MessagePackFactory()), new ObjectMapper()),
                new TreatyVisRuntimeBootstrapFlagStateService(repository, runtimeFlagCacheRoot, () -> List.of(), flagUrl -> new byte[] {1})
            );

            TreatyVisRuntimeBootstrapService.RuntimeBootstrapResetResult result = service.resetImportedRuntime();

            assertEquals(1, result.treatyReset().deletedChangeCount());
            assertEquals(1, result.checkpointReset().deletedCheckpointCount());
            assertEquals(1, result.scoreReset().deletedDayCount());
            assertEquals(1, result.scoreBootstrapReset().deletedHelperRowCount());
            assertEquals(1, result.flagReset().deletedChangeCount());
            assertEquals(1, result.flagReset().deletedRawCacheFileCount());
            assertFalse(Files.exists(importRoot));
            assertFalse(Files.exists(reportPath));
            assertFalse(Files.exists(runtimeFlagCacheRoot));
            assertEquals(0, repository.countUnifiedTreatyChanges());
            assertEquals(0, repository.countTreatyCheckpoints());
            assertEquals(0, repository.countTopNScoreRows());
            assertEquals(0, repository.countLastAllianceScores());
            assertEquals(0, repository.countFlagChanges());
            TreatyVisRuntimeRepository.RuntimeBootstrapState state = repository.loadBootstrapState();
            assertFalse(state.treatyImportComplete());
            assertFalse(state.scoreImportComplete());
            assertFalse(state.flagImportComplete());
            assertFalse(state.validationComplete());
            assertNull(state.importedTreatyMinDay());
            assertNull(state.importedScoreMinDay());
            assertNull(state.importedFlagMinDay());
        }
        }

    private static void writeFile(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, "test");
    }
}
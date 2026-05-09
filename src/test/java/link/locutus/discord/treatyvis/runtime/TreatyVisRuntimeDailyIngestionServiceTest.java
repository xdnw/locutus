package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeDailyIngestionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void skipsUntilBootstrapValidationAndCheckpointExist() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.markHistoricalTreatyImportComplete(100, 100);
            repository.markHistoricalScoreImportComplete(100, 100);
            repository.markHistoricalFlagImportComplete(100, 100);

            AtomicInteger scoreCalls = new AtomicInteger();
            AtomicInteger flagCalls = new AtomicInteger();
            AtomicInteger treatyCalls = new AtomicInteger();
            AtomicInteger exportCalls = new AtomicInteger();
            TreatyVisRuntimeDailyIngestionService service = new TreatyVisRuntimeDailyIngestionService(
                    repository,
                    () -> {
                        scoreCalls.incrementAndGet();
                        return new TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult(0, 0, 0, false);
                    },
                    () -> {
                        flagCalls.incrementAndGet();
                        return new TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult(0, 0, 0, 0);
                    },
                    () -> {
                        treatyCalls.incrementAndGet();
                        return TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult.skipped("test");
                    },
                    () -> {
                        exportCalls.incrementAndGet();
                        return new TreatyHistoryService.BuiltTreatyArtifacts(
                                new byte[0],
                                new byte[0],
                                null
                        );
                    },
                    () -> null
            );

            TreatyVisRuntimeDailyIngestionService.DailyIngestionResult result = service.runDailyIngestion();

            assertFalse(result.executed());
            assertEquals(0, scoreCalls.get());
            assertEquals(0, flagCalls.get());
            assertEquals(0, treatyCalls.get());
            assertEquals(0, exportCalls.get());
            assertTrue(result.gatingIssues().contains("Bootstrap validation is incomplete."));
            assertTrue(result.gatingIssues().contains("No treaty checkpoint is available for runtime ingestion."));
        }
    }

    @Test
    void runsScoreFlagAndExportWhenBootstrapIsReady() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-ready.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.markHistoricalTreatyImportComplete(100, 100);
            repository.markHistoricalScoreImportComplete(100, 100);
            repository.markHistoricalFlagImportComplete(100, 100);
            repository.setValidationComplete(true);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    100,
                    1_700_000_000_000L,
                    java.util.List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, 12))
            ));

            AtomicInteger scoreCalls = new AtomicInteger();
            AtomicInteger flagCalls = new AtomicInteger();
            AtomicInteger treatyCalls = new AtomicInteger();
            AtomicInteger exportCalls = new AtomicInteger();
            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreResult = new TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult(5, 3, 200, true);
            TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagResult = new TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult(4, 2, 1, 200);
            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult treatyResult =
                    TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult.completed(
                            100,
                            107,
                            107,
                            1,
                            1,
                            true,
                            true,
                            false,
                                true,
                                0
                    );
                byte[] payloadBytes = new byte[] {1, 2, 3, 4};
            TreatyVisRuntimeDailyIngestionService service = new TreatyVisRuntimeDailyIngestionService(
                    repository,
                    () -> {
                        scoreCalls.incrementAndGet();
                        return scoreResult;
                    },
                    () -> {
                        flagCalls.incrementAndGet();
                        return flagResult;
                    },
                    () -> {
                        treatyCalls.incrementAndGet();
                        return treatyResult;
                    },
                    () -> {
                        exportCalls.incrementAndGet();
                        return new TreatyHistoryService.BuiltTreatyArtifacts(
                                payloadBytes,
                                new byte[12],
                                TreatyVisRuntimeFlagAssetUtil.sha256(payloadBytes).asBytes()
                        );
                    },
                    () -> new link.locutus.discord.web.jooby.CloudStorage() {
                        @Override
                        public void putObject(String key, byte[] data, long maxAge) {
                        }

                        @Override
                        public byte[] getObject(String key) {
                            return new byte[0];
                        }

                        @Override
                        public String getLink(String key) {
                            return "https://example.invalid/" + key;
                        }

                        @Override
                        public void deleteObject(String key) {
                        }

                        @Override
                        public java.util.List<link.locutus.discord.web.jooby.CloudItem> getObjects() {
                            return java.util.List.of();
                        }

                        @Override
                        public void close() {
                        }
                    }
            );

            TreatyVisRuntimeDailyIngestionService.DailyIngestionResult result = service.runDailyIngestion();

            assertTrue(result.executed());
            assertEquals(1, scoreCalls.get());
            assertEquals(1, flagCalls.get());
            assertEquals(1, treatyCalls.get());
            assertEquals(1, exportCalls.get());
            assertEquals(treatyResult, result.treatyCheckpointResult());
            assertEquals(scoreResult, result.scoreResult());
            assertEquals(flagResult, result.flagResult());
            assertEquals(payloadBytes.length, result.publishedArtifacts().historyBytes());
            assertEquals(
                    TreatyVisRuntimeFlagAssetUtil.sha256(payloadBytes),
                    TreatyVisRuntimeFlagAssetUtil.hashCode(repository.loadLastPayloadSha256())
            );
        }
    }
}

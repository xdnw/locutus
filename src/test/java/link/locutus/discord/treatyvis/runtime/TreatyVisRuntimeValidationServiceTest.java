package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeValidationServiceTest {
    private final ObjectMapper msgpack = new ObjectMapper(new MessagePackFactory());
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void validatesImportedRuntimeTablesAndWritesReport() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        byte[] alphaRaw = createPngBytes(new Color(200, 10, 10));
        byte[] betaRaw = createPngBytes(new Color(10, 10, 200));
        String alphaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).toString();
        String betaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).toString();
        String alphaNormalizedHash = "a".repeat(64);
        String betaNormalizedHash = "b".repeat(64);
        Files.write(stagedData.resolve(Path.of("flag_cache", "alpha-cache.png")), alphaRaw);
        Files.write(stagedData.resolve(Path.of("flag_cache", "beta-cache.png")), betaRaw);

        Files.writeString(stagedData.resolve("treaties.json"), "[]\n");
        Files.writeString(stagedData.resolve("treaties_archive.json"), "{}\n");
        Map<String, Object> activeTreaty = new LinkedHashMap<>();
        activeTreaty.put("treaty_type", "MDP");
        activeTreaty.put("opened_at", "2026-03-01T00:00:00Z");
        activeTreaty.put("expires_at", null);
        activeTreaty.put("last_event_id", "event-a");

        json.writeValue(stagedData.resolve("incremental_state.json").toFile(), Map.of(
                "schema_version", 1,
                "event_store", Map.of(
                        "max_timestamp", "2026-03-03T14:04:08.958000Z",
                        "active", Map.of(
                                "4729:881", Map.of(
                                        "MDP", activeTreaty
                                )
                        )
                )
        ));
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "urls", Map.of(
                        "https://flags.test/alpha.png", Map.of("cache_file", "alpha-cache.png", "content_sha256", alphaRawHash),
                        "https://flags.test/beta.png", Map.of("cache_file", "beta-cache.png", "content_sha256", betaRawHash)
                )
        ));
        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 4729, "raw_flag_url", "https://flags.test/alpha.png"),
                        Map.of("timestamp", "2025-01-02T00:00:00Z", "alliance_id", 881, "raw_flag_url", "https://flags.test/beta.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash,
                        "https://flags.test/beta.png", betaNormalizedHash
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of("columns", 16),
                "assets", Map.of(
                        "a", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "b", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash)
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of(Map.of("file", "treaty_changes_reconciled_window_2025-01.msgpack"))
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_window_2025-01.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "time_remaining_turns", 12,
                        "source", "endpoint_delta"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2026-03-03T16:04:08.958Z",
                        "action", "expired",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "turns_remaining", 0,
                        "source", "current_truth_reconcile"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_summary.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_score_ranks_daily.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_window_2025-01.msgpack").toFile(), Map.of(
                "schema_version", 2,
                "quantization_scale", TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
                "day_keys", List.of("2025-01-01", "2025-01-02"),
                "days", List.of(
                        List.of(List.of(4729, 182340), List.of(881, 175210)),
                        List.of(List.of(4729, 183100), List.of(881, 175210))
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flags_window_2025-01.msgpack").toFile(), Map.of());

        Path importRoot = tempDir.resolve("runtime-import");
        Path reportPath = tempDir.resolve("runtime_validate_report.json");
        Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime", "flag_cache"));

        TreatyVisRuntimeBootstrapService bootstrapService = new TreatyVisRuntimeBootstrapService(importRoot, reportPath, json);
        bootstrapService.importLegacyArtifacts(stagedImportRoot, false);

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService = new TreatyVisRuntimeLegacyTreatyImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyScoreImportService scoreImportService = new TreatyVisRuntimeLegacyScoreImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyFlagImportService flagImportService = new TreatyVisRuntimeLegacyFlagImportService(importRoot, runtimeFlagCacheRoot, repository, msgpack, json);

            treatyImportService.importHistoricalTreaties(false);
            scoreImportService.importHistoricalScores(false);
            flagImportService.importHistoricalFlags(false);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    Math.toIntExact(LocalDate.parse("2026-03-03").toEpochDay()),
                    Instant.parse("2026-03-03T14:04:08.958000Z").toEpochMilli(),
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.MDP, -1))
            ));
            repository.replaceLastAllianceScores(List.of(
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(4729, 183100),
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(881, 175210)
            ));
            repository.replaceTopNScoreRows(Map.of(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    )),
                    Math.toIntExact(LocalDate.parse("2025-01-02").toEpochDay()), TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    ))
            ));

            TreatyVisRuntimeValidationService service = new TreatyVisRuntimeValidationService(
                    bootstrapService,
                    repository,
                    treatyImportService,
                    scoreImportService,
                    flagImportService,
                    new TreatyVisRuntimeImportedSnapshotService(repository),
                    reportPath,
                    runtimeFlagCacheRoot,
                    List::of,
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(4729, 183100),
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(881, 175210)
                    ),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(4729, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(881, "https://flags.test/beta.png")
                    ),
                    allianceId -> "Alliance " + allianceId,
                    json
            );

            TreatyVisRuntimeValidationService.RuntimeValidationResult result = service.validateImportedRuntime();

            assertTrue(result.valid());
            assertTrue(Files.isRegularFile(reportPath));
            String report = Files.readString(reportPath);
            assertTrue(report.contains("checkpointProvenance"));
            assertTrue(report.contains("importedSnapshotAvailable"));
                        assertTrue(report.contains("treatyReplay"));
                        assertTrue(report.contains("matchesCurrentTruth"));
                        assertTrue(report.contains("payloadReplay"));
                        assertTrue(report.contains("matchesImportedSource"));
                        assertTrue(report.contains("bootstrapLiveHandoff"));
                        assertTrue(report.contains("scoreMatchesCurrentLive"));
            assertEquals(reportPath.toAbsolutePath().normalize(), result.reportPath());
            assertTrue(repository.loadBootstrapState().validationComplete());
        }
    }

    @Test
    void ignoresCurrentLiveScoreDriftWhenImportedScoreStateIsInternallyConsistent() throws Exception {
        Path stagedImportRoot = tempDir.resolve("drift-import");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        byte[] alphaRaw = createPngBytes(new Color(120, 80, 20));
        byte[] betaRaw = createPngBytes(new Color(20, 80, 120));
        String alphaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).toString();
        String betaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).toString();
        String alphaNormalizedHash = "e".repeat(64);
        String betaNormalizedHash = "f".repeat(64);
        Files.write(stagedData.resolve(Path.of("flag_cache", "alpha-cache.png")), alphaRaw);
        Files.write(stagedData.resolve(Path.of("flag_cache", "beta-cache.png")), betaRaw);

        Files.writeString(stagedData.resolve("treaties.json"), "[]\n");
        Files.writeString(stagedData.resolve("treaties_archive.json"), "{}\n");
        Map<String, Object> activeTreaty = new LinkedHashMap<>();
        activeTreaty.put("treaty_type", "MDP");
        activeTreaty.put("opened_at", "2026-03-01T00:00:00Z");
        activeTreaty.put("expires_at", null);
        activeTreaty.put("last_event_id", "event-a");

        json.writeValue(stagedData.resolve("incremental_state.json").toFile(), Map.of(
                "schema_version", 1,
                "event_store", Map.of(
                        "max_timestamp", "2026-03-03T14:04:08.958000Z",
                        "active", Map.of(
                                "4729:881", Map.of(
                                        "MDP", activeTreaty
                                )
                        )
                )
        ));
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "urls", Map.of(
                        "https://flags.test/alpha.png", Map.of("cache_file", "alpha-cache.png", "content_sha256", alphaRawHash),
                        "https://flags.test/beta.png", Map.of("cache_file", "beta-cache.png", "content_sha256", betaRawHash)
                )
        ));
        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 4729, "raw_flag_url", "https://flags.test/alpha.png"),
                        Map.of("timestamp", "2025-01-02T00:00:00Z", "alliance_id", 881, "raw_flag_url", "https://flags.test/beta.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash,
                        "https://flags.test/beta.png", betaNormalizedHash
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of("columns", 16),
                "assets", Map.of(
                        "a", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "b", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash)
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of(Map.of("file", "treaty_changes_reconciled_window_2025-01.msgpack"))
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_window_2025-01.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "time_remaining_turns", 12,
                        "source", "endpoint_delta"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2026-03-03T16:04:08.958Z",
                        "action", "expired",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "turns_remaining", 0,
                        "source", "current_truth_reconcile"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_summary.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_score_ranks_daily.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_window_2025-01.msgpack").toFile(), Map.of(
                "schema_version", 2,
                "quantization_scale", TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
                "day_keys", List.of("2025-01-01", "2025-01-02"),
                "days", List.of(
                        List.of(List.of(4729, 182340), List.of(881, 175210)),
                        List.of(List.of(4729, 183100), List.of(881, 175210))
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flags_window_2025-01.msgpack").toFile(), Map.of());

        Path importRoot = tempDir.resolve("runtime-drift-import");
        Path reportPath = tempDir.resolve("runtime_validate_drift_report.json");
        Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime-drift", "flag_cache"));

        TreatyVisRuntimeBootstrapService bootstrapService = new TreatyVisRuntimeBootstrapService(importRoot, reportPath, json);
        bootstrapService.importLegacyArtifacts(stagedImportRoot, false);

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-drift.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService = new TreatyVisRuntimeLegacyTreatyImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyScoreImportService scoreImportService = new TreatyVisRuntimeLegacyScoreImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyFlagImportService flagImportService = new TreatyVisRuntimeLegacyFlagImportService(importRoot, runtimeFlagCacheRoot, repository, msgpack, json);

            treatyImportService.importHistoricalTreaties(false);
            scoreImportService.importHistoricalScores(false);
            flagImportService.importHistoricalFlags(false);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    Math.toIntExact(LocalDate.parse("2026-03-03").toEpochDay()),
                    Instant.parse("2026-03-03T14:04:08.958000Z").toEpochMilli(),
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.MDP, -1))
            ));
            repository.replaceLastAllianceScores(List.of(
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(4729, 183100),
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(881, 175210)
            ));
            repository.replaceTopNScoreRows(Map.of(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    )),
                    Math.toIntExact(LocalDate.parse("2025-01-02").toEpochDay()), TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    ))
            ));

            TreatyVisRuntimeValidationService service = new TreatyVisRuntimeValidationService(
                    bootstrapService,
                    repository,
                    treatyImportService,
                    scoreImportService,
                    flagImportService,
                    new TreatyVisRuntimeImportedSnapshotService(repository),
                    reportPath,
                    runtimeFlagCacheRoot,
                    List::of,
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(4729, 190000),
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(881, 175210)
                    ),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(4729, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(881, "https://flags.test/beta.png")
                    ),
                    allianceId -> "Alliance " + allianceId,
                    json
            );

            TreatyVisRuntimeValidationService.RuntimeValidationResult result = service.validateImportedRuntime();

            assertTrue(result.valid());
            String report = Files.readString(reportPath);
            assertTrue(report.contains("\"scoreMatchesCurrentLive\" : false"));
            assertTrue(repository.loadBootstrapState().validationComplete());
        }
    }

    @Test
    void ignoresTreatySyntheticGrowthAndCheckpointDriftWhenImportedTreatyStateIsInternallyConsistent() throws Exception {
        Path stagedImportRoot = tempDir.resolve("treaty-drift-import");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        byte[] alphaRaw = createPngBytes(new Color(80, 120, 20));
        byte[] betaRaw = createPngBytes(new Color(20, 120, 80));
        String alphaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).toString();
        String betaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).toString();
        String alphaNormalizedHash = "1".repeat(64);
        String betaNormalizedHash = "2".repeat(64);
        Files.write(stagedData.resolve(Path.of("flag_cache", "alpha-cache.png")), alphaRaw);
        Files.write(stagedData.resolve(Path.of("flag_cache", "beta-cache.png")), betaRaw);

        Files.writeString(stagedData.resolve("treaties.json"), "[]\n");
        Files.writeString(stagedData.resolve("treaties_archive.json"), "{}\n");
        Map<String, Object> activeTreaty = new LinkedHashMap<>();
        activeTreaty.put("treaty_type", "MDP");
        activeTreaty.put("opened_at", "2025-01-01T00:00:00Z");
        activeTreaty.put("expires_at", "2025-01-01T02:00:00Z");
        activeTreaty.put("last_event_id", "event-a");

        json.writeValue(stagedData.resolve("incremental_state.json").toFile(), Map.of(
                "schema_version", 1,
                "event_store", Map.of(
                        "max_timestamp", "2025-01-01T00:00:00Z",
                        "active", Map.of(
                                "4729:881", Map.of(
                                        "MDP", activeTreaty
                                )
                        )
                )
        ));
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "urls", Map.of(
                        "https://flags.test/alpha.png", Map.of("cache_file", "alpha-cache.png", "content_sha256", alphaRawHash),
                        "https://flags.test/beta.png", Map.of("cache_file", "beta-cache.png", "content_sha256", betaRawHash)
                )
        ));
        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 4729, "raw_flag_url", "https://flags.test/alpha.png"),
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 881, "raw_flag_url", "https://flags.test/beta.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash,
                        "https://flags.test/beta.png", betaNormalizedHash
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of("columns", 16),
                "assets", Map.of(
                        "a", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "b", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash)
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of(Map.of("file", "treaty_changes_reconciled_window_2025-01.msgpack"))
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_window_2025-01.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "time_remaining_turns", 1,
                        "source", "endpoint_delta"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of());
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_summary.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_score_ranks_daily.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_window_2025-01.msgpack").toFile(), Map.of(
                "schema_version", 2,
                "quantization_scale", TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
                "day_keys", List.of("2025-01-01"),
                "days", List.of(
                        List.of(List.of(4729, 183100), List.of(881, 175210))
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flags_window_2025-01.msgpack").toFile(), Map.of());

        Path importRoot = tempDir.resolve("runtime-treaty-drift-import");
        Path reportPath = tempDir.resolve("runtime_validate_treaty_drift_report.json");
        Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime-treaty-drift", "flag_cache"));

        TreatyVisRuntimeBootstrapService bootstrapService = new TreatyVisRuntimeBootstrapService(importRoot, reportPath, json);
        bootstrapService.importLegacyArtifacts(stagedImportRoot, false);

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-treaty-drift.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService importTreatyService = new TreatyVisRuntimeLegacyTreatyImportService(
                    importRoot,
                    repository,
                    msgpack,
                    () -> Instant.parse("2025-01-02T00:00:00Z").toEpochMilli()
            );
            TreatyVisRuntimeLegacyTreatyImportService validationTreatyService = new TreatyVisRuntimeLegacyTreatyImportService(
                    importRoot,
                    repository,
                    msgpack,
                    () -> Instant.parse("2025-01-01T00:30:00Z").toEpochMilli()
            );
            TreatyVisRuntimeLegacyScoreImportService scoreImportService = new TreatyVisRuntimeLegacyScoreImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyFlagImportService flagImportService = new TreatyVisRuntimeLegacyFlagImportService(importRoot, runtimeFlagCacheRoot, repository, msgpack, json);

            importTreatyService.importHistoricalTreaties(false);
            scoreImportService.importHistoricalScores(false);
            flagImportService.importHistoricalFlags(false);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()),
                    Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.MDP, 1))
            ));
            repository.replaceLastAllianceScores(List.of(
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(4729, 183100),
                    new TreatyVisRuntimeRepository.LastAllianceScoreRow(881, 175210)
            ));
            repository.replaceTopNScoreRows(Map.of(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    ))
            ));

            TreatyVisRuntimeValidationService service = new TreatyVisRuntimeValidationService(
                    bootstrapService,
                    repository,
                    validationTreatyService,
                    scoreImportService,
                    flagImportService,
                    new TreatyVisRuntimeImportedSnapshotService(repository),
                    reportPath,
                    runtimeFlagCacheRoot,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(4729, 881, TreatyType.MDP, -1)),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(4729, 183100),
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(881, 175210)
                    ),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(4729, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(881, "https://flags.test/beta.png")
                    ),
                    allianceId -> "Alliance " + allianceId,
                    json
            );

            TreatyVisRuntimeValidationService.RuntimeValidationResult result = service.validateImportedRuntime();

            assertTrue(result.valid());
            String report = Files.readString(reportPath);
            assertTrue(report.contains("\"sourceCount\" : 1"));
            assertTrue(report.contains("\"importedCount\" : 2"));
            assertTrue(report.contains("\"matchesCurrentTruth\" : false"));
            assertTrue(repository.loadBootstrapState().validationComplete());
        }
    }

    @Test
    void importWorkflowCanRetryAfterReset() throws Exception {
        Path stagedImportRoot = tempDir.resolve("workflow-source");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        byte[] alphaRaw = createPngBytes(new Color(160, 30, 30));
        byte[] betaRaw = createPngBytes(new Color(30, 30, 160));
        String alphaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).toString();
        String betaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).toString();
        String alphaNormalizedHash = "c".repeat(64);
        String betaNormalizedHash = "d".repeat(64);
        Files.write(stagedData.resolve(Path.of("flag_cache", "alpha-cache.png")), alphaRaw);
        Files.write(stagedData.resolve(Path.of("flag_cache", "beta-cache.png")), betaRaw);

        Files.writeString(stagedData.resolve("treaties.json"), "[]\n");
        Files.writeString(stagedData.resolve("treaties_archive.json"), "{}\n");
        Map<String, Object> activeTreaty = new LinkedHashMap<>();
        activeTreaty.put("treaty_type", "MDP");
        activeTreaty.put("opened_at", "2026-03-01T00:00:00Z");
        activeTreaty.put("expires_at", null);
        activeTreaty.put("last_event_id", "event-a");
        json.writeValue(stagedData.resolve("incremental_state.json").toFile(), Map.of(
                "schema_version", 1,
                "event_store", Map.of(
                        "max_timestamp", "2026-03-03T14:04:08.958000Z",
                        "active", Map.of("4729:881", Map.of("MDP", activeTreaty))
                )
        ));
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "urls", Map.of(
                        "https://flags.test/alpha.png", Map.of("cache_file", "alpha-cache.png", "content_sha256", alphaRawHash),
                        "https://flags.test/beta.png", Map.of("cache_file", "beta-cache.png", "content_sha256", betaRawHash)
                )
        ));
        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 4729, "raw_flag_url", "https://flags.test/alpha.png"),
                        Map.of("timestamp", "2025-01-02T00:00:00Z", "alliance_id", 881, "raw_flag_url", "https://flags.test/beta.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash,
                        "https://flags.test/beta.png", betaNormalizedHash
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of("columns", 16),
                "assets", Map.of(
                        "a", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "b", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash)
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of(Map.of("file", "treaty_changes_reconciled_window_2025-01.msgpack"))
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_window_2025-01.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "time_remaining_turns", 12,
                        "source", "endpoint_delta"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-02T00:00:00Z",
                        "action", "expired",
                        "treaty_type", "MDP",
                        "from_alliance_id", 4729,
                        "to_alliance_id", 881,
                        "turns_remaining", -1,
                        "source", "current_truth_reconcile"
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("treaty_changes_reconciled_summary.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_score_ranks_daily.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_index.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("flags_delta.msgpack").toFile(), Map.of());
        msgpack.writeValue(stagedPublicData.resolve("alliance_scores_v2_window_2025-01.msgpack").toFile(), Map.of(
                "schema_version", 2,
                "quantization_scale", TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
                "day_keys", List.of("2025-01-01", "2025-01-02"),
                "days", List.of(
                        List.of(List.of(4729, 182340), List.of(881, 175210)),
                        List.of(List.of(4729, 183100), List.of(881, 175210))
                )
        ));
        msgpack.writeValue(stagedPublicData.resolve("flags_window_2025-01.msgpack").toFile(), Map.of());

        Path importRoot = tempDir.resolve("workflow-import");
        Path reportPath = tempDir.resolve("workflow_validate_report.json");
        Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("workflow", "flag_cache"));

        TreatyVisRuntimeBootstrapService bootstrapService = new TreatyVisRuntimeBootstrapService(importRoot, reportPath, json);

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("workflow.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService = new TreatyVisRuntimeLegacyTreatyImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeLegacyTreatyCheckpointImportService checkpointImportService = new TreatyVisRuntimeLegacyTreatyCheckpointImportService(importRoot, repository, json);
            TreatyVisRuntimeLegacyScoreImportService scoreImportService = new TreatyVisRuntimeLegacyScoreImportService(importRoot, repository, msgpack);
            TreatyVisRuntimeBootstrapScoreStateService scoreBootstrapService = new TreatyVisRuntimeBootstrapScoreStateService(
                    repository,
                    () -> List.of(
                            new TreatyVisRuntimeBootstrapScoreStateService.LiveAllianceScore(4729, 183100),
                            new TreatyVisRuntimeBootstrapScoreStateService.LiveAllianceScore(881, 175210)
                    )
            );
            TreatyVisRuntimeLegacyFlagImportService flagImportService = new TreatyVisRuntimeLegacyFlagImportService(importRoot, runtimeFlagCacheRoot, repository, msgpack, json);
            TreatyVisRuntimeBootstrapFlagStateService flagBootstrapService = new TreatyVisRuntimeBootstrapFlagStateService(
                    repository,
                    runtimeFlagCacheRoot,
                    () -> List.of(
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(4729, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(881, "https://flags.test/beta.png")
                    ),
                    flagUrl -> switch (flagUrl) {
                        case "https://flags.test/alpha.png" -> alphaRaw;
                        case "https://flags.test/beta.png" -> betaRaw;
                        default -> throw new IllegalArgumentException("Unexpected flag url: " + flagUrl);
                    }
            );
            TreatyVisRuntimeValidationService validationService = new TreatyVisRuntimeValidationService(
                    bootstrapService,
                    repository,
                    treatyImportService,
                    scoreImportService,
                    flagImportService,
                    new TreatyVisRuntimeImportedSnapshotService(repository),
                    reportPath,
                    runtimeFlagCacheRoot,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(4729, 881, TreatyType.MDP, -1)),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(4729, 183100),
                            new TreatyVisRuntimeValidationService.LiveAllianceScoreRow(881, 175210)
                    ),
                    () -> List.of(
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(4729, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeValidationService.LiveAllianceFlagRow(881, "https://flags.test/beta.png")
                    ),
                    allianceId -> "Alliance " + allianceId,
                    json
            );

            bootstrapService.importLegacyArtifacts(stagedImportRoot, false);
            treatyImportService.importHistoricalTreaties(false);
            checkpointImportService.importHistoricalTreatyCheckpoint(false);
            scoreImportService.importHistoricalScores(false);
            scoreBootstrapService.bootstrapCurrentScores(false);
            flagImportService.importHistoricalFlags(false);
            flagBootstrapService.bootstrapCurrentFlags();
            assertTrue(validationService.validateImportedRuntime().valid());
            assertTrue(repository.loadBootstrapState().validationComplete());

            assertThrows(IOException.class, () -> treatyImportService.importHistoricalTreaties(false));

            treatyImportService.resetImportedHistoricalTreaties();
            checkpointImportService.resetImportedHistoricalTreatyCheckpoint();
            scoreImportService.resetImportedHistoricalScores();
            scoreBootstrapService.resetBootstrappedCurrentScores();
            flagImportService.resetImportedHistoricalFlags();
            bootstrapService.resetBootstrapArtifacts();
            repository.resetBootstrapState();
            assertFalse(repository.loadBootstrapState().validationComplete());

            bootstrapService.importLegacyArtifacts(stagedImportRoot, false);
            treatyImportService.importHistoricalTreaties(false);
            checkpointImportService.importHistoricalTreatyCheckpoint(false);
            scoreImportService.importHistoricalScores(false);
            scoreBootstrapService.bootstrapCurrentScores(false);
            flagImportService.importHistoricalFlags(false);
            flagBootstrapService.bootstrapCurrentFlags();
            assertTrue(validationService.validateImportedRuntime().valid());
        }
    }

    private static byte[] createPngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y += 1) {
            for (int x = 0; x < image.getWidth(); x += 1) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

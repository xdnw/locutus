package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.TreatyChangeAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Instant;
import java.time.LocalDate;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreatyVisRuntimeImportedSnapshotServiceTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void buildsImportedRuntimeInputFromCheckpointAndRuntimeTables() throws Exception {
        int baseDay = Math.toIntExact(LocalDate.parse("2026-03-03").toEpochDay());
        long cursorMs = Instant.parse("2026-03-03T14:04:08.958000Z").toEpochMilli();
        byte[] alphaHash = com.google.common.hash.HashCode.fromString("aa".repeat(32)).asBytes();
        byte[] betaHash = com.google.common.hash.HashCode.fromString("bb".repeat(32)).asBytes();
        byte[] alphaIcon = new byte[] {1, 2, 3};
        byte[] betaIcon = new byte[] {4, 5, 6};

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.markHistoricalTreatyImportComplete(baseDay, baseDay + 2);
            repository.markHistoricalScoreImportComplete(baseDay - 10, baseDay + 2);
            repository.markHistoricalFlagImportComplete(baseDay - 10, baseDay + 2);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    baseDay + 2,
                    cursorMs + 172_800_000L,
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 9123, TreatyType.MDOAP, -1))
            ));
            repository.replaceUnifiedTreatyChanges(List.of(
                    new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                            Instant.parse("2026-03-03T01:00:00Z").toEpochMilli(),
                            TreatyChangeAction.SIGNED,
                            TreatyType.MDP,
                            4729,
                            881,
                            -1
                    ),
                    new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                            Instant.parse("2026-03-04T02:00:00Z").toEpochMilli(),
                            TreatyChangeAction.ENDED,
                            TreatyType.MDP,
                            4729,
                            881,
                            -1
                    )
            ));
            repository.replaceTopNScoreRows(Map.of(
                    baseDay, TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    )),
                    baseDay + 1, TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(9123, 171000)
                    ))
            ));
            repository.replaceFlagChanges(List.of(
                    new TreatyVisRuntimeRepository.FlagChangeRow(4729, baseDay, alphaHash),
                    new TreatyVisRuntimeRepository.FlagChangeRow(881, baseDay + 1, null),
                    new TreatyVisRuntimeRepository.FlagChangeRow(9123, baseDay + 2, betaHash)
            ));
            repository.replaceFlagIcons(List.of(
                    new TreatyVisRuntimeRepository.FlagIconRow(alphaHash, alphaIcon),
                    new TreatyVisRuntimeRepository.FlagIconRow(betaHash, betaIcon)
            ));
            repository.replaceFlagAtlasState(List.of(
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(alphaHash, 11),
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(betaHash, 29)
            ));

            TreatyVisRuntimeImportedSnapshotService service = new TreatyVisRuntimeImportedSnapshotService(repository);
            Map<Integer, String> allianceNames = Map.of(
                    4729, "Rose",
                    881, "Guardian",
                    9123, "Eclipse"
            );
            TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot imported = service.buildImportedSnapshot(
                    allianceId -> allianceNames.getOrDefault(allianceId, "AA:" + allianceId)
            );

            assertNotNull(imported);
            assertEquals(baseDay, imported.input().baseDay());
            assertEquals(List.of(), imported.input().activeTreaties());
            TreatyVisRuntimePayload payload = new TreatyVisRuntimeBuilder().build(imported.input());

            assertEquals(baseDay, payload.baseDay());
            assertEquals(List.of(), payload.initialState().activeEdgeIndexes());
            assertEquals(List.of(0, 1), payload.treatyChanges().days());
            assertEquals(List.of(0, 1, 2), payload.treatyChanges().rowOffsets());
            assertEquals(List.of(0, 0), payload.treatyChanges().edgeIndexes());
            assertEquals(List.of(1, 3), payload.treatyChanges().actions());
            assertEquals(List.of(new TreatyVisRuntimeInput.AllianceFlag(4729, 1)), imported.input().initialFlags());
            assertEquals(List.of(
                    new TreatyVisRuntimeInput.FlagChange(1, 881, 0),
                    new TreatyVisRuntimeInput.FlagChange(2, 9123, 2)
            ), imported.input().flagChanges());
            assertEquals(List.of(1), payload.initialState().flagAllianceIndexes());
            assertEquals(List.of(1), payload.initialState().flagIndexes());
            assertEquals(List.of(1, 2), payload.flagChanges().days());
            assertEquals(List.of(0, 1, 2), payload.flagChanges().rowOffsets());
            assertEquals(List.of(0, 2), payload.flagChanges().allianceIndexes());
            assertEquals(List.of(0, 2), payload.flagChanges().flagIndexes());
            assertEquals(List.of(1, 0), payload.initialState().scoreAllianceIndexes());
            assertEquals(List.of(182340, 175210), payload.initialState().scoreQuantized());
            assertEquals(List.of(1), payload.scoreSnapshots().days());
            assertEquals(List.of(0, 2), payload.scoreSnapshots().rowOffsets());
            assertEquals(List.of(1, 2), payload.scoreSnapshots().allianceIndexes());
            assertEquals(List.of(183100, 171000), payload.scoreSnapshots().scoresQuantized());
                        assertArrayEquals(alphaIcon, imported.flagIconBytesByIndex().get(1));
                        assertArrayEquals(betaIcon, imported.flagIconBytesByIndex().get(2));
        }
    }

    @Test
    void carriesFullImportedFlagAtlasCatalogWhenEventStreamUsesOnlySubset() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-full-catalog-snapshot");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        String alphaNormalizedHash = "1".repeat(64);
        String betaNormalizedHash = "2".repeat(64);
        String gammaNormalizedHash = "3".repeat(64);
        int baseDay = Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay());

        com.fasterxml.jackson.databind.ObjectMapper msgpack = TreatyVisRuntimeSerializers.MSGPACK;
        com.fasterxml.jackson.databind.ObjectMapper json = TreatyVisRuntimeSerializers.JSON;

        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 100, "raw_flag_url", "https://flags.test/alpha.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash
                )
        ));
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "created_at", "2026-02-24T20:41:59Z",
                "schema_version", 2,
                "updated_at", "2026-03-01T07:07:08Z",
                "urls", Map.of()
        ));
        Files.write(stagedPublicData.resolve("flag_atlas.webp"), createAtlasWebpBytes(
                List.of(new Color(50, 200, 50), new Color(200, 50, 50), new Color(50, 50, 200)),
                16,
                10
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of(
                        "columns", 3,
                        "tile_width", 16,
                        "tile_height", 10,
                        "rows", 1,
                        "width", 48,
                        "height", 10,
                        "count", 3,
                        "webp", "/data/flag_atlas.webp"
                ),
                "assets", Map.of(
                        "f0", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "f1", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash),
                        "f2", Map.of("x", 32, "y", 0, "w", 16, "h", 10, "hash", gammaNormalizedHash)
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-full-catalog-snapshot.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyFlagImportService flagImportService = new TreatyVisRuntimeLegacyFlagImportService(
                    stagedImportRoot,
                    tempDir.resolve(Path.of("runtime-full-catalog-snapshot", "flag_cache")),
                    repository,
                    msgpack,
                    json
            );
            flagImportService.importHistoricalFlags(false);

            repository.markHistoricalTreatyImportComplete(baseDay, baseDay);
            repository.markHistoricalScoreImportComplete(baseDay, baseDay);
            repository.replaceTopNScoreRows(Map.of(
                    baseDay,
                    TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(100, 12345)
                    ))
            ));
            repository.replaceUnifiedTreatyChanges(List.of());

            TreatyVisRuntimeImportedSnapshotService service = new TreatyVisRuntimeImportedSnapshotService(repository);
            TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot imported = service.buildImportedSnapshot(
                    allianceId -> "AA:" + allianceId
            );

            assertNotNull(imported);
            assertEquals(3, imported.flagIconBytesByIndex().size());
            assertNotNull(imported.flagIconBytesByIndex().get(1));
            assertNotNull(imported.flagIconBytesByIndex().get(2));
            assertNotNull(imported.flagIconBytesByIndex().get(3));
            assertEquals(List.of(new TreatyVisRuntimeInput.AllianceFlag(100, 1)), imported.input().initialFlags());
        }
    }

    private static byte[] createAtlasWebpBytes(List<Color> colors, int tileWidth, int tileHeight) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(colors.size() * tileWidth, tileHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < colors.size(); index += 1) {
            Color color = colors.get(index);
            for (int y = 0; y < tileHeight; y += 1) {
                for (int x = 0; x < tileWidth; x += 1) {
                    image.setRGB((index * tileWidth) + x, y, color.getRGB());
                }
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "webp", output);
        return output.toByteArray();
    }
}
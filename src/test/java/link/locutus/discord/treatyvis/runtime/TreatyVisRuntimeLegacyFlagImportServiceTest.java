package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeLegacyFlagImportServiceTest {
    private final ObjectMapper msgpack = new ObjectMapper(new MessagePackFactory());
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void importsHistoricalFlagChangesAndRawCache() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        byte[] alphaRaw = createPngBytes(new Color(200, 10, 10));
        byte[] betaRaw = createPngBytes(new Color(10, 10, 200));
        String alphaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).toString();
        String betaRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).toString();
        String alphaNormalizedHash = repeatHex("a", 64);
        String betaNormalizedHash = repeatHex("b", 64);

        String alphaCacheFile = "alpha-cache.png";
        String betaCacheFile = "beta-cache.png";
        Files.write(stagedData.resolve(Path.of("flag_cache", alphaCacheFile)), alphaRaw);
        Files.write(stagedData.resolve(Path.of("flag_cache", betaCacheFile)), betaRaw);

        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 100, "raw_flag_url", "https://flags.test/alpha.png"),
                        Map.of("timestamp", "2025-01-02T00:00:00Z", "alliance_id", 100, "raw_flag_url", "https://flags.test/beta.png"),
                        Map.of("timestamp", "2025-01-02T00:00:00Z", "alliance_id", 200, "raw_flag_url", "")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/alpha.png", alphaNormalizedHash,
                        "https://flags.test/beta.png", betaNormalizedHash
                )
        ));

        Map<String, Object> alphaState = new LinkedHashMap<>();
        alphaState.put("cache_file", alphaCacheFile);
        alphaState.put("content_sha256", alphaRawHash);
        alphaState.put("attempts", 2);
        alphaState.put("http_status", 200);
        alphaState.put("last_error", "");
        alphaState.put("status", "downloaded");
        alphaState.put("updated_at", "2026-02-25T02:10:20Z");
        Map<String, Object> betaState = new LinkedHashMap<>();
        betaState.put("cache_file", betaCacheFile);
        betaState.put("content_sha256", betaRawHash);
        betaState.put("attempts", 1);
        betaState.put("http_status", 200);
        betaState.put("last_error", "");
        betaState.put("status", "downloaded");
        betaState.put("updated_at", "2026-02-24T20:42:05Z");
        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "created_at", "2026-02-24T20:41:59Z",
                "schema_version", 2,
                "updated_at", "2026-03-01T07:07:08Z",
                "urls", Map.of(
                        "https://flags.test/alpha.png", alphaState,
                        "https://flags.test/beta.png", betaState
                )
        ));

        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of(
                        "columns", 16,
                        "tile_width", 16,
                        "tile_height", 10,
                        "rows", 1,
                        "width", 256,
                        "height", 10,
                        "count", 2,
                        "webp", "flag_atlas.webp",
                        "png", "flag_atlas.png"
                ),
                "assets", Map.of(
                        "f0", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash),
                        "f1", Map.of("x", 16, "y", 0, "w", 16, "h", 10, "hash", betaNormalizedHash)
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime", "flag_cache"));
            TreatyVisRuntimeLegacyFlagImportService service = new TreatyVisRuntimeLegacyFlagImportService(
                    stagedImportRoot,
                    runtimeFlagCacheRoot,
                    repository,
                    msgpack,
                    json
            );

            TreatyVisRuntimeLegacyFlagImportService.FlagImportResult result = service.importHistoricalFlags(false);

            assertEquals(3, result.importedChangeCount());
            assertEquals(2, result.importedIconCount());
            assertEquals(2, result.importedAtlasAssignmentCount());
            assertEquals(2, result.importedLastFlagUrlCount());
            assertEquals(2, result.copiedRawCacheCount());
            assertTrue(Files.isRegularFile(runtimeFlagCacheRoot.resolve(alphaRawHash)));
            assertTrue(Files.isRegularFile(runtimeFlagCacheRoot.resolve(betaRawHash)));

            TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
            assertTrue(bootstrapState.flagImportComplete());
            assertEquals(Math.toIntExact(java.time.LocalDate.parse("2025-01-01").toEpochDay()), bootstrapState.importedFlagMinDay());
            assertEquals(Math.toIntExact(java.time.LocalDate.parse("2025-01-02").toEpochDay()), bootstrapState.importedFlagMaxDay());

            Map<Integer, byte[]> lastFlagHashes = repository.loadLastFlagHashesByAlliance();
            assertEquals(betaNormalizedHash, TreatyVisRuntimeFlagAssetUtil.hashCode(lastFlagHashes.get(100)).toString());
            assertEquals(null, lastFlagHashes.get(200));

            TreatyVisRuntimeLegacyFlagImportService.FlagResetResult resetResult = service.resetImportedHistoricalFlags();
            assertEquals(3, resetResult.deletedChangeCount());
            assertEquals(2, resetResult.deletedRawCacheFileCount());
            assertFalse(Files.exists(runtimeFlagCacheRoot));
        }
    }

    @Test
    void importsHistoricalFlagsFromAtlasWhenRawDownloadIsMissing() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-atlas-only");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        String alphaNormalizedHash = repeatHex("c", 64);

        msgpack.writeValue(stagedData.resolve("flags_day_cache.msgpack").toFile(), Map.of(
                "raw_events_after_legacy", List.of(
                        Map.of("timestamp", "2025-01-01T00:00:00Z", "alliance_id", 100, "raw_flag_url", "https://flags.test/missing.png")
                ),
                "url_to_hash", Map.of(
                        "https://flags.test/missing.png", alphaNormalizedHash
                )
        ));

        json.writeValue(stagedData.resolve("flag_download_state.json").toFile(), Map.of(
                "created_at", "2026-02-24T20:41:59Z",
                "schema_version", 2,
                "updated_at", "2026-03-01T07:07:08Z",
                "urls", Map.of()
        ));

        Files.write(stagedPublicData.resolve("flag_atlas.webp"), createAtlasWebpBytes(
                List.of(new Color(50, 200, 50)),
                16,
                10
        ));
        msgpack.writeValue(stagedPublicData.resolve("flag_assets.msgpack").toFile(), Map.of(
                "atlas", Map.of(
                        "columns", 1,
                        "tile_width", 16,
                        "tile_height", 10,
                        "rows", 1,
                        "width", 16,
                        "height", 10,
                        "count", 1,
                        "webp", "/data/flag_atlas.webp"
                ),
                "assets", Map.of(
                        "f0", Map.of("x", 0, "y", 0, "w", 16, "h", 10, "hash", alphaNormalizedHash)
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-atlas-only.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime-atlas-only", "flag_cache"));
            TreatyVisRuntimeLegacyFlagImportService service = new TreatyVisRuntimeLegacyFlagImportService(
                    stagedImportRoot,
                    runtimeFlagCacheRoot,
                    repository,
                    msgpack,
                    json
            );

            TreatyVisRuntimeLegacyFlagImportService.FlagImportResult result = service.importHistoricalFlags(false);

            assertEquals(1, result.importedChangeCount());
            assertEquals(1, result.importedIconCount());
            assertEquals(1, result.importedAtlasAssignmentCount());
            assertEquals(1, result.importedLastFlagUrlCount());
            assertEquals(0, result.copiedRawCacheCount());
            assertFalse(Files.exists(runtimeFlagCacheRoot));

            Map<Integer, byte[]> lastFlagHashes = repository.loadLastFlagHashesByAlliance();
            assertEquals(alphaNormalizedHash, TreatyVisRuntimeFlagAssetUtil.hashCode(lastFlagHashes.get(100)).toString());
        }
    }

    @Test
    void importsFullHistoricalAtlasCatalogEvenWhenOnlySubsetOfFlagsAppearInEvents() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-full-atlas-catalog");
        Path stagedData = stagedImportRoot.resolve("data");
        Path stagedPublicData = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(stagedData.resolve("flag_cache"));
        Files.createDirectories(stagedPublicData);

        String alphaNormalizedHash = repeatHex("1", 64);
        String betaNormalizedHash = repeatHex("2", 64);
        String gammaNormalizedHash = repeatHex("3", 64);

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

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-full-atlas-catalog.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime-full-atlas-catalog", "flag_cache"));
            TreatyVisRuntimeLegacyFlagImportService service = new TreatyVisRuntimeLegacyFlagImportService(
                    stagedImportRoot,
                    runtimeFlagCacheRoot,
                    repository,
                    msgpack,
                    json
            );

            TreatyVisRuntimeLegacyFlagImportService.FlagImportResult result = service.importHistoricalFlags(false);

            assertEquals(1, result.importedChangeCount());
            assertEquals(3, result.importedIconCount());
            assertEquals(3, result.importedAtlasAssignmentCount());
            assertEquals(1, result.importedLastFlagUrlCount());
            assertEquals(0, result.copiedRawCacheCount());
        }
    }

        @Test
        void copiedAtlasTileRemainsVisibleAfterWebpRoundTrip() throws Exception {
                byte[] atlasBytes = createAtlasWebpBytes(
                                List.of(new Color(50, 200, 50), new Color(200, 50, 50), new Color(50, 50, 200)),
                                16,
                                10
                );

                BufferedImage decodedAtlas = ImageIO.read(new java.io.ByteArrayInputStream(atlasBytes));
                byte[] iconBytes = TreatyVisRuntimeFlagAssetUtil.encodeAtlasTileIcon(decodedAtlas, 16, 0, 16, 10);
                BufferedImage decodedIcon = TreatyVisRuntimeFlagAssetUtil.decodeValidatedImage(iconBytes);

                assertTrue(hasVisiblePixels(decodedIcon));
        }

    private static byte[] createPngBytes(Color color) throws IOException {
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

        private static byte[] createAtlasWebpBytes(List<Color> colors, int tileWidth, int tileHeight) throws IOException {
                BufferedImage image = new BufferedImage(colors.size() * tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB);
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

    private static String repeatHex(String value, int length) {
        return value.repeat(length);
    }

        private static boolean hasVisiblePixels(BufferedImage image) {
                for (int y = 0; y < image.getHeight(); y += 1) {
                        for (int x = 0; x < image.getWidth(); x += 1) {
                                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                                        return true;
                                }
                        }
                }
                return false;
        }
}
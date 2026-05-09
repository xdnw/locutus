package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeBootstrapFlagStateServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void bootstrapsLiveFlagHelperStateAndExtendsAtlasForNewHashes() throws Exception {
        byte[] alphaRaw = createPngBytes(new Color(220, 20, 20));
        byte[] betaRaw = createPngBytes(new Color(20, 20, 220));
        byte[] gammaRaw = createPngBytes(new Color(20, 220, 20));
                byte[] alphaHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).asBytes();
                byte[] betaHash = TreatyVisRuntimeFlagAssetUtil.sha256(betaRaw).asBytes();
                com.google.common.hash.HashCode gammaHash = TreatyVisRuntimeFlagAssetUtil.sha256(gammaRaw);

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.replaceFlagChanges(List.of(
                    new TreatyVisRuntimeRepository.FlagChangeRow(100, Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), alphaHash),
                    new TreatyVisRuntimeRepository.FlagChangeRow(200, Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), betaHash)
            ));
            repository.replaceFlagIcons(List.of(
                    new TreatyVisRuntimeRepository.FlagIconRow(alphaHash, TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(alphaRaw)),
                    new TreatyVisRuntimeRepository.FlagIconRow(betaHash, TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(betaRaw))
            ));
            repository.replaceFlagAtlasState(List.of(
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(alphaHash, 1),
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(betaHash, 2)
            ));
            repository.replaceLastFlagUrls(List.of(
                    new TreatyVisRuntimeRepository.LastFlagUrlRow(100, "https://flags.test/alpha.png", alphaHash),
                    new TreatyVisRuntimeRepository.LastFlagUrlRow(200, "https://flags.test/beta.png", betaHash)
            ));
            repository.markHistoricalFlagImportComplete(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()),
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay())
            );

            Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime", "flag_cache"));
            TreatyVisRuntimeBootstrapFlagStateService service = new TreatyVisRuntimeBootstrapFlagStateService(
                    repository,
                    runtimeFlagCacheRoot,
                    () -> List.of(
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(100, "https://flags.test/alpha.png"),
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(200, ""),
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(300, "https://flags.test/gamma.png")
                    ),
                                        flagUrl -> switch (flagUrl) {
                        case "https://flags.test/gamma.png" -> gammaRaw;
                        default -> throw new IllegalArgumentException("Unexpected flag url: " + flagUrl);
                    }
            );

            TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult result = service.bootstrapCurrentFlags();

            assertEquals(3, result.helperAllianceCount());
            assertEquals(2, result.changedAllianceCount());
            assertEquals(1, result.newIconCount());

            Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> lastRows = repository.loadLastFlagUrlRows();
            assertEquals("https://flags.test/alpha.png", lastRows.get(100).flagUrl());
            assertEquals(null, lastRows.get(200).flagUrl());
            assertEquals("https://flags.test/gamma.png", lastRows.get(300).flagUrl());
            assertEquals(gammaHash, TreatyVisRuntimeFlagAssetUtil.hashCode(lastRows.get(300).flagHash()));

            List<TreatyVisRuntimeRepository.FlagChangeRow> changeRows = repository.loadFlagChangeRows();
            List<TreatyVisRuntimeRepository.FlagChangeRow> bootstrapRows = changeRows.stream()
                    .filter(row -> row.day() == result.bootstrapDay())
                    .toList();
            assertEquals(2, bootstrapRows.size());
            assertEquals(200, bootstrapRows.get(0).allianceId());
            assertEquals(null, bootstrapRows.get(0).flagHash());
            assertEquals(300, bootstrapRows.get(1).allianceId());
            assertEquals(gammaHash, TreatyVisRuntimeFlagAssetUtil.hashCode(bootstrapRows.get(1).flagHash()));

            Map<com.google.common.hash.HashCode, Integer> tileIndexByHash = repository.loadFlagAtlasStateRows().stream()
                    .collect(Collectors.toMap(
                            row -> TreatyVisRuntimeFlagAssetUtil.hashCode(row.flagHash()),
                            TreatyVisRuntimeRepository.FlagAtlasStateRow::tileIndex
                    ));
            assertEquals(3, tileIndexByHash.get(gammaHash));
            assertTrue(Files.isRegularFile(runtimeFlagCacheRoot.resolve(gammaHash.toString())));
            assertNotNull(repository.loadLastFlagHashesByAlliance().get(300));
        }
    }

    @Test
    void keepsPreviousOrNoFlagWhenLiveFetchFails() throws Exception {
        byte[] alphaRaw = createPngBytes(new Color(220, 20, 20));
        byte[] alphaHash = TreatyVisRuntimeFlagAssetUtil.sha256(alphaRaw).asBytes();

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-fetch-fail.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.replaceFlagChanges(List.of(
                    new TreatyVisRuntimeRepository.FlagChangeRow(100, Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), alphaHash)
            ));
            repository.replaceFlagIcons(List.of(
                    new TreatyVisRuntimeRepository.FlagIconRow(alphaHash, TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(alphaRaw))
            ));
            repository.replaceFlagAtlasState(List.of(
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(alphaHash, 1)
            ));
            repository.replaceLastFlagUrls(List.of(
                    new TreatyVisRuntimeRepository.LastFlagUrlRow(100, "https://flags.test/alpha.png", alphaHash)
            ));
            repository.markHistoricalFlagImportComplete(
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()),
                    Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay())
            );

            Path runtimeFlagCacheRoot = tempDir.resolve(Path.of("runtime-fetch-fail", "flag_cache"));
            TreatyVisRuntimeBootstrapFlagStateService service = new TreatyVisRuntimeBootstrapFlagStateService(
                    repository,
                    runtimeFlagCacheRoot,
                    () -> List.of(
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(100, "https://flags.test/failing.jpg"),
                            new TreatyVisRuntimeBootstrapFlagStateService.LiveAllianceFlag(200, "https://flags.test/failing.jpg")
                    ),
                    flagUrl -> {
                        throw new IOException("403");
                    }
            );

            TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult result = service.bootstrapCurrentFlags();

            assertEquals(2, result.helperAllianceCount());
            assertEquals(0, result.changedAllianceCount());
            assertEquals(0, result.newIconCount());

            Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> lastRows = repository.loadLastFlagUrlRows();
            assertEquals("https://flags.test/failing.jpg", lastRows.get(100).flagUrl());
            assertEquals(TreatyVisRuntimeFlagAssetUtil.hashCode(alphaHash), TreatyVisRuntimeFlagAssetUtil.hashCode(lastRows.get(100).flagHash()));
            assertEquals("https://flags.test/failing.jpg", lastRows.get(200).flagUrl());
            assertEquals(null, lastRows.get(200).flagHash());
            assertTrue(repository.loadFlagChangeRows().stream().noneMatch(row -> row.day() == result.bootstrapDay()));
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
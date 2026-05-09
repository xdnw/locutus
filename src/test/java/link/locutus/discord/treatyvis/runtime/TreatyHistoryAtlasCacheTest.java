package link.locutus.discord.treatyvis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyHistoryAtlasCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void reusesCachedAtlasBytesWhenStateMatches() throws Exception {
        Path atlasPath = tempDir.resolve("treaty_atlas.webp");
        Path statePath = tempDir.resolve("treaty_atlas.sha256");
        TreatyHistoryAtlasCache cache = new TreatyHistoryAtlasCache(atlasPath, statePath);
        TreatyHistoryAtlasCache.TreatyHistoryAtlasSource source = new TreatyHistoryAtlasCache.TreatyHistoryAtlasSource(
            Map.of(1, new byte[] {1, 2, 3, 4})
        );

        AtomicInteger buildCount = new AtomicInteger();
        byte[] first = cache.loadOrBuild(source, () -> {
            buildCount.incrementAndGet();
            BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, Color.RED.getRGB());
            return image;
        });
        byte[] second = cache.loadOrBuild(source, () -> {
            buildCount.incrementAndGet();
            BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, Color.BLUE.getRGB());
            return image;
        });

        assertEquals(1, buildCount.get());
        assertArrayEquals(first, second);
        assertTrue(Files.isRegularFile(atlasPath));
        assertTrue(Files.isRegularFile(statePath));
        assertEquals(64, Files.readString(statePath, StandardCharsets.UTF_8).trim().length());
    }
}

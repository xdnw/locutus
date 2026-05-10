package link.locutus.discord.treatyvis.runtime;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeFlagAssetUtilTest {
    @Test
    void rejectsOversizedImageDimensionsDuringNormalization() throws Exception {
        byte[] rawBytes = createPngBytes(TreatyHistoryRuntimeConfig.FLAG_IMAGE_MAX_WIDTH + 1, 1, new Color(200, 20, 20));

        assertThrows(IOException.class, () -> TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(rawBytes));
    }

    @Test
    void normalizedRawIconRemainsVisibleAfterRoundTrip() throws Exception {
        byte[] rawBytes = createPngBytes(8, 6, new Color(20, 120, 220));

        byte[] iconBytes = TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(rawBytes);
        BufferedImage decodedIcon = TreatyVisRuntimeFlagAssetUtil.decodeValidatedImage(iconBytes);

        assertTrue(hasVisiblePixels(decodedIcon));
    }

    private static byte[] createPngBytes(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y += 1) {
            for (int x = 0; x < image.getWidth(); x += 1) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
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
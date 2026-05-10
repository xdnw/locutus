package link.locutus.discord.treatyvis.runtime;

import com.google.common.hash.HashCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TreatyVisRuntimeFlagAssetUtil {
    private TreatyVisRuntimeFlagAssetUtil() {
    }

    static byte[] downloadRawBytes(String flagUrl) throws IOException {
        URI uri = URI.create(flagUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("Flag download URL must use http or https: " + flagUrl);
        }

        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(TreatyHistoryRuntimeConfig.FLAG_DOWNLOAD_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(TreatyHistoryRuntimeConfig.FLAG_DOWNLOAD_READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "image/webp,image/png,image/*;q=0.8,*/*;q=0.1");

        String contentType = connection.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.toLowerCase().startsWith("image/")) {
            throw new IOException("Flag download content type is not an image: " + contentType);
        }

        int contentLength = connection.getContentLength();
        if (contentLength > TreatyHistoryRuntimeConfig.FLAG_DOWNLOAD_MAX_BYTES) {
            throw new IOException("Flag download exceeds byte limit: " + contentLength);
        }

        try (InputStream input = connection.getInputStream()) {
            return readBoundedBytes(input, TreatyHistoryRuntimeConfig.FLAG_DOWNLOAD_MAX_BYTES);
        }
    }

    static byte[] encodeNormalizedIcon(byte[] rawBytes) throws IOException {
        BufferedImage source = decodeValidatedImage(rawBytes);
        BufferedImage icon = renderNormalizedIcon(source);
        return encodeWebp(icon, TreatyHistoryRuntimeConfig.FLAG_ICON_WEBP_QUALITY);
    }

    static byte[] encodeAtlasTileIcon(BufferedImage atlasImage, int x, int y, int width, int height) throws IOException {
        return encodeWebp(copyTile(atlasImage, x, y, width, height), TreatyHistoryRuntimeConfig.FLAG_ICON_WEBP_QUALITY);
    }

    static BufferedImage decodeValidatedImage(byte[] rawBytes) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (imageInput == null) {
                throw new IOException("Unable to open image input stream for flag bytes");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException("Unable to decode raw flag image bytes");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateImageDimensions(width, height);
                ImageReadParam readParam = reader.getDefaultReadParam();
                BufferedImage image = reader.read(0, readParam);
                if (image == null) {
                    throw new IOException("Unable to decode raw flag image bytes");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    static BufferedImage renderNormalizedIcon(BufferedImage source) {
        BufferedImage icon = new BufferedImage(
                TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_WIDTH,
                TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_HEIGHT,
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = icon.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, icon.getWidth(), icon.getHeight());
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            double scale = Math.min(icon.getWidth() / (double) source.getWidth(), icon.getHeight() / (double) source.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int drawX = Math.max(0, (icon.getWidth() - drawWidth) / 2);
            int drawY = Math.max(0, (icon.getHeight() - drawHeight) / 2);
            graphics.drawImage(source, drawX, drawY, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return icon;
    }

    static BufferedImage copyTile(BufferedImage source, int x, int y, int width, int height) {
        BufferedImage tile = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        } finally {
            graphics.dispose();
        }
        return tile;
    }

    static byte[] encodeWebp(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").hasNext()
                ? ImageIO.getImageWritersByMIMEType("image/webp").next()
                : null;
        if (writer == null) {
            throw new IOException("No WebP ImageIO writer is available on the runtime classpath.");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] compressionTypes = writeParam.getCompressionTypes();
                if (compressionTypes != null && compressionTypes.length > 0) {
                    writeParam.setCompressionType(compressionTypes[0]);
                }
                writeParam.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    static Map<HashCode, Integer> buildDenseTileIndexByHash(List<TreatyVisRuntimeRepository.FlagAtlasStateRow> atlasRows) throws IOException {
        Map<HashCode, Integer> flagIndexByHash = new LinkedHashMap<>();
        int nextTileIndex = 1;
        for (TreatyVisRuntimeRepository.FlagAtlasStateRow row : atlasRows.stream()
                .sorted(java.util.Comparator.comparingInt(TreatyVisRuntimeRepository.FlagAtlasStateRow::tileIndex))
                .toList()) {
            if (row.tileIndex() <= 0) {
                throw new IOException("Flag atlas tile indexes must be positive for stored hashes.");
            }
            Integer previousIndex = flagIndexByHash.putIfAbsent(hashCode(row.flagHash()), nextTileIndex);
            if (previousIndex != null && previousIndex != nextTileIndex) {
                throw new IOException("Flag atlas hash mapped to multiple tile indexes.");
            }
            if (previousIndex == null) {
                nextTileIndex += 1;
            }
        }
        return Map.copyOf(flagIndexByHash);
    }

    static Map<Integer, byte[]> buildIconBytesByIndex(
            Map<HashCode, Integer> flagIndexByHash,
            List<TreatyVisRuntimeRepository.FlagIconRow> iconRows
    ) {
        Map<Integer, byte[]> iconsByIndex = new LinkedHashMap<>();
        for (TreatyVisRuntimeRepository.FlagIconRow row : iconRows) {
            Integer tileIndex = flagIndexByHash.get(hashCode(row.flagHash()));
            if (tileIndex != null) {
                iconsByIndex.put(tileIndex, row.iconBytes());
            }
        }
        return Map.copyOf(iconsByIndex);
    }

    static HashCode hashCode(byte[] bytes) {
        return HashCode.fromBytes(bytes);
    }

    static HashCode sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HashCode.fromBytes(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static byte[] readBoundedBytes(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Flag download exceeds byte limit while streaming.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateImageDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("Flag image dimensions must be positive.");
        }
        if (width > TreatyHistoryRuntimeConfig.FLAG_IMAGE_MAX_WIDTH || height > TreatyHistoryRuntimeConfig.FLAG_IMAGE_MAX_HEIGHT) {
            throw new IOException("Flag image dimensions exceed configured limit: " + width + "x" + height);
        }
        long pixelCount = (long) width * (long) height;
        if (pixelCount > TreatyHistoryRuntimeConfig.FLAG_IMAGE_MAX_PIXELS) {
            throw new IOException("Flag image pixel count exceeds configured limit: " + pixelCount);
        }
    }
}
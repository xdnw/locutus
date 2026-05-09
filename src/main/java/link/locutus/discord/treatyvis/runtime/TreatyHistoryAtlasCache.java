package link.locutus.discord.treatyvis.runtime;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class TreatyHistoryAtlasCache {
    private final Path atlasPath;
    private final Path statePath;

    TreatyHistoryAtlasCache(Path atlasPath, Path statePath) {
        this.atlasPath = atlasPath.toAbsolutePath().normalize();
        this.statePath = statePath.toAbsolutePath().normalize();
    }

    byte[] loadOrBuild(TreatyHistoryAtlasSource source, Supplier<BufferedImage> atlasBuilder) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(atlasBuilder, "atlasBuilder");

        String expectedState = computeStateHash(source);
        if (Files.isRegularFile(atlasPath) && Files.isRegularFile(statePath)) {
            String actualState = Files.readString(statePath, StandardCharsets.UTF_8).trim();
            if (expectedState.equals(actualState)) {
                return Files.readAllBytes(atlasPath);
            }
        }

        byte[] atlasBytes = TreatyVisRuntimeFlagAssetUtil.encodeWebp(atlasBuilder.get(), TreatyHistoryRuntimeConfig.FLAG_ATLAS_WEBP_QUALITY);
        writeAtomically(atlasPath, atlasBytes);
        writeAtomically(statePath, expectedState.getBytes(StandardCharsets.UTF_8));
        return atlasBytes;
    }

    private static String computeStateHash(TreatyHistoryAtlasSource source) throws IOException {
        MessageDigest digest = sha256();
        updateInt(digest, TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_WIDTH);
        updateInt(digest, TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_HEIGHT);
        updateInt(digest, TreatyHistoryRuntimeConfig.FLAG_ATLAS_COLUMNS);

        source.flagIconBytesByIndex().forEach((index, bytes) -> {
            digest.update((byte) 1);
            updateInt(digest, index);
            updateBytes(digest, bytes);
        });

        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeAtomically(Path targetPath, byte[] bytes) throws IOException {
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        Files.write(tempPath, bytes);
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 digest is unavailable.", ex);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        updateInt(digest, safeBytes.length);
        digest.update(safeBytes);
    }

    record TreatyHistoryAtlasSource(Map<Integer, byte[]> flagIconBytesByIndex) {
        TreatyHistoryAtlasSource {
            flagIconBytesByIndex = normalizeMap(flagIconBytesByIndex);
        }

        private static <T> Map<Integer, T> normalizeMap(Map<Integer, T> input) {
            return input.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        }
    }
}

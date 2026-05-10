package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import link.locutus.discord.Locutus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class TreatyVisRuntimeLegacyFlagImportService {
    private static final String FLAG_CACHE_DIRECTORY = "data/flag_cache";

    private final Path stagedImportRoot;
    private final Path runtimeFlagCacheRoot;
    private final TreatyVisRuntimeRepository repository;
    private final ObjectMapper msgpack;
    private final ObjectMapper json;

    public TreatyVisRuntimeLegacyFlagImportService() {
        this(
                TreatyVisRuntimeBootstrapService.DEFAULT_IMPORT_ROOT,
                Path.of(FLAG_CACHE_DIRECTORY),
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
            TreatyVisRuntimeSerializers.MSGPACK,
            TreatyVisRuntimeSerializers.JSON
        );
    }

    TreatyVisRuntimeLegacyFlagImportService(
            Path stagedImportRoot,
            Path runtimeFlagCacheRoot,
            TreatyVisRuntimeRepository repository,
            ObjectMapper msgpack,
            ObjectMapper json
    ) {
        this.stagedImportRoot = stagedImportRoot.toAbsolutePath().normalize();
        this.runtimeFlagCacheRoot = runtimeFlagCacheRoot.toAbsolutePath().normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.msgpack = Objects.requireNonNull(msgpack, "msgpack");
        this.json = Objects.requireNonNull(json, "json");
    }

    public void assertImportTargetAvailable(boolean replaceExisting) throws IOException {
        repository.ensureTables();
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        if (!replaceExisting && (bootstrapState.flagImportComplete() || repository.countFlagChanges() > 0 || Files.exists(runtimeFlagCacheRoot))) {
            throw new IOException("Historical flag import already exists. Run the runtime bootstrap reset first or rerun with replace enabled.");
        }
    }

    public FlagImportResult importHistoricalFlags(boolean replaceExisting) throws IOException {
        assertImportTargetAvailable(replaceExisting);
        if (replaceExisting) {
            resetImportedHistoricalFlags();
        }

        repository.ensureTables();
        FlagsDayCache cache = loadFlagsDayCache();
        DownloadState downloadState = loadDownloadState();
        Map<String, DownloadedFlag> downloadedFlagsByUrl = buildDownloadedFlagsByUrl(cache.urlToHash(), downloadState.urls());

        AssetPayload assetPayload = loadAssetPayload();
        Map<HashCode, Integer> tileIndexByNormalizedHash = buildTileIndexByNormalizedHash(assetPayload);
        Map<HashCode, byte[]> iconBytesByNormalizedHash = loadNormalizedIconBytesByHash(assetPayload);
        if (downloadedFlagsByUrl.isEmpty() && iconBytesByNormalizedHash.isEmpty()) {
            throw new IOException("No staged historical flag assets were found under " + stagedImportRoot);
        }

        copyRawFlagCache(downloadedFlagsByUrl.values());
        FlagImportRows importRows = buildImportRows(cache, downloadedFlagsByUrl, tileIndexByNormalizedHash, iconBytesByNormalizedHash);

        repository.replaceFlagChanges(importRows.flagChanges());
        repository.replaceFlagIcons(importRows.flagIcons());
        repository.replaceFlagAtlasState(importRows.flagAtlasState());
        repository.replaceLastFlagUrls(importRows.lastFlagUrls());
        repository.markHistoricalFlagImportComplete(importRows.minDay(), importRows.maxDay());

        return new FlagImportResult(
                importRows.flagChanges().size(),
                importRows.flagIcons().size(),
                importRows.flagAtlasState().size(),
                importRows.lastFlagUrls().size(),
                importRows.copiedRawCacheCount(),
                importRows.minDay(),
                importRows.maxDay()
        );
    }

    public FlagResetResult resetImportedHistoricalFlags() throws IOException {
        int deletedFlagChangeCount = repository.clearImportedFlagHistory();
        int deletedRawCacheFileCount = deleteRuntimeFlagCache();
        return new FlagResetResult(deletedFlagChangeCount, deletedRawCacheFileCount);
    }

    FlagValidationData loadHistoricalFlagValidationData() throws IOException {
        repository.ensureTables();
        FlagsDayCache cache = loadFlagsDayCache();
        DownloadState downloadState = loadDownloadState();
        Map<String, DownloadedFlag> downloadedFlagsByUrl = buildDownloadedFlagsByUrl(cache.urlToHash(), downloadState.urls());
        AssetPayload assetPayload = loadAssetPayload();
        Map<HashCode, Integer> tileIndexByNormalizedHash = buildTileIndexByNormalizedHash(assetPayload);
        Map<HashCode, byte[]> iconBytesByNormalizedHash = loadNormalizedIconBytesByHash(assetPayload);

        Map<Integer, byte[]> latestHashByAlliance = new LinkedHashMap<>();
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;
        int resolvedEventCount = 0;
        for (RawFlagEvent event : cache.rawEvents()) {
            int epochDay = parseEpochDay(event.timestamp());
            byte[] flagHash = null;
            if (!event.rawFlagUrl().isBlank()) {
                ResolvedHistoricalFlag resolvedFlag = resolveHistoricalFlag(
                        event.rawFlagUrl(),
                        cache,
                        downloadedFlagsByUrl,
                        tileIndexByNormalizedHash,
                        iconBytesByNormalizedHash
                );
                if (resolvedFlag == null) {
                    continue;
                }
                flagHash = resolvedFlag.storedHash().asBytes();
            }
            latestHashByAlliance.put(event.allianceId(), flagHash);
            minDay = Math.min(minDay, epochDay);
            maxDay = Math.max(maxDay, epochDay);
            resolvedEventCount += 1;
        }

        Map<String, HashCode> rawHashByUrl = new LinkedHashMap<>();
        for (DownloadedFlag downloadedFlag : downloadedFlagsByUrl.values()) {
            rawHashByUrl.put(downloadedFlag.url(), downloadedFlag.rawHash());
        }

        return new FlagValidationData(
            resolvedEventCount,
            resolvedEventCount == 0 ? null : minDay,
            resolvedEventCount == 0 ? null : maxDay,
                iconBytesByNormalizedHash.size(),
                tileIndexByNormalizedHash.size(),
                Map.copyOf(rawHashByUrl),
                Map.copyOf(latestHashByAlliance)
        );
    }

    private FlagsDayCache loadFlagsDayCache() throws IOException {
        Path cachePath = stagedImportRoot.resolve(Path.of("data", "flags_day_cache.msgpack"));
        if (!Files.isRegularFile(cachePath)) {
            throw new IOException("Staged flags day cache is missing: " + cachePath);
        }
        Map<String, Object> payload = msgpack.readValue(cachePath.toFile(), new TypeReference<>() {
        });
        Object rawEvents = payload.get("raw_events_after_legacy");
        Object urlToHash = payload.get("url_to_hash");
        if (!(rawEvents instanceof List<?>) || !(urlToHash instanceof Map<?, ?>)) {
            throw new IOException("Staged flags day cache is missing raw_events_after_legacy or url_to_hash: " + cachePath);
        }

        List<RawFlagEvent> events = new ArrayList<>();
        for (Object rawEvent : (List<?>) rawEvents) {
            if (!(rawEvent instanceof Map<?, ?> row)) {
                continue;
            }
            int allianceId = intValue(row.get("alliance_id"));
            String timestamp = stringValue(row.get("timestamp"));
            String rawFlagUrl = stringValue(row.get("raw_flag_url"));
            if (allianceId <= 0 || timestamp.isBlank()) {
                continue;
            }
            events.add(new RawFlagEvent(allianceId, timestamp, rawFlagUrl));
        }
        events.sort(Comparator.comparing(RawFlagEvent::timestamp).thenComparingInt(RawFlagEvent::allianceId));

        Map<String, HashCode> normalizedHashByUrl = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) urlToHash).entrySet()) {
            String url = stringValue(entry.getKey());
            HashCode normalizedHash = parseHashCode(stringValue(entry.getValue()));
            if (!url.isBlank() && normalizedHash != null) {
                normalizedHashByUrl.put(url, normalizedHash);
            }
        }

        return new FlagsDayCache(events, normalizedHashByUrl);
    }

    private DownloadState loadDownloadState() throws IOException {
        Path statePath = stagedImportRoot.resolve(Path.of("data", "flag_download_state.json"));
        if (!Files.isRegularFile(statePath)) {
            throw new IOException("Staged flag download state is missing: " + statePath);
        }

        DownloadState state = json.readValue(statePath.toFile(), DownloadState.class);
        if (state == null || state.urls() == null) {
            throw new IOException("Unable to decode staged flag download state: " + statePath);
        }
        return state;
    }

    private AssetPayload loadAssetPayload() throws IOException {
        Path assetsPath = stagedImportRoot.resolve(Path.of("public", "data", "flag_assets.msgpack"));
        if (!Files.isRegularFile(assetsPath)) {
            throw new IOException("Staged flag assets payload is missing: " + assetsPath);
        }
        AssetPayload payload = msgpack.readValue(assetsPath.toFile(), AssetPayload.class);
        if (payload == null || payload.assets() == null) {
            throw new IOException("Unable to decode staged flag assets payload: " + assetsPath);
        }
        return payload;
    }

    private Map<String, DownloadedFlag> buildDownloadedFlagsByUrl(Map<String, HashCode> normalizedHashByUrl, Map<String, DownloadStateEntry> urlStates) throws IOException {
        Map<String, DownloadedFlag> downloadedFlagsByUrl = new LinkedHashMap<>();
        for (Map.Entry<String, HashCode> entry : normalizedHashByUrl.entrySet()) {
            String url = entry.getKey();
            HashCode normalizedHash = entry.getValue();
            DownloadStateEntry stateEntry = urlStates.get(url);
            if (stateEntry == null) {
                continue;
            }
            HashCode rawHash = parseHashCode(stateEntry.contentSha256());
            String cacheFile = stringValue(stateEntry.cacheFile());
            if (rawHash == null || cacheFile.isBlank()) {
                continue;
            }

            Path sourceCachePath = stagedImportRoot.resolve(Path.of("data", "flag_cache", cacheFile));
            if (!Files.isRegularFile(sourceCachePath)) {
                continue;
            }

            byte[] rawBytes = Files.readAllBytes(sourceCachePath);
            HashCode computedRawHash = TreatyVisRuntimeFlagAssetUtil.sha256(rawBytes);
            if (!computedRawHash.equals(rawHash)) {
                throw new IOException("Staged raw flag cache hash mismatch for " + sourceCachePath + ": expected " + rawHash + " but found " + computedRawHash);
            }

            downloadedFlagsByUrl.put(url, new DownloadedFlag(url, normalizedHash, rawHash, rawBytes));
        }
        return downloadedFlagsByUrl;
    }

    private void copyRawFlagCache(Iterable<DownloadedFlag> downloadedFlags) throws IOException {
        boolean createdDirectory = false;
        for (DownloadedFlag downloadedFlag : downloadedFlags) {
            if (!createdDirectory) {
                Files.createDirectories(runtimeFlagCacheRoot);
                createdDirectory = true;
            }
            Path targetPath = runtimeFlagCacheRoot.resolve(downloadedFlag.rawHash().toString());
            Files.write(targetPath, downloadedFlag.rawBytes());
        }
    }

    private FlagImportRows buildImportRows(
            FlagsDayCache cache,
            Map<String, DownloadedFlag> downloadedFlagsByUrl,
            Map<HashCode, Integer> tileIndexByNormalizedHash,
            Map<HashCode, byte[]> iconBytesByNormalizedHash
    ) throws IOException {
        Map<HashCode, TreatyVisRuntimeRepository.FlagIconRow> iconByHash = new LinkedHashMap<>();
        Map<HashCode, TreatyVisRuntimeRepository.FlagAtlasStateRow> atlasByHash = new LinkedHashMap<>();

        for (Map.Entry<HashCode, Integer> entry : tileIndexByNormalizedHash.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList()) {
            byte[] iconBytes = iconBytesByNormalizedHash.get(entry.getKey());
            if (iconBytes == null) {
                continue;
            }
            iconByHash.put(entry.getKey(), new TreatyVisRuntimeRepository.FlagIconRow(entry.getKey().asBytes(), iconBytes));
            atlasByHash.put(entry.getKey(), new TreatyVisRuntimeRepository.FlagAtlasStateRow(entry.getKey().asBytes(), entry.getValue()));
        }

        Map<AllianceDayKey, TreatyVisRuntimeRepository.FlagChangeRow> changeByAllianceAndDay = new LinkedHashMap<>();
        Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> latestByAlliance = new LinkedHashMap<>();
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;
        for (RawFlagEvent event : cache.rawEvents()) {
            int epochDay = parseEpochDay(event.timestamp());
            byte[] flagHash = null;
            String rawUrl = event.rawFlagUrl();
            if (!rawUrl.isBlank()) {
                ResolvedHistoricalFlag resolvedFlag = resolveHistoricalFlag(
                        rawUrl,
                        cache,
                        downloadedFlagsByUrl,
                        tileIndexByNormalizedHash,
                        iconBytesByNormalizedHash
                );
                if (resolvedFlag == null) {
                    continue;
                }
                iconByHash.putIfAbsent(resolvedFlag.storedHash(), new TreatyVisRuntimeRepository.FlagIconRow(resolvedFlag.storedHash().asBytes(), resolvedFlag.iconBytes()));
                atlasByHash.putIfAbsent(resolvedFlag.storedHash(), new TreatyVisRuntimeRepository.FlagAtlasStateRow(resolvedFlag.storedHash().asBytes(), resolvedFlag.tileIndex()));
                flagHash = resolvedFlag.storedHash().asBytes();
            }

            changeByAllianceAndDay.put(new AllianceDayKey(event.allianceId(), epochDay), new TreatyVisRuntimeRepository.FlagChangeRow(event.allianceId(), epochDay, flagHash));
            latestByAlliance.put(event.allianceId(), new TreatyVisRuntimeRepository.LastFlagUrlRow(event.allianceId(), rawUrl, flagHash));
            minDay = Math.min(minDay, epochDay);
            maxDay = Math.max(maxDay, epochDay);
        }

        if (changeByAllianceAndDay.isEmpty()) {
            throw new IOException("No staged historical flag events were found in flags_day_cache.msgpack under " + stagedImportRoot);
        }

        return new FlagImportRows(
                new ArrayList<>(changeByAllianceAndDay.values()),
            new ArrayList<>(iconByHash.values()),
            new ArrayList<>(atlasByHash.values()),
                new ArrayList<>(latestByAlliance.values()),
                downloadedFlagsByUrl.size(),
                minDay,
                maxDay
        );
    }

    private Map<HashCode, Integer> buildTileIndexByNormalizedHash(AssetPayload payload) {
        int columns = Math.max(1, payload.atlas() == null ? 1 : payload.atlas().columns());
        Map<HashCode, Integer> tileIndexByNormalizedHash = new HashMap<>();
        for (AssetPayloadEntry entry : payload.assets().values()) {
            HashCode normalizedHash = parseHashCode(entry.hash());
            if (normalizedHash == null) {
                continue;
            }
            int tileIndex = ((Math.max(0, entry.y()) / Math.max(1, entry.h())) * columns)
                    + (Math.max(0, entry.x()) / Math.max(1, entry.w()))
                    + 1;
            tileIndexByNormalizedHash.put(normalizedHash, tileIndex);
        }
        return tileIndexByNormalizedHash;
    }

    private Map<HashCode, byte[]> loadNormalizedIconBytesByHash(AssetPayload payload) throws IOException {
        Path atlasWebpPath = resolveAtlasWebpPath(payload);
        if (atlasWebpPath == null || !Files.isRegularFile(atlasWebpPath)) {
            return Map.of();
        }

        BufferedImage atlasImage = ImageIO.read(atlasWebpPath.toFile());
        if (atlasImage == null) {
            return Map.of();
        }

        Map<HashCode, byte[]> iconBytesByNormalizedHash = new LinkedHashMap<>();
        for (AssetPayloadEntry entry : payload.assets().values()) {
            HashCode normalizedHash = parseHashCode(entry.hash());
            if (normalizedHash == null) {
                continue;
            }
            int width = Math.max(1, entry.w());
            int height = Math.max(1, entry.h());
            int x = Math.max(0, entry.x());
            int y = Math.max(0, entry.y());
            if (x + width > atlasImage.getWidth() || y + height > atlasImage.getHeight()) {
                continue;
            }
            BufferedImage tile = atlasImage.getSubimage(x, y, width, height);
            iconBytesByNormalizedHash.put(normalizedHash, TreatyVisRuntimeFlagAssetUtil.encodeWebp(tile, TreatyHistoryRuntimeConfig.FLAG_ICON_WEBP_QUALITY));
        }
        return Map.copyOf(iconBytesByNormalizedHash);
    }

    private ResolvedHistoricalFlag resolveHistoricalFlag(
            String rawUrl,
            FlagsDayCache cache,
            Map<String, DownloadedFlag> downloadedFlagsByUrl,
            Map<HashCode, Integer> tileIndexByNormalizedHash,
            Map<HashCode, byte[]> iconBytesByNormalizedHash
    ) throws IOException {
        DownloadedFlag downloadedFlag = downloadedFlagsByUrl.get(rawUrl);
        if (downloadedFlag != null) {
            Integer tileIndex = tileIndexByNormalizedHash.get(downloadedFlag.normalizedHash());
            byte[] iconBytes = iconBytesByNormalizedHash.get(downloadedFlag.normalizedHash());
            if (tileIndex == null) {
                return null;
            }
            if (iconBytes == null) {
                iconBytes = uncheckedEncodeIcon(downloadedFlag.rawBytes());
            }
            return new ResolvedHistoricalFlag(downloadedFlag.normalizedHash(), iconBytes, tileIndex);
        }

        HashCode normalizedHash = cache.urlToHash().get(rawUrl);
        if (normalizedHash == null) {
            return null;
        }
        Integer tileIndex = tileIndexByNormalizedHash.get(normalizedHash);
        byte[] iconBytes = iconBytesByNormalizedHash.get(normalizedHash);
        if (tileIndex == null || iconBytes == null) {
            return null;
        }
        return new ResolvedHistoricalFlag(normalizedHash, iconBytes, tileIndex);
    }

    private Path resolveAtlasWebpPath(AssetPayload payload) {
        String atlasWebp = payload.atlas() == null ? "" : stringValue(payload.atlas().webp());
        if (!atlasWebp.isBlank()) {
            String normalized = atlasWebp.replace('\\', '/');
            if (normalized.startsWith("/data/")) {
                return stagedImportRoot.resolve(Path.of("public", normalized.substring(1)));
            }
        }
        return stagedImportRoot.resolve(Path.of("public", "data", "flag_atlas.webp"));
    }

    private int deleteRuntimeFlagCache() throws IOException {
        if (!Files.exists(runtimeFlagCacheRoot)) {
            return 0;
        }
        final int[] deletedFileCount = {0};
        Files.walkFileTree(runtimeFlagCacheRoot, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                deletedFileCount[0] += 1;
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return deletedFileCount[0];
    }

    private static int parseEpochDay(String timestamp) throws IOException {
        try {
            return Math.toIntExact(LocalDate.parse(timestamp.substring(0, 10)).toEpochDay());
        } catch (RuntimeException ex) {
            throw new IOException("Invalid historical flag timestamp: " + timestamp, ex);
        }
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static HashCode parseHashCode(String value) {
        String normalized = stringValue(value).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            return null;
        }
        return HashCode.fromString(normalized);
    }

    private static byte[] uncheckedEncodeIcon(byte[] rawBytes) {
        try {
            return TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(rawBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record FlagImportResult(
            int importedChangeCount,
            int importedIconCount,
            int importedAtlasAssignmentCount,
            int importedLastFlagUrlCount,
            int copiedRawCacheCount,
            int minDay,
            int maxDay
    ) {
    }

    public record FlagResetResult(int deletedChangeCount, int deletedRawCacheFileCount) {
    }

        record FlagValidationData(
            int sourceEventCount,
            Integer minDay,
            Integer maxDay,
            int sourceIconCount,
            int sourceAtlasAssignmentCount,
            Map<String, HashCode> rawHashByUrl,
            Map<Integer, byte[]> latestHashByAlliance
        ) {
        }

    private record RawFlagEvent(int allianceId, String timestamp, String rawFlagUrl) {
    }

    private record FlagsDayCache(List<RawFlagEvent> rawEvents, Map<String, HashCode> urlToHash) {
    }

    private record DownloadedFlag(String url, HashCode normalizedHash, HashCode rawHash, byte[] rawBytes) {
    }

    private record ResolvedHistoricalFlag(HashCode storedHash, byte[] iconBytes, int tileIndex) {
    }

    private record AllianceDayKey(int allianceId, int day) {
    }

    private record FlagImportRows(
            List<TreatyVisRuntimeRepository.FlagChangeRow> flagChanges,
            List<TreatyVisRuntimeRepository.FlagIconRow> flagIcons,
            List<TreatyVisRuntimeRepository.FlagAtlasStateRow> flagAtlasState,
            List<TreatyVisRuntimeRepository.LastFlagUrlRow> lastFlagUrls,
            int copiedRawCacheCount,
            int minDay,
            int maxDay
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AssetPayload(AssetAtlas atlas, Map<String, AssetPayloadEntry> assets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
        private record AssetAtlas(
            @JsonProperty("columns") int columns,
            @JsonProperty("webp") String webp,
            @JsonProperty("png") String png
        ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AssetPayloadEntry(int x, int y, int w, int h, String hash) {
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record DownloadState(@JsonProperty("urls") Map<String, DownloadStateEntry> urls) {
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
    private record DownloadStateEntry(
            @JsonProperty("cache_file") String cacheFile,
            @JsonProperty("content_sha256") String contentSha256
    ) {
    }
}
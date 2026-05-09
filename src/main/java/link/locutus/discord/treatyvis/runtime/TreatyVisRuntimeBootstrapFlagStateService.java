package link.locutus.discord.treatyvis.runtime;

import com.google.common.hash.HashCode;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Supplier;

public final class TreatyVisRuntimeBootstrapFlagStateService {
    private static final Path DEFAULT_RUNTIME_FLAG_CACHE_ROOT = Path.of("data", "flag_cache");

    private final TreatyVisRuntimeRepository repository;
    private final Path runtimeFlagCacheRoot;
    private final Supplier<List<LiveAllianceFlag>> liveFlagSupplier;
    private final FlagBytesFetcher flagBytesFetcher;

    public TreatyVisRuntimeBootstrapFlagStateService() {
        this(
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
                DEFAULT_RUNTIME_FLAG_CACHE_ROOT,
                TreatyVisRuntimeBootstrapFlagStateService::loadLiveAllianceFlags,
            TreatyVisRuntimeFlagAssetUtil::downloadRawBytes
        );
    }

    TreatyVisRuntimeBootstrapFlagStateService(
            TreatyVisRuntimeRepository repository,
            Path runtimeFlagCacheRoot,
            Supplier<List<LiveAllianceFlag>> liveFlagSupplier,
            FlagBytesFetcher flagBytesFetcher
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runtimeFlagCacheRoot = runtimeFlagCacheRoot.toAbsolutePath().normalize();
        this.liveFlagSupplier = Objects.requireNonNull(liveFlagSupplier, "liveFlagSupplier");
        this.flagBytesFetcher = Objects.requireNonNull(flagBytesFetcher, "flagBytesFetcher");
    }

    public void assertBootstrapTargetAvailable() throws IOException {
        repository.ensureTables();
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        if (!bootstrapState.flagImportComplete() || repository.loadLastFlagUrlRows().isEmpty()) {
            throw new IOException("Historical flag import must complete before bootstrapping live flag helper state.");
        }
    }

    public FlagBootstrapResult bootstrapCurrentFlags() throws IOException {
        assertBootstrapTargetAvailable();

        Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> previousByAlliance = repository.loadLastFlagUrlRows();
        Map<HashCode, Integer> tileIndexByHash = new HashMap<>();
        int nextTileIndex = 1;
        for (TreatyVisRuntimeRepository.FlagAtlasStateRow row : repository.loadFlagAtlasStateRows()) {
            tileIndexByHash.put(TreatyVisRuntimeFlagAssetUtil.hashCode(row.flagHash()), row.tileIndex());
            nextTileIndex = Math.max(nextTileIndex, row.tileIndex() + 1);
        }

        int bootstrapDay = Math.toIntExact(LocalDate.now(ZoneOffset.UTC).toEpochDay());
        List<LiveAllianceFlag> liveFlags = new ArrayList<>(liveFlagSupplier.get());
        liveFlags.sort(Comparator.comparingInt(LiveAllianceFlag::allianceId));

        Files.createDirectories(runtimeFlagCacheRoot);
        List<TreatyVisRuntimeRepository.FlagChangeRow> changeRows = new ArrayList<>();
        List<TreatyVisRuntimeRepository.FlagIconRow> iconRows = new ArrayList<>();
        List<TreatyVisRuntimeRepository.FlagAtlasStateRow> atlasRows = new ArrayList<>();
        List<TreatyVisRuntimeRepository.LastFlagUrlRow> lastUrlRows = new ArrayList<>();
        Map<String, LiveFlagAsset> assetByUrl = new LinkedHashMap<>();
        Set<String> failedUrls = new HashSet<>();

        for (LiveAllianceFlag liveFlag : liveFlags) {
            if (liveFlag.allianceId() <= 0) {
                continue;
            }
            String currentUrl = normalizeUrl(liveFlag.flagUrl());
            TreatyVisRuntimeRepository.LastFlagUrlRow previous = previousByAlliance.get(liveFlag.allianceId());
            String storedUrl = currentUrl.isBlank() ? null : currentUrl;
            HashCode previousHash = previous == null || previous.flagHash() == null
                    ? null
                    : TreatyVisRuntimeFlagAssetUtil.hashCode(previous.flagHash());
                HashCode currentHash = currentUrl.isBlank() ? null : previousHash;

            if (!currentUrl.isBlank()) {
                String previousUrl = previous == null ? "" : normalizeUrl(previous.flagUrl());
                if (!(previousHash != null && currentUrl.equals(previousUrl))) {
                    LiveFlagAsset asset = assetByUrl.get(currentUrl);
                    if (asset == null && !failedUrls.contains(currentUrl)) {
                        try {
                            byte[] rawBytes = flagBytesFetcher.fetch(currentUrl);
                            HashCode fetchedHash = TreatyVisRuntimeFlagAssetUtil.sha256(rawBytes);
                            asset = new LiveFlagAsset(fetchedHash, rawBytes);
                            assetByUrl.put(currentUrl, asset);

                            Path cachePath = runtimeFlagCacheRoot.resolve(fetchedHash.toString());
                            if (!Files.isRegularFile(cachePath)) {
                                Files.createDirectories(runtimeFlagCacheRoot);
                                Files.write(cachePath, rawBytes);
                            }
                            if (!tileIndexByHash.containsKey(fetchedHash)) {
                                tileIndexByHash.put(fetchedHash, nextTileIndex);
                                iconRows.add(new TreatyVisRuntimeRepository.FlagIconRow(fetchedHash.asBytes(), TreatyVisRuntimeFlagAssetUtil.encodeNormalizedIcon(rawBytes)));
                                atlasRows.add(new TreatyVisRuntimeRepository.FlagAtlasStateRow(fetchedHash.asBytes(), nextTileIndex));
                                nextTileIndex += 1;
                            }
                        } catch (IOException ex) {
                            failedUrls.add(currentUrl);
                            asset = null;
                    }
                    }
                    if (asset != null) {
                        currentHash = asset.rawHash();
                    }
                }
            }

            if (!Objects.equals(previousHash, currentHash)) {
                changeRows.add(new TreatyVisRuntimeRepository.FlagChangeRow(liveFlag.allianceId(), bootstrapDay, currentHash == null ? null : currentHash.asBytes()));
            }
            lastUrlRows.add(new TreatyVisRuntimeRepository.LastFlagUrlRow(
                    liveFlag.allianceId(),
                    storedUrl,
                    currentHash == null ? null : currentHash.asBytes()
            ));
        }

        if (!iconRows.isEmpty()) {
            repository.upsertFlagIcons(iconRows);
        }
        if (!atlasRows.isEmpty()) {
            repository.upsertFlagAtlasState(atlasRows);
        }
        if (!changeRows.isEmpty()) {
            repository.appendFlagChanges(changeRows);
        }
        repository.upsertLastFlagUrls(lastUrlRows);

        return new FlagBootstrapResult(lastUrlRows.size(), changeRows.size(), iconRows.size(), bootstrapDay);
    }

    private static List<LiveAllianceFlag> loadLiveAllianceFlags() {
        return Locutus.imp().getNationDB().getAlliances().stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new LiveAllianceFlag(alliance.getId(), alliance.getFlag()))
                .toList();
    }

    private static String normalizeUrl(String flagUrl) {
        return flagUrl == null ? "" : flagUrl.trim();
    }

    public record LiveAllianceFlag(int allianceId, String flagUrl) {
    }

    public record FlagBootstrapResult(int helperAllianceCount, int changedAllianceCount, int newIconCount, int bootstrapDay) {
    }

    @FunctionalInterface
    interface FlagBytesFetcher {
        byte[] fetch(String flagUrl) throws IOException;
    }

    private record LiveFlagAsset(HashCode rawHash, byte[] rawBytes) {
    }
}
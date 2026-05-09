package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.AllianceNameHistoryRepository;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Treaty;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TreatyHistoryService {
    public static final String TREATY_HISTORY_OBJECT_KEY = "treaty/history.msgpack";
    public static final String TREATY_ATLAS_OBJECT_KEY = "treaty/atlas.webp";

    private final ObjectMapper serializer;
    private final TreatyVisRuntimeBuilder builder;
    private final TreatyVisRuntimeImportedSnapshotService importedSnapshotService;
    private final TreatyHistoryAtlasCache atlasCache;

    public TreatyHistoryService() {
        this(
                TreatyVisRuntimeSerializers.MSGPACK,
                new TreatyVisRuntimeBuilder(),
                null,
                new TreatyHistoryAtlasCache(
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_CACHE_PATH,
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_STATE_PATH
                )
        );
    }

    TreatyHistoryService(ObjectMapper serializer, TreatyVisRuntimeBuilder builder) {
        this(
                serializer,
                builder,
                null,
                new TreatyHistoryAtlasCache(
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_CACHE_PATH,
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_STATE_PATH
                )
        );
    }

    TreatyHistoryService(
            ObjectMapper serializer,
            TreatyVisRuntimeBuilder builder,
            TreatyVisRuntimeImportedSnapshotService importedSnapshotService,
            TreatyHistoryAtlasCache atlasCache
    ) {
        this.serializer = serializer;
        this.builder = builder;
        this.importedSnapshotService = importedSnapshotService;
        this.atlasCache = atlasCache;
    }

    public TreatyVisRuntimePayload buildPayload() {
        return buildPayloadSnapshotData().payload();
    }

    public byte[] encodePayloadMessagePack() throws IOException {
        return serializer.writeValueAsBytes(buildPayload());
    }

    public byte[] buildAtlasWebp() throws IOException {
        AtlasSnapshotData snapshot = buildAtlasSnapshotData();
        return atlasCache.loadOrBuild(snapshot.toAtlasSource(), () -> buildAtlasImage(snapshot));
    }

    public BuiltTreatyArtifacts buildRuntimeArtifacts() throws IOException {
        byte[] historyBytes = encodePayloadMessagePack();
        byte[] atlasBytes = buildAtlasWebp();
        return new BuiltTreatyArtifacts(
                historyBytes,
                atlasBytes,
                TreatyVisRuntimeFlagAssetUtil.sha256(historyBytes).asBytes()
        );
    }

    private PayloadSnapshotData buildPayloadSnapshotData() {
        NationDB db = Locutus.imp().getNationDB();
        TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
        AllianceNameHistoryRepository allianceNameHistory = Locutus.imp().getWarDb().getAllianceNameHistory();
        List<DBAlliance> allAlliances = new ArrayList<>(db.getAlliances());

        TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot importedSnapshot = loadImportedSnapshot(repository, allianceNameHistory);
        if (importedSnapshot != null) {
            TreatyVisRuntimePayload payload = builder.build(importedSnapshot.input());
            return new PayloadSnapshotData(payload);
        }

        Set<Treaty> activeTreaties = db.getTreaties();
        List<TreatyVisRuntimeInput.TreatyEdge> treaties = activeTreaties.stream()
                .map(treaty -> new TreatyVisRuntimeInput.TreatyEdge(
                        treaty.getFromId(),
                        treaty.getToId(),
                        treaty.getType()
                ))
                .toList();

        List<TreatyVisRuntimeInput.AllianceFlag> initialFlags = buildCurrentFlagsFromRepository(allAlliances, repository);

        List<TreatyVisRuntimeInput.AllianceScore> scores = allAlliances.stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new TreatyVisRuntimeInput.AllianceScore(
                        alliance.getId(),
                        quantizeScore(alliance.getScore())
                ))
                .filter(score -> score.quantizedScore() > 0)
                .sorted(Comparator
                        .comparingInt(TreatyVisRuntimeInput.AllianceScore::quantizedScore)
                        .reversed()
                        .thenComparingInt(TreatyVisRuntimeInput.AllianceScore::allianceId))
                .limit(TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES)
                .toList();
            List<TreatyVisRuntimeInput.Alliance> alliances = allAlliances.stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new TreatyVisRuntimeInput.Alliance(
                    alliance.getId(),
                    allianceNameHistory.getAllianceName(alliance.getId())
                ))
                .toList();

        TreatyVisRuntimeInput input = new TreatyVisRuntimeInput(
                currentEpochDayUtc(),
                TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
            alliances,
                treaties,
                initialFlags,
                scores
        );
        TreatyVisRuntimePayload payload = builder.build(input);
        return new PayloadSnapshotData(payload);
    }

    private AtlasSnapshotData buildAtlasSnapshotData() {
        NationDB db = Locutus.imp().getNationDB();
        TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);

        TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot importedSnapshot = loadImportedSnapshot(
                repository,
                Locutus.imp().getWarDb().getAllianceNameHistory()
        );
        if (importedSnapshot != null) {
            return new AtlasSnapshotData(importedSnapshot.flagIconBytesByIndex());
        }

        try {
            return new AtlasSnapshotData(buildStoredIconBytesByIndex(repository));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot loadImportedSnapshot(
            TreatyVisRuntimeRepository repository,
            AllianceNameHistoryRepository allianceNameHistory
    ) {
        try {
            TreatyVisRuntimeImportedSnapshotService snapshotService = importedSnapshotService != null
                    ? importedSnapshotService
                    : new TreatyVisRuntimeImportedSnapshotService(repository);
            return snapshotService.buildImportedSnapshot(allianceNameHistory::getAllianceName);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static BufferedImage buildAtlasImage(AtlasSnapshotData snapshot) {
        int tileCount = resolveTileCount(snapshot.flagIconBytesByIndex());
        int rows = Math.max(1, (int) Math.ceil(tileCount / (double) TreatyHistoryRuntimeConfig.FLAG_ATLAS_COLUMNS));
        int width = TreatyHistoryRuntimeConfig.FLAG_ATLAS_COLUMNS * TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_WIDTH;
        int height = rows * TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_HEIGHT;

        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, width, height);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        try {
            for (int index = 1; index < tileCount; index += 1) {
                byte[] iconBytes = snapshot.flagIconBytesByIndex().get(index);
                if (iconBytes == null) {
                    continue;
                }

                BufferedImage image;
                try {
                    image = TreatyVisRuntimeFlagAssetUtil.decodeValidatedImage(iconBytes);
                } catch (IOException ex) {
                    continue;
                }
                if (image == null) {
                    continue;
                }

                int tileX = (index % TreatyHistoryRuntimeConfig.FLAG_ATLAS_COLUMNS) * TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_WIDTH;
                int tileY = (index / TreatyHistoryRuntimeConfig.FLAG_ATLAS_COLUMNS) * TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_HEIGHT;
                drawImageIntoTile(
                        graphics,
                        image,
                        tileX,
                        tileY,
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_WIDTH,
                        TreatyHistoryRuntimeConfig.FLAG_ATLAS_TILE_HEIGHT
                );
            }
        } finally {
            graphics.dispose();
        }

        return atlas;
    }

    private static int resolveTileCount(Map<Integer, byte[]> flagIconBytesByIndex) {
        int maxIndex = 0;
        for (int index : flagIconBytesByIndex.keySet()) {
            if (index > maxIndex) {
                maxIndex = index;
            }
        }
        return Math.max(maxIndex + 1, 1);
    }

    private static void drawImageIntoTile(Graphics2D graphics, BufferedImage image, int tileX, int tileY, int tileWidth, int tileHeight) {
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }

        double scale = Math.min(tileWidth / (double) sourceWidth, tileHeight / (double) sourceHeight);
        int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int drawX = tileX + Math.max(0, (tileWidth - drawWidth) / 2);
        int drawY = tileY + Math.max(0, (tileHeight - drawHeight) / 2);
        graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
    }

    private static List<TreatyVisRuntimeInput.AllianceFlag> buildCurrentFlagsFromRepository(
            List<DBAlliance> allAlliances,
            TreatyVisRuntimeRepository repository
    ) {
        try {
            Map<HashCode, Integer> flagIndexByHash = TreatyVisRuntimeFlagAssetUtil.buildDenseTileIndexByHash(repository.loadFlagAtlasStateRows());
            Map<Integer, byte[]> flagHashByAlliance = repository.loadLastFlagHashesByAlliance();
            return allAlliances.stream()
                    .filter(alliance -> alliance.getId() > 0)
                    .map(alliance -> {
                        byte[] flagHash = flagHashByAlliance.get(alliance.getId());
                        Integer flagIndex = flagHash == null ? null : flagIndexByHash.get(TreatyVisRuntimeFlagAssetUtil.hashCode(flagHash));
                        return new TreatyVisRuntimeInput.AllianceFlag(alliance.getId(), flagIndex == null ? 0 : flagIndex);
                    })
                    .filter(flag -> flag.flagIndex() > 0)
                    .toList();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Map<Integer, byte[]> buildStoredIconBytesByIndex(TreatyVisRuntimeRepository repository) throws IOException {
        Map<HashCode, Integer> flagIndexByHash = TreatyVisRuntimeFlagAssetUtil.buildDenseTileIndexByHash(repository.loadFlagAtlasStateRows());
        Map<Integer, byte[]> iconBytesByIndex = new LinkedHashMap<>();
        for (TreatyVisRuntimeRepository.FlagIconRow row : repository.loadFlagIconRows()) {
            Integer tileIndex = flagIndexByHash.get(TreatyVisRuntimeFlagAssetUtil.hashCode(row.flagHash()));
            if (tileIndex != null) {
                iconBytesByIndex.put(tileIndex, row.iconBytes());
            }
        }
        return Map.copyOf(iconBytesByIndex);
    }

    private static int currentEpochDayUtc() {
        return Math.toIntExact(LocalDate.now(ZoneOffset.UTC).toEpochDay());
    }

    private static int quantizeScore(double score) {
        return Math.max(0, Math.toIntExact(Math.round(score * TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION)));
    }

    private record PayloadSnapshotData(TreatyVisRuntimePayload payload) {
    }

        public record BuiltTreatyArtifacts(
            byte[] historyBytes,
            byte[] atlasBytes,
            byte[] historySha256
    ) {
    }

    private record AtlasSnapshotData(Map<Integer, byte[]> flagIconBytesByIndex) {
        TreatyHistoryAtlasCache.TreatyHistoryAtlasSource toAtlasSource() {
            return new TreatyHistoryAtlasCache.TreatyHistoryAtlasSource(flagIconBytesByIndex);
        }
    }
}
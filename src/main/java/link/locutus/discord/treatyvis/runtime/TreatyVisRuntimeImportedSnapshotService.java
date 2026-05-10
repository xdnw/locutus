package link.locutus.discord.treatyvis.runtime;

import com.google.common.hash.HashCode;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.TreatyChangeAction;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntFunction;

final class TreatyVisRuntimeImportedSnapshotService {
    private final TreatyVisRuntimeRepository repository;

    TreatyVisRuntimeImportedSnapshotService(TreatyVisRuntimeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    ImportedSnapshot buildImportedSnapshot(IntFunction<String> allianceNameResolver) throws IOException {
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        if (!bootstrapState.treatyImportComplete()
                || !bootstrapState.scoreImportComplete()
                || !bootstrapState.flagImportComplete()) {
            return null;
        }

        Integer importedTreatyMinDay = bootstrapState.importedTreatyMinDay();
        if (importedTreatyMinDay == null) {
            return null;
        }

        int baseDay = importedTreatyMinDay;
        List<TreatyVisRuntimeRepository.FlagAtlasStateRow> atlasRows = repository.loadFlagAtlasStateRows();
        Map<HashCode, Integer> flagIndexByHash = TreatyVisRuntimeFlagAssetUtil.buildDenseTileIndexByHash(atlasRows);
        Map<Integer, byte[]> iconByFlagIndex = TreatyVisRuntimeFlagAssetUtil.buildIconBytesByIndex(flagIndexByHash, repository.loadFlagIconRows());
        validateAtlasIcons(flagIndexByHash, iconByFlagIndex);

        TreatyTimelineProjection treatyTimeline = buildTreatyTimeline(baseDay);
        TreeMap<Integer, byte[]> scoreRows = new TreeMap<>(repository.loadTopNScoreRows());
        List<TreatyVisRuntimeInput.AllianceScore> initialScores = buildInitialScores(scoreRows.floorEntry(baseDay));
        List<TreatyVisRuntimeInput.ScoreSnapshot> scoreSnapshots = buildScoreSnapshots(baseDay, scoreRows.tailMap(baseDay, false));

        List<TreatyVisRuntimeRepository.FlagChangeRow> flagRows = repository.loadFlagChangeRows();
        Map<Integer, Integer> baseFlagIndexes = new LinkedHashMap<>();
        List<TreatyVisRuntimeInput.FlagChange> futureFlagChanges = new ArrayList<>();
        for (TreatyVisRuntimeRepository.FlagChangeRow row : flagRows) {
            int flagIndex = resolveFlagIndex(row.flagHash(), flagIndexByHash);
            if (row.day() <= baseDay) {
                baseFlagIndexes.put(row.allianceId(), flagIndex);
            } else {
                futureFlagChanges.add(new TreatyVisRuntimeInput.FlagChange(row.day() - baseDay, row.allianceId(), flagIndex));
            }
        }
        List<TreatyVisRuntimeInput.AllianceFlag> initialFlags = baseFlagIndexes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new TreatyVisRuntimeInput.AllianceFlag(entry.getKey(), entry.getValue()))
                .toList();
        List<TreatyVisRuntimeInput.Alliance> alliances = buildAlliances(
            treatyTimeline,
            initialFlags,
            futureFlagChanges,
            initialScores,
            scoreSnapshots,
            allianceNameResolver
        );

        return new ImportedSnapshot(
                new TreatyVisRuntimeInput(
                        baseDay,
                        TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION,
                alliances,
                        treatyTimeline.initialActiveTreaties(),
                        initialFlags,
                        initialScores,
                        treatyTimeline.futureTreatyChanges(),
                        futureFlagChanges,
                        scoreSnapshots
                ),
                iconByFlagIndex
        );
    }

    private static List<TreatyVisRuntimeInput.Alliance> buildAlliances(
            TreatyTimelineProjection treatyTimeline,
            List<TreatyVisRuntimeInput.AllianceFlag> initialFlags,
            List<TreatyVisRuntimeInput.FlagChange> futureFlagChanges,
            List<TreatyVisRuntimeInput.AllianceScore> initialScores,
            List<TreatyVisRuntimeInput.ScoreSnapshot> scoreSnapshots,
            IntFunction<String> allianceNameResolver
    ) {
        LinkedHashSet<Integer> allianceIds = new LinkedHashSet<>();
        for (TreatyVisRuntimeInput.TreatyEdge edge : treatyTimeline.initialActiveTreaties()) {
            allianceIds.add(edge.fromAllianceId());
            allianceIds.add(edge.toAllianceId());
        }
        for (TreatyVisRuntimeInput.TreatyChange change : treatyTimeline.futureTreatyChanges()) {
            TreatyVisRuntimeInput.TreatyEdge edge = change.edge();
            allianceIds.add(edge.fromAllianceId());
            allianceIds.add(edge.toAllianceId());
        }
        for (TreatyVisRuntimeInput.AllianceFlag flag : initialFlags) {
            allianceIds.add(flag.allianceId());
        }
        for (TreatyVisRuntimeInput.FlagChange change : futureFlagChanges) {
            allianceIds.add(change.allianceId());
        }
        for (TreatyVisRuntimeInput.AllianceScore score : initialScores) {
            allianceIds.add(score.allianceId());
        }
        for (TreatyVisRuntimeInput.ScoreSnapshot snapshot : scoreSnapshots) {
            for (TreatyVisRuntimeInput.AllianceScore score : snapshot.scores()) {
                allianceIds.add(score.allianceId());
            }
        }
        return allianceIds.stream()
                .filter(allianceId -> allianceId > 0)
                .sorted()
                .map(allianceId -> new TreatyVisRuntimeInput.Alliance(allianceId, allianceNameResolver.apply(allianceId)))
                .toList();
    }

    private TreatyTimelineProjection buildTreatyTimeline(int baseDay) {
        List<DBTreatyChange> rows = repository.loadUnifiedTreatyChangesSince(0L).stream()
                .sorted(Comparator.comparingLong(DBTreatyChange::getTimestamp))
                .toList();
        Map<TreatyEdgeKey, TreatyVisRuntimeInput.TreatyEdge> activeTreatiesByKey = new LinkedHashMap<>();
        List<TreatyVisRuntimeInput.TreatyChange> futureChanges = new ArrayList<>();
        for (DBTreatyChange row : rows) {
            TreatyVisRuntimeInput.TreatyEdge edge = new TreatyVisRuntimeInput.TreatyEdge(
                    row.getFromAllianceId(),
                    row.getToAllianceId(),
                    row.getTreatyType()
            );
            TreatyEdgeKey edgeKey = TreatyEdgeKey.from(edge);
            int eventDay = Math.toIntExact(Instant.ofEpochMilli(row.getTimestamp()).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay());
            if (eventDay < baseDay) {
                applyTreatyAction(activeTreatiesByKey, edgeKey, edge, row.getAction());
                continue;
            }
            futureChanges.add(new TreatyVisRuntimeInput.TreatyChange(
                    eventDay - baseDay,
                    edge,
                    toPayloadActionCode(row.getAction())
            ));
        }
        return new TreatyTimelineProjection(List.copyOf(activeTreatiesByKey.values()), List.copyOf(futureChanges));
    }

    private static void applyTreatyAction(
            Map<TreatyEdgeKey, TreatyVisRuntimeInput.TreatyEdge> activeTreatiesByKey,
            TreatyEdgeKey edgeKey,
            TreatyVisRuntimeInput.TreatyEdge edge,
            TreatyChangeAction action
    ) {
        switch (action) {
            case SIGNED, EXTENDED -> activeTreatiesByKey.put(edgeKey, edge);
            case CANCELLED, EXPIRED, ENDED -> activeTreatiesByKey.remove(edgeKey);
        }
    }

    private static int toPayloadActionCode(TreatyChangeAction action) {
        return switch (action) {
            case SIGNED -> 1;
            case EXTENDED -> 2;
            case CANCELLED, ENDED -> 3;
            case EXPIRED -> 4;
        };
    }

    private static List<TreatyVisRuntimeInput.AllianceScore> buildInitialScores(Map.Entry<Integer, byte[]> baseRow) {
        if (baseRow == null) {
            return List.of();
        }
        return decodeAllianceScores(baseRow.getValue());
    }

    private static List<TreatyVisRuntimeInput.ScoreSnapshot> buildScoreSnapshots(int baseDay, SortedMap<Integer, byte[]> futureRows) {
        List<TreatyVisRuntimeInput.ScoreSnapshot> snapshots = new ArrayList<>(futureRows.size());
        for (Map.Entry<Integer, byte[]> entry : futureRows.entrySet()) {
            snapshots.add(new TreatyVisRuntimeInput.ScoreSnapshot(entry.getKey() - baseDay, decodeAllianceScores(entry.getValue())));
        }
        return List.copyOf(snapshots);
    }

    private static void validateAtlasIcons(
            Map<HashCode, Integer> flagIndexByHash,
            Map<Integer, byte[]> iconByFlagIndex
    ) throws IOException {
        for (Integer tileIndex : flagIndexByHash.values()) {
            if (!iconByFlagIndex.containsKey(tileIndex)) {
                throw new IOException("Missing flag icon payload for atlas tile index " + tileIndex);
            }
        }
    }

    private static int resolveFlagIndex(byte[] flagHash, Map<HashCode, Integer> flagIndexByHash) throws IOException {
        if (flagHash == null) {
            return 0;
        }
        HashCode flagHashCode = TreatyVisRuntimeFlagAssetUtil.hashCode(flagHash);
        Integer flagIndex = flagIndexByHash.get(flagHashCode);
        if (flagIndex == null) {
            throw new IOException("Missing flag atlas index for imported flag hash " + flagHashCode);
        }
        return flagIndex;
    }

    private static List<TreatyVisRuntimeInput.AllianceScore> decodeAllianceScores(byte[] payload) {
        return TreatyVisRuntimeScoreRowCodec.decode(payload).stream()
                .map(row -> new TreatyVisRuntimeInput.AllianceScore(row.allianceId(), row.quantizedScore()))
                .toList();
    }

    record ImportedSnapshot(TreatyVisRuntimeInput input, Map<Integer, byte[]> flagIconBytesByIndex) {
    }

    private record TreatyTimelineProjection(
            List<TreatyVisRuntimeInput.TreatyEdge> initialActiveTreaties,
            List<TreatyVisRuntimeInput.TreatyChange> futureTreatyChanges
    ) {
    }

    private record TreatyEdgeKey(int fromAllianceId, int toAllianceId, TreatyType treatyType) {
        static TreatyEdgeKey from(TreatyVisRuntimeInput.TreatyEdge edge) {
            return new TreatyEdgeKey(edge.fromAllianceId(), edge.toAllianceId(), edge.treatyType());
        }
    }
}
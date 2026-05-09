package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.TreatyChangeAction;
import link.locutus.discord.util.TimeUtil;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

public final class TreatyVisRuntimeLegacyTreatyImportService {
    private static final String DEFAULT_INDEX_FILE = "treaty_changes_reconciled_index.msgpack";

    private final Path stagedImportRoot;
    private final TreatyVisRuntimeRepository repository;
    private final ObjectMapper msgpack;
    private final LongSupplier currentTimeMsSupplier;

    public TreatyVisRuntimeLegacyTreatyImportService() {
        this(
                TreatyVisRuntimeBootstrapService.DEFAULT_IMPORT_ROOT,
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
            TreatyVisRuntimeSerializers.MSGPACK,
                System::currentTimeMillis
        );
    }

    TreatyVisRuntimeLegacyTreatyImportService(Path stagedImportRoot, TreatyVisRuntimeRepository repository, ObjectMapper msgpack) {
        this(stagedImportRoot, repository, msgpack, System::currentTimeMillis);
    }

    TreatyVisRuntimeLegacyTreatyImportService(
            Path stagedImportRoot,
            TreatyVisRuntimeRepository repository,
            ObjectMapper msgpack,
            LongSupplier currentTimeMsSupplier
    ) {
        this.stagedImportRoot = stagedImportRoot.toAbsolutePath().normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.msgpack = Objects.requireNonNull(msgpack, "msgpack");
        this.currentTimeMsSupplier = Objects.requireNonNull(currentTimeMsSupplier, "currentTimeMsSupplier");
    }

    public void assertImportTargetAvailable(boolean replaceExisting) throws IOException {
        repository.ensureTables();
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        if (!replaceExisting && (bootstrapState.treatyImportComplete() || repository.countUnifiedTreatyChanges() > 0)) {
            throw new IOException("Historical treaty import already exists. Run the runtime bootstrap reset first or rerun with replace enabled.");
        }
    }

    public TreatyImportResult importHistoricalTreaties(boolean replaceExisting) throws IOException {
        assertImportTargetAvailable(replaceExisting);
        if (replaceExisting) {
            repository.clearUnifiedTreatyHistory();
        }

        List<TreatyVisRuntimeRepository.UnifiedTreatyChangeRow> importedRows = loadUnifiedTreatyRows();
        if (importedRows.isEmpty()) {
            throw new IOException("No staged historical treaty events were found under " + stagedImportRoot);
        }

        repository.replaceUnifiedTreatyChanges(importedRows);
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;
        for (TreatyVisRuntimeRepository.UnifiedTreatyChangeRow row : importedRows) {
            int day = Math.toIntExact(Instant.ofEpochMilli(row.timestamp()).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay());
            minDay = Math.min(minDay, day);
            maxDay = Math.max(maxDay, day);
        }
        repository.markHistoricalTreatyImportComplete(minDay, maxDay);

        return new TreatyImportResult(importedRows.size(), minDay, maxDay);
    }

    public TreatyResetResult resetImportedHistoricalTreaties() {
        int deletedChangeCount = repository.clearUnifiedTreatyHistory();
        return new TreatyResetResult(deletedChangeCount);
    }

    List<TreatyVisRuntimeRepository.UnifiedTreatyChangeRow> loadUnifiedTreatyRows() throws IOException {
        List<DBTreatyChange> treatyVisRows = loadHistoricalTreatySourceRows();
        List<DBTreatyChange> legacyBackendRows = repository.loadLegacyTreatyChangesSource();
        long backendCutoffMs = legacyBackendRows.isEmpty() ? Long.MAX_VALUE : legacyBackendRows.get(0).getTimestamp();

        List<DBTreatyChange> preferredRows = new ArrayList<>(treatyVisRows.size() + legacyBackendRows.size());
        for (DBTreatyChange row : treatyVisRows) {
            if (row.getTimestamp() < backendCutoffMs || isTerminal(row.getAction())) {
                preferredRows.add(row);
            }
        }
        preferredRows.addAll(legacyBackendRows);

        List<DBTreatyChange> normalizedRows = sortAndDedupe(preferredRows);
        List<DBTreatyChange> rowsWithSyntheticExpiries = appendSyntheticExpiryRows(normalizedRows, currentTimeMsSupplier.getAsLong());
        return rowsWithSyntheticExpiries.stream()
                .map(row -> new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                        row.getTimestamp(),
                        row.getAction(),
                        row.getTreatyType(),
                        row.getFromAllianceId(),
                        row.getToAllianceId(),
                        row.getTurnsRemaining()
                ))
                .toList();
    }

    List<DBTreatyChange> loadHistoricalTreatySourceRows() throws IOException {
        Path publicDataRoot = stagedImportRoot.resolve(Path.of("public", "data"));
        Path indexPath = publicDataRoot.resolve(DEFAULT_INDEX_FILE);
        if (!Files.isRegularFile(indexPath)) {
            throw new IOException("Staged treaty index is missing: " + indexPath);
        }

        Map<String, Object> indexPayload = msgpack.readValue(indexPath.toFile(), new TypeReference<>() {
        });
        if (indexPayload == null) {
            throw new IOException("Unable to decode staged treaty index payload: " + indexPath);
        }

        List<Map<String, Object>> rawEvents = new ArrayList<>();
        Object windows = indexPayload.get("windows");
        if (windows instanceof List<?> windowRows) {
            for (Object windowRow : windowRows) {
                if (!(windowRow instanceof Map<?, ?> row)) {
                    continue;
                }
                String fileName = safe(row.get("file"));
                if (fileName.isBlank()) {
                    continue;
                }
                rawEvents.addAll(loadTreatyEventList(publicDataRoot.resolve(fileName)));
            }
        }

        String deltaFile = safe(indexPayload.get("delta_file"));
        if (deltaFile.isBlank()) {
            deltaFile = "treaty_changes_reconciled_delta.msgpack";
        }
        rawEvents.addAll(loadTreatyEventList(publicDataRoot.resolve(deltaFile)));

        List<DBTreatyChange> rows = new ArrayList<>(rawEvents.size());
        for (Map<String, Object> rawEvent : rawEvents) {
            rows.add(normalizeLegacyTreatyChange(rawEvent));
        }
        return sortAndDedupe(resolveUnknownTreatyTypes(sortAndDedupe(rows)));
    }

    private List<Map<String, Object>> loadTreatyEventList(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Staged treaty event payload is missing: " + path);
        }
        List<Map<String, Object>> payload = msgpack.readValue(path.toFile(), new TypeReference<>() {
        });
        return payload == null ? List.of() : payload;
    }

    private DBTreatyChange normalizeLegacyTreatyChange(Map<String, Object> rawEvent) throws IOException {
        long timestamp = parseTimestampMillis(safe(rawEvent.get("timestamp")));
        TreatyChangeAction action = parseAction(safe(rawEvent.get("action")));
        TreatyType treatyType = parseTreatyType(safe(rawEvent.get("treaty_type")));
        int fromAllianceId = parsePositiveInt(rawEvent.get("from_alliance_id"), "from_alliance_id");
        int toAllianceId = parsePositiveInt(rawEvent.get("to_alliance_id"), "to_alliance_id");
        int turnsRemaining = parseTurnsRemaining(rawEvent.get("time_remaining_turns"), rawEvent.get("turns_remaining"));
        return new DBTreatyChange(timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining);
    }

    private static long parseTimestampMillis(String value) throws IOException {
        if (value.isBlank()) {
            throw new IOException("Historical treaty event timestamp is missing");
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid historical treaty timestamp: " + value, ex);
        }
    }

    private static TreatyChangeAction parseAction(String value) throws IOException {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "signed" -> TreatyChangeAction.SIGNED;
            case "extended" -> TreatyChangeAction.EXTENDED;
            case "cancelled" -> TreatyChangeAction.CANCELLED;
            case "expired" -> TreatyChangeAction.EXPIRED;
            case "ended", "terminated", "termination", "inferred_cancelled" -> TreatyChangeAction.ENDED;
            default -> throw new IOException("Unsupported historical treaty action: " + value);
        };
    }

    private static TreatyType parseTreatyType(String value) throws IOException {
        String normalized = value.trim().replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IOException("Historical treaty type is missing");
        }
        if (normalized.equals("UNKNOWN")) {
            return TreatyType.NONE;
        }
        try {
            return TreatyType.parse(normalized);
        } catch (RuntimeException ex) {
            throw new IOException("Unsupported historical treaty type: " + value, ex);
        }
    }

    private static int parsePositiveInt(Object value, String label) throws IOException {
        try {
            int parsed = Integer.parseInt(safe(value));
            if (parsed <= 0) {
                throw new IOException("Historical treaty " + label + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid historical treaty " + label + ": " + value, ex);
        }
    }

    private static int parseTurnsRemaining(Object primary, Object secondary) throws IOException {
        Object candidate = primary != null ? primary : secondary;
        if (candidate == null || safe(candidate).isBlank()) {
            return -1;
        }
        try {
            int value = Integer.parseInt(safe(candidate));
            if (value < -1) {
                throw new IOException("Historical treaty turns_remaining must be >= -1");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid historical treaty turns_remaining: " + candidate, ex);
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isTerminal(TreatyChangeAction action) {
        return action == TreatyChangeAction.CANCELLED || action == TreatyChangeAction.EXPIRED || action == TreatyChangeAction.ENDED;
    }

    private static List<DBTreatyChange> resolveUnknownTreatyTypes(List<DBTreatyChange> rows) throws IOException {
        Map<TreatyPairKey, List<DBTreatyChange>> rowsByPair = new LinkedHashMap<>();
        for (DBTreatyChange row : rows) {
            rowsByPair.computeIfAbsent(TreatyPairKey.from(row.getFromAllianceId(), row.getToAllianceId()), ignored -> new ArrayList<>())
                    .add(row);
        }

        List<DBTreatyChange> resolvedRows = new ArrayList<>(rows.size());
        for (List<DBTreatyChange> pairRows : rowsByPair.values()) {
            resolvedRows.addAll(resolveUnknownTreatyTypesForPair(pairRows));
        }
        return resolvedRows;
    }

    private static List<DBTreatyChange> resolveUnknownTreatyTypesForPair(List<DBTreatyChange> pairRows) {
        TreatyType activeType = null;
        List<DBTreatyChange> resolvedRows = new ArrayList<>(pairRows.size());
        for (int index = 0; index < pairRows.size(); index++) {
            DBTreatyChange row = pairRows.get(index);
            TreatyType treatyType = row.getTreatyType();
            if (treatyType == TreatyType.NONE) {
                treatyType = inferMissingTreatyType(pairRows, index, activeType);
                if (treatyType == null) {
                    continue;
                }
                row = new DBTreatyChange(
                        row.getTimestamp(),
                        row.getAction(),
                        treatyType,
                        row.getFromAllianceId(),
                        row.getToAllianceId(),
                        row.getTurnsRemaining()
                );
            }

            TreatyChangeAction action = row.getAction();
            if (action == TreatyChangeAction.SIGNED || action == TreatyChangeAction.EXTENDED) {
                activeType = treatyType;
            } else if (isTerminal(action) && activeType == treatyType) {
                activeType = null;
            }
            resolvedRows.add(row);
        }
        return resolvedRows;
    }

    private static TreatyType inferMissingTreatyType(List<DBTreatyChange> pairRows, int index, TreatyType activeType) {
        DBTreatyChange row = pairRows.get(index);
        TreatyChangeAction action = row.getAction();
        if (activeType != null && action != TreatyChangeAction.SIGNED) {
            return activeType;
        }

        TreatyType candidate = null;
        for (int nextIndex = index + 1; nextIndex < pairRows.size(); nextIndex++) {
            DBTreatyChange nextRow = pairRows.get(nextIndex);
            TreatyType nextType = nextRow.getTreatyType();
            if (nextType == TreatyType.NONE) {
                continue;
            }
            if (nextRow.getAction() == TreatyChangeAction.SIGNED) {
                return null;
            }
            if (candidate == null) {
                candidate = nextType;
                continue;
            }
            if (candidate != nextType) {
                return null;
            }
        }
        return candidate;
    }

    private static List<DBTreatyChange> sortAndDedupe(List<DBTreatyChange> rows) {
        List<DBTreatyChange> sortedRows = rows.stream()
                .sorted(Comparator.comparingLong(DBTreatyChange::getTimestamp)
                        .thenComparingInt(DBTreatyChange::getFromAllianceId)
                        .thenComparingInt(DBTreatyChange::getToAllianceId)
                        .thenComparing(row -> row.getTreatyType().ordinal())
                        .thenComparing(row -> row.getAction().ordinal())
                        .thenComparingInt(DBTreatyChange::getTurnsRemaining))
                .toList();
        List<DBTreatyChange> dedupedRows = new ArrayList<>(sortedRows.size());
        Set<ChangeKey> seen = new LinkedHashSet<>();
        for (DBTreatyChange row : sortedRows) {
            if (seen.add(ChangeKey.from(row))) {
                dedupedRows.add(row);
            }
        }
        return List.copyOf(dedupedRows);
    }

    private static List<DBTreatyChange> appendSyntheticExpiryRows(List<DBTreatyChange> rows, long currentTimeMs) {
        Map<TreatyPairKey, ActiveTreatyState> activeByPair = new LinkedHashMap<>();
        Set<ChangeKey> seen = new LinkedHashSet<>();
        for (DBTreatyChange row : rows) {
            seen.add(ChangeKey.from(row));
            TreatyPairKey pairKey = TreatyPairKey.from(row.getFromAllianceId(), row.getToAllianceId());
            if (row.getAction() == TreatyChangeAction.SIGNED || row.getAction() == TreatyChangeAction.EXTENDED) {
                long turnEnds = row.getTurnsRemaining() < 0
                        ? Long.MAX_VALUE
                        : TimeUtil.getTurn(row.getTimestamp()) + row.getTurnsRemaining();
                activeByPair.put(pairKey, new ActiveTreatyState(row.getTreatyType(), turnEnds));
                continue;
            }
            if (isTerminal(row.getAction())) {
                ActiveTreatyState current = activeByPair.get(pairKey);
                if (current != null && current.treatyType() == row.getTreatyType()) {
                    activeByPair.remove(pairKey);
                }
            }
        }

        long currentTurn = TimeUtil.getTurn(currentTimeMs);
        List<DBTreatyChange> syntheticRows = new ArrayList<>();
        for (Map.Entry<TreatyPairKey, ActiveTreatyState> entry : activeByPair.entrySet()) {
            ActiveTreatyState state = entry.getValue();
            if (state.isPermanent() || state.turnEnds() > currentTurn) {
                continue;
            }
            DBTreatyChange expiry = new DBTreatyChange(
                    TimeUtil.getTimeFromTurn(state.turnEnds()),
                    TreatyChangeAction.EXPIRED,
                    state.treatyType(),
                    entry.getKey().fromAllianceId(),
                    entry.getKey().toAllianceId(),
                    0
            );
            if (seen.add(ChangeKey.from(expiry))) {
                syntheticRows.add(expiry);
            }
        }
        if (syntheticRows.isEmpty()) {
            return rows;
        }
        List<DBTreatyChange> combined = new ArrayList<>(rows.size() + syntheticRows.size());
        combined.addAll(rows);
        combined.addAll(syntheticRows);
        return sortAndDedupe(combined);
    }

    private record ChangeKey(
            long timestamp,
            TreatyChangeAction action,
            TreatyType treatyType,
            int fromAllianceId,
            int toAllianceId,
            int turnsRemaining
    ) {
        static ChangeKey from(DBTreatyChange row) {
            return new ChangeKey(
                    row.getTimestamp(),
                    row.getAction(),
                    row.getTreatyType(),
                    row.getFromAllianceId(),
                    row.getToAllianceId(),
                    row.getTurnsRemaining()
            );
        }
    }

    private record TreatyPairKey(int fromAllianceId, int toAllianceId) {
        static TreatyPairKey from(int leftAllianceId, int rightAllianceId) {
            return leftAllianceId <= rightAllianceId
                    ? new TreatyPairKey(leftAllianceId, rightAllianceId)
                    : new TreatyPairKey(rightAllianceId, leftAllianceId);
        }
    }

    private record ActiveTreatyState(TreatyType treatyType, long turnEnds) {
        boolean isPermanent() {
            return turnEnds == Long.MAX_VALUE;
        }
    }

    public record TreatyImportResult(int importedChangeCount, int minDay, int maxDay) {
    }

    public record TreatyResetResult(int deletedChangeCount) {
    }
}

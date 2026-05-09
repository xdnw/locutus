package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TreatyVisRuntimeLegacyTreatyCheckpointImportService {
    private static final Path DEFAULT_INCREMENTAL_STATE_PATH = Path.of("data", "incremental_state.json");
    private static final long MILLIS_PER_TREATY_TURN = 2L * 60L * 60L * 1000L;

    private final Path stagedImportRoot;
    private final TreatyVisRuntimeRepository repository;
    private final ObjectMapper json;

    public TreatyVisRuntimeLegacyTreatyCheckpointImportService() {
        this(
                TreatyVisRuntimeBootstrapService.DEFAULT_IMPORT_ROOT,
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
            TreatyVisRuntimeSerializers.JSON
        );
    }

    TreatyVisRuntimeLegacyTreatyCheckpointImportService(Path stagedImportRoot, TreatyVisRuntimeRepository repository, ObjectMapper json) {
        this.stagedImportRoot = stagedImportRoot.toAbsolutePath().normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.json = Objects.requireNonNull(json, "json");
    }

    public void assertImportTargetAvailable(boolean replaceExisting) throws IOException {
        repository.ensureTables();
        if (!replaceExisting && repository.countTreatyCheckpoints() > 0) {
            throw new IOException("Historical treaty checkpoint import already exists. Run the runtime bootstrap reset first or rerun with replace enabled.");
        }
    }

    public TreatyCheckpointImportResult importHistoricalTreatyCheckpoint(boolean replaceExisting) throws IOException {
        assertImportTargetAvailable(replaceExisting);
        if (replaceExisting) {
            repository.clearTreatyCheckpoints();
        }

        TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint = loadTreatyCheckpoint();
        repository.replaceTreatyCheckpoint(checkpoint);
        return new TreatyCheckpointImportResult(checkpoint.day(), checkpoint.sourceCursorMs(), checkpoint.entries().size());
    }

    public TreatyCheckpointResetResult resetImportedHistoricalTreatyCheckpoint() {
        return new TreatyCheckpointResetResult(repository.clearTreatyCheckpoints());
    }

    private TreatyVisRuntimeRepository.TreatyCheckpoint loadTreatyCheckpoint() throws IOException {
        Path statePath = stagedImportRoot.resolve(DEFAULT_INCREMENTAL_STATE_PATH);
        if (!Files.isRegularFile(statePath)) {
            throw new IOException("Staged incremental treaty state is missing: " + statePath);
        }

        Map<String, Object> root = json.readValue(statePath.toFile(), new TypeReference<>() {
        });
        Map<String, Object> eventStore = requireMap(root.get("event_store"), "event_store");
        long sourceCursorMs = parseInstantMillis(requireString(eventStore.get("max_timestamp"), "event_store.max_timestamp"));
        int checkpointDay = Math.toIntExact(Instant.ofEpochMilli(sourceCursorMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay());
        Map<String, Object> active = requireMap(eventStore.get("active"), "event_store.active");

        List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> pairEntry : active.entrySet()) {
            int[] pair = parsePair(pairEntry.getKey());
            Map<String, Object> byType = requireMap(pairEntry.getValue(), "event_store.active[" + pairEntry.getKey() + "]");
            for (Map.Entry<String, Object> treatyEntry : byType.entrySet()) {
                Map<String, Object> state = requireMap(treatyEntry.getValue(), "event_store.active[" + pairEntry.getKey() + "][" + treatyEntry.getKey() + "]");
                TreatyType treatyType = TreatyType.parse(requireTreatyType(treatyEntry.getKey(), state));
                int turnsRemaining = parseTurnsRemaining(sourceCursorMs, state.get("expires_at"));
                entries.add(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(pair[0], pair[1], treatyType, turnsRemaining));
            }
        }

        return new TreatyVisRuntimeRepository.TreatyCheckpoint(checkpointDay, sourceCursorMs, List.copyOf(entries));
    }

    private static String requireTreatyType(String treatyTypeKey, Map<String, Object> state) throws IOException {
        String fromState = stringValue(state.get("treaty_type"));
        String treatyType = fromState.isBlank() ? treatyTypeKey : fromState;
        if (treatyType.isBlank()) {
            throw new IOException("Historical treaty checkpoint entry is missing treaty_type");
        }
        return treatyType;
    }

    private static int[] parsePair(String pairKey) throws IOException {
        String[] parts = pairKey.split(":", -1);
        if (parts.length != 2) {
            throw new IOException("Invalid historical treaty checkpoint pair key: " + pairKey);
        }
        try {
            int left = Integer.parseInt(parts[0]);
            int right = Integer.parseInt(parts[1]);
            if (left <= 0 || right <= 0) {
                throw new IOException("Historical treaty checkpoint pair ids must be positive: " + pairKey);
            }
            return new int[] {left, right};
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid historical treaty checkpoint pair key: " + pairKey, ex);
        }
    }

    private static int parseTurnsRemaining(long checkpointCursorMs, Object expiresAtValue) throws IOException {
        String expiresAt = stringValue(expiresAtValue);
        if (expiresAt.isBlank()) {
            return -1;
        }
        long expiresAtMs = parseInstantMillis(expiresAt);
        long delta = expiresAtMs - checkpointCursorMs;
        if (delta <= 0L) {
            return 0;
        }
        long roundedUpTurns = (delta + MILLIS_PER_TREATY_TURN - 1L) / MILLIS_PER_TREATY_TURN;
        return Math.toIntExact(roundedUpTurns);
    }

    private static Map<String, Object> requireMap(Object value, String label) throws IOException {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IOException("Missing or invalid " + label);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) raw;
        return typed;
    }

    private static String requireString(Object value, String label) throws IOException {
        String result = stringValue(value);
        if (result.isBlank()) {
            throw new IOException("Missing " + label);
        }
        return result;
    }

    private static long parseInstantMillis(String value) throws IOException {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid timestamp: " + value, ex);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record TreatyCheckpointImportResult(int checkpointDay, long sourceCursorMs, int activeEntryCount) {
    }

    public record TreatyCheckpointResetResult(int deletedCheckpointCount) {
    }
}
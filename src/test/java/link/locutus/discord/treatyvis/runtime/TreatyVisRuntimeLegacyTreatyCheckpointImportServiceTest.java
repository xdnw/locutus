package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreatyVisRuntimeLegacyTreatyCheckpointImportServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void importsCorrectedTreatyCheckpointFromIncrementalState() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        Path incrementalStatePath = stagedImportRoot.resolve(Path.of("data", "incremental_state.json"));
        Files.createDirectories(incrementalStatePath.getParent());
        Map<String, Object> extensionState = new LinkedHashMap<>();
        extensionState.put("treaty_type", "EXTENSION");
        extensionState.put("opened_at", "2026-02-20T00:00:00Z");
        extensionState.put("expires_at", null);
        extensionState.put("last_event_id", "event-b");

        Map<String, Object> active = new LinkedHashMap<>();
        active.put("9:12", Map.of(
                "MDP", Map.of(
                        "treaty_type", "MDP",
                        "opened_at", "2026-03-01T00:00:00Z",
                        "expires_at", "2026-03-03T18:04:08.958000Z",
                        "last_event_id", "event-a"
                )
        ));
        active.put("4:7", Map.of("EXTENSION", extensionState));

        json.writeValue(incrementalStatePath.toFile(), Map.of(
                "schema_version", 1,
                "event_store", Map.of(
                        "max_timestamp", "2026-03-03T14:04:08.958000Z",
                        "active", active
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyCheckpointImportService service = new TreatyVisRuntimeLegacyTreatyCheckpointImportService(
                    stagedImportRoot,
                    repository,
                    json
            );

            TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointImportResult result = service.importHistoricalTreatyCheckpoint(false);

            assertEquals(Math.toIntExact(LocalDate.parse("2026-03-03").toEpochDay()), result.checkpointDay());
            assertEquals(Instant.parse("2026-03-03T14:04:08.958000Z").toEpochMilli(), result.sourceCursorMs());
            assertEquals(2, result.activeEntryCount());

            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint = repository.loadLatestTreatyCheckpoint();
            assertNotNull(checkpoint);
            assertEquals(result.checkpointDay(), checkpoint.day());
            assertEquals(result.sourceCursorMs(), checkpoint.sourceCursorMs());
            assertEquals(List.of(
                    new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4, 7, TreatyType.EXTENSION, -1),
                    new TreatyVisRuntimeRepository.TreatyCheckpointEntry(9, 12, TreatyType.MDP, 2)
            ), checkpoint.entries());

            TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointResetResult resetResult = service.resetImportedHistoricalTreatyCheckpoint();
            assertEquals(1, resetResult.deletedCheckpointCount());
            assertEquals(0, repository.countTreatyCheckpoints());
        }
    }
}
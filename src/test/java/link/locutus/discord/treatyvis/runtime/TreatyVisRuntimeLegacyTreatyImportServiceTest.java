package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.TreatyChangeAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeLegacyTreatyImportServiceTest {
    private final ObjectMapper msgpack = new ObjectMapper(new MessagePackFactory());

    @TempDir
    Path tempDir;

    @Test
        void importsUnifiedTreatyChangesFromLegacySourcesAndSynthesizesMissingExpiry() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import");
        Path publicDataRoot = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(publicDataRoot);

        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of(Map.of("file", "treaty_changes_reconciled_window_2025-01.msgpack"))
        ));
        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_window_2025-01.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "MDP",
                        "from_alliance_id", 1,
                        "to_alliance_id", 2,
                        "time_remaining_turns", 12,
                        "source", "endpoint_delta"
                ),
                Map.of(
                        "timestamp", "2025-01-01T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "NAP",
                        "from_alliance_id", 6,
                        "to_alliance_id", 7,
                        "time_remaining_turns", 1,
                        "source", "endpoint_delta"
                )
        ));
        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2025-01-02T00:00:00Z",
                        "action", "ended",
                        "treaty_type", "UNKNOWN",
                        "from_alliance_id", 1,
                        "to_alliance_id", 2,
                        "turns_remaining", -1,
                        "source", "current_truth_reconcile"
                ),
                Map.of(
                        "timestamp", "2025-01-03T00:00:00Z",
                        "action", "signed",
                        "treaty_type", "ODP",
                        "from_alliance_id", 4,
                        "to_alliance_id", 5,
                        "turns_remaining", -1,
                        "source", "endpoint_delta"
                ),
                Map.of(
                        "timestamp", "2025-01-04T00:00:00Z",
                        "action", "cancelled",
                        "treaty_type", "ODP",
                        "from_alliance_id", 4,
                        "to_alliance_id", 5,
                        "turns_remaining", -1,
                        "source", "current_truth_reconcile"
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            db.executeStmt("CREATE TABLE IF NOT EXISTS TREATY_CHANGES (timestamp BIGINT NOT NULL, action TINYINT NOT NULL, treaty_type TINYINT NOT NULL, from_alliance_id INT NOT NULL, to_alliance_id INT NOT NULL, turns_remaining INT NOT NULL)");
                        db.executeStmt("INSERT INTO TREATY_CHANGES(timestamp, action, treaty_type, from_alliance_id, to_alliance_id, turns_remaining) VALUES(" + Instant.parse("2025-01-03T00:00:00Z").toEpochMilli() + ", " + TreatyChangeAction.SIGNED.ordinal() + ", " + TreatyType.ODP.ordinal() + ", 4, 5, -1)");
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService service = new TreatyVisRuntimeLegacyTreatyImportService(
                    stagedImportRoot,
                    repository,
                    msgpack,
                    () -> Instant.parse("2025-01-05T00:00:00Z").toEpochMilli()
            );

            TreatyVisRuntimeLegacyTreatyImportService.TreatyImportResult result = service.importHistoricalTreaties(false);

            assertEquals(6, result.importedChangeCount());
            assertEquals(Math.toIntExact(LocalDate.parse("2025-01-01").toEpochDay()), result.minDay());
            assertEquals(Math.toIntExact(LocalDate.parse("2025-01-04").toEpochDay()), result.maxDay());

            List<DBTreatyChange> merged = repository.loadUnifiedTreatyChangesSince(0);
            assertEquals(6, merged.size());
            assertEquals(TreatyChangeAction.SIGNED, merged.get(0).getAction());
            assertEquals(TreatyType.MDP, merged.get(0).getTreatyType());
            assertTrue(merged.stream().anyMatch(row -> row.getAction() == TreatyChangeAction.ENDED && row.getTreatyType() == TreatyType.MDP));
            assertTrue(merged.stream().anyMatch(row -> row.getAction() == TreatyChangeAction.EXPIRED && row.getTreatyType() == TreatyType.NAP));
            assertTrue(merged.stream().anyMatch(row -> row.getAction() == TreatyChangeAction.SIGNED && row.getTreatyType() == TreatyType.ODP));
            assertTrue(merged.stream().anyMatch(row -> row.getAction() == TreatyChangeAction.CANCELLED && row.getTreatyType() == TreatyType.ODP));
            assertFalse(merged.stream().anyMatch(row -> row.getAction() == TreatyChangeAction.EXPIRED && row.getTreatyType() == TreatyType.ODP));

            TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
            assertTrue(bootstrapState.treatyImportComplete());
            assertEquals(result.minDay(), bootstrapState.importedTreatyMinDay());
            assertEquals(result.maxDay(), bootstrapState.importedTreatyMaxDay());

            TreatyVisRuntimeLegacyTreatyImportService.TreatyResetResult resetResult = service.resetImportedHistoricalTreaties();
            assertEquals(6, resetResult.deletedChangeCount());
            assertEquals(0, repository.loadUnifiedTreatyChangesSince(0).size());
            assertFalse(repository.loadBootstrapState().treatyImportComplete());
        }
    }

    @Test
    void infersUnknownSignedTreatyTypeFromLaterConcreteTerminal() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-unknown-signed");
        Path publicDataRoot = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(publicDataRoot);

        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of()
        ));
        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2022-08-15T10:12:58Z",
                        "action", "signed",
                        "treaty_type", "UNKNOWN",
                        "from_alliance_id", 10,
                        "to_alliance_id", 11,
                        "turns_remaining", -1,
                        "source", "archive_delta"
                ),
                Map.of(
                        "timestamp", "2022-08-20T10:12:58Z",
                        "action", "ended",
                        "treaty_type", "MDP",
                        "from_alliance_id", 10,
                        "to_alliance_id", 11,
                        "turns_remaining", -1,
                        "source", "current_truth_reconcile"
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-unknown-signed.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService service = new TreatyVisRuntimeLegacyTreatyImportService(
                    stagedImportRoot,
                    repository,
                    msgpack,
                    () -> Instant.parse("2022-08-21T00:00:00Z").toEpochMilli()
            );

            service.importHistoricalTreaties(false);
            List<DBTreatyChange> merged = repository.loadUnifiedTreatyChangesSince(0);

            assertEquals(2, merged.size());
            assertEquals(TreatyChangeAction.SIGNED, merged.get(0).getAction());
            assertEquals(TreatyType.MDP, merged.get(0).getTreatyType());
            assertEquals(TreatyChangeAction.ENDED, merged.get(1).getAction());
            assertEquals(TreatyType.MDP, merged.get(1).getTreatyType());
        }
    }

    @Test
    void skipsAmbiguousUnknownSignedTreatyType() throws Exception {
        Path stagedImportRoot = tempDir.resolve("legacy-import-ambiguous-unknown");
        Path publicDataRoot = stagedImportRoot.resolve(Path.of("public", "data"));
        Files.createDirectories(publicDataRoot);

        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_index.msgpack").toFile(), Map.of(
                "schema_version", 1,
                "delta_file", "treaty_changes_reconciled_delta.msgpack",
                "windows", List.of()
        ));
        msgpack.writeValue(publicDataRoot.resolve("treaty_changes_reconciled_delta.msgpack").toFile(), List.of(
                Map.of(
                        "timestamp", "2022-08-15T10:12:58Z",
                        "action", "signed",
                        "treaty_type", "UNKNOWN",
                        "from_alliance_id", 12,
                        "to_alliance_id", 13,
                        "turns_remaining", -1,
                        "source", "archive_delta"
                ),
                Map.of(
                        "timestamp", "2022-08-20T10:12:58Z",
                        "action", "signed",
                        "treaty_type", "NAP",
                        "from_alliance_id", 12,
                        "to_alliance_id", 13,
                        "turns_remaining", -1,
                        "source", "endpoint_delta"
                )
        ));

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-ambiguous-unknown.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            TreatyVisRuntimeLegacyTreatyImportService service = new TreatyVisRuntimeLegacyTreatyImportService(
                    stagedImportRoot,
                    repository,
                    msgpack,
                    () -> Instant.parse("2022-08-21T00:00:00Z").toEpochMilli()
            );

            service.importHistoricalTreaties(false);
            List<DBTreatyChange> merged = repository.loadUnifiedTreatyChangesSince(0);

            assertEquals(1, merged.size());
            assertEquals(TreatyChangeAction.SIGNED, merged.get(0).getAction());
            assertEquals(TreatyType.NAP, merged.get(0).getTreatyType());
        }
    }
}

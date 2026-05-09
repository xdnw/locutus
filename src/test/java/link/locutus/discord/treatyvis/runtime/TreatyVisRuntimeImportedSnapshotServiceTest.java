package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.TreatyChangeAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreatyVisRuntimeImportedSnapshotServiceTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void buildsImportedRuntimeInputFromCheckpointAndRuntimeTables() throws Exception {
        int baseDay = Math.toIntExact(LocalDate.parse("2026-03-03").toEpochDay());
        long cursorMs = Instant.parse("2026-03-03T14:04:08.958000Z").toEpochMilli();
        byte[] alphaHash = com.google.common.hash.HashCode.fromString("aa".repeat(32)).asBytes();
        byte[] betaHash = com.google.common.hash.HashCode.fromString("bb".repeat(32)).asBytes();
        byte[] alphaIcon = new byte[] {1, 2, 3};
        byte[] betaIcon = new byte[] {4, 5, 6};

        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.markHistoricalTreatyImportComplete(baseDay, baseDay + 2);
            repository.markHistoricalScoreImportComplete(baseDay - 10, baseDay + 2);
            repository.markHistoricalFlagImportComplete(baseDay - 10, baseDay + 2);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    baseDay + 2,
                    cursorMs + 172_800_000L,
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 9123, TreatyType.MDOAP, -1))
            ));
            repository.replaceUnifiedTreatyChanges(List.of(
                    new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                            Instant.parse("2026-03-03T01:00:00Z").toEpochMilli(),
                            TreatyChangeAction.SIGNED,
                            TreatyType.MDP,
                            4729,
                            881,
                            -1
                    ),
                    new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                            Instant.parse("2026-03-04T02:00:00Z").toEpochMilli(),
                            TreatyChangeAction.ENDED,
                            TreatyType.MDP,
                            4729,
                            881,
                            -1
                    )
            ));
            repository.replaceTopNScoreRows(Map.of(
                    baseDay, TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 182340),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(881, 175210)
                    )),
                    baseDay + 1, TreatyVisRuntimeScoreRowCodec.encode(List.of(
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(4729, 183100),
                            new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(9123, 171000)
                    ))
            ));
            repository.replaceFlagChanges(List.of(
                    new TreatyVisRuntimeRepository.FlagChangeRow(4729, baseDay, alphaHash),
                    new TreatyVisRuntimeRepository.FlagChangeRow(881, baseDay + 1, null),
                    new TreatyVisRuntimeRepository.FlagChangeRow(9123, baseDay + 2, betaHash)
            ));
            repository.replaceFlagIcons(List.of(
                    new TreatyVisRuntimeRepository.FlagIconRow(alphaHash, alphaIcon),
                    new TreatyVisRuntimeRepository.FlagIconRow(betaHash, betaIcon)
            ));
            repository.replaceFlagAtlasState(List.of(
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(alphaHash, 11),
                    new TreatyVisRuntimeRepository.FlagAtlasStateRow(betaHash, 29)
            ));

            TreatyVisRuntimeImportedSnapshotService service = new TreatyVisRuntimeImportedSnapshotService(repository);
            Map<Integer, String> allianceNames = Map.of(
                    4729, "Rose",
                    881, "Guardian",
                    9123, "Eclipse"
            );
            TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot imported = service.buildImportedSnapshot(
                    allianceId -> allianceNames.getOrDefault(allianceId, "AA:" + allianceId)
            );

            assertNotNull(imported);
            assertEquals(baseDay, imported.input().baseDay());
            assertEquals(List.of(), imported.input().activeTreaties());
            TreatyVisRuntimePayload payload = new TreatyVisRuntimeBuilder().build(imported.input());

            assertEquals(baseDay, payload.baseDay());
            assertEquals(List.of(), payload.initialState().activeEdgeIndexes());
            assertEquals(List.of(0, 1), payload.treatyChanges().days());
            assertEquals(List.of(0, 1, 2), payload.treatyChanges().rowOffsets());
            assertEquals(List.of(0, 0), payload.treatyChanges().edgeIndexes());
            assertEquals(List.of(1, 3), payload.treatyChanges().actions());
            assertEquals(List.of(new TreatyVisRuntimeInput.AllianceFlag(4729, 1)), imported.input().initialFlags());
            assertEquals(List.of(
                    new TreatyVisRuntimeInput.FlagChange(1, 881, 0),
                    new TreatyVisRuntimeInput.FlagChange(2, 9123, 2)
            ), imported.input().flagChanges());
            assertEquals(List.of(1), payload.initialState().flagAllianceIndexes());
            assertEquals(List.of(1), payload.initialState().flagIndexes());
            assertEquals(List.of(1, 2), payload.flagChanges().days());
            assertEquals(List.of(0, 1, 2), payload.flagChanges().rowOffsets());
            assertEquals(List.of(0, 2), payload.flagChanges().allianceIndexes());
            assertEquals(List.of(0, 2), payload.flagChanges().flagIndexes());
            assertEquals(List.of(1, 0), payload.initialState().scoreAllianceIndexes());
            assertEquals(List.of(182340, 175210), payload.initialState().scoreQuantized());
            assertEquals(List.of(1), payload.scoreSnapshots().days());
            assertEquals(List.of(0, 2), payload.scoreSnapshots().rowOffsets());
            assertEquals(List.of(1, 2), payload.scoreSnapshots().allianceIndexes());
            assertEquals(List.of(183100, 171000), payload.scoreSnapshots().scoresQuantized());
                        assertArrayEquals(alphaIcon, imported.flagIconBytesByIndex().get(1));
                        assertArrayEquals(betaIcon, imported.flagIconBytesByIndex().get(2));
        }
    }
}
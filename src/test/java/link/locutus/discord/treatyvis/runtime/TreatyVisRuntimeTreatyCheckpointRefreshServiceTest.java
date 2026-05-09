package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.TreatyChangeAction;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyVisRuntimeTreatyCheckpointRefreshServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsCheckpointWhenReplayMatchesAndStrideIsNotDue() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            long checkpointCursorMs = TimeUtil.getTimeFromDay(10);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    10,
                    checkpointCursorMs,
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, -1))
            ));
            TreatyVisRuntimeTreatyCheckpointRefreshService service = new TreatyVisRuntimeTreatyCheckpointRefreshService(
                    repository,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(1, 2, TreatyType.MDP, -1)),
                    () -> TimeUtil.getTimeFromDay(12),
                    7,
                    (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> {
                    }
            );

            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult result = service.refreshTreatyCheckpoint();

            assertTrue(result.executed());
            assertTrue(result.matchesCurrentTruth());
            assertFalse(result.checkpointDue());
            assertFalse(result.driftDetected());
            assertFalse(result.checkpointWritten());
            assertEquals(0, result.repairChangeCount());
            assertEquals(10, result.checkpointDay());
            assertEquals(checkpointCursorMs, repository.loadLatestTreatyCheckpoint().sourceCursorMs());
        }
    }

    @Test
    void writesStrideCheckpointFromCurrentTruth() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-stride.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    10,
                    TimeUtil.getTimeFromDay(10),
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, -1))
            ));
            long currentTimeMs = TimeUtil.getTimeFromDay(17);
            TreatyVisRuntimeTreatyCheckpointRefreshService service = new TreatyVisRuntimeTreatyCheckpointRefreshService(
                    repository,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(1, 2, TreatyType.MDP, -1)),
                    () -> currentTimeMs,
                    7,
                    (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> {
                    }
            );

            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult result = service.refreshTreatyCheckpoint();

            assertTrue(result.matchesCurrentTruth());
            assertTrue(result.checkpointDue());
            assertFalse(result.driftDetected());
            assertTrue(result.checkpointWritten());
            assertEquals(0, result.repairChangeCount());
            assertEquals(17, result.checkpointDay());
            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint = repository.loadLatestTreatyCheckpoint();
            assertEquals(17, checkpoint.day());
            assertEquals(currentTimeMs, checkpoint.sourceCursorMs());
            assertEquals(1, checkpoint.entries().size());
        }
    }

    @Test
    void repairsCheckpointFromCurrentTruthWhenReplayDrifts() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-drift.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            List<TreatyVisRuntimeTreatyCheckpointRefreshService.RepairChange> repairs = new ArrayList<>();
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    10,
                    TimeUtil.getTimeFromDay(10),
                    List.of()
            ));
            long currentTimeMs = TimeUtil.getTimeFromDay(12);
            TreatyVisRuntimeTreatyCheckpointRefreshService service = new TreatyVisRuntimeTreatyCheckpointRefreshService(
                    repository,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(1, 2, TreatyType.MDP, -1)),
                    () -> currentTimeMs,
                7,
                (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> repairs.add(
                    new TreatyVisRuntimeTreatyCheckpointRefreshService.RepairChange(timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining)
                )
            );

            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult result = service.refreshTreatyCheckpoint();

            assertFalse(result.matchesCurrentTruth());
            assertFalse(result.checkpointDue());
            assertTrue(result.driftDetected());
            assertTrue(result.checkpointWritten());
                assertEquals(0, result.repairChangeCount());
                assertEquals(List.of(), repairs);
            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint = repository.loadLatestTreatyCheckpoint();
            assertEquals(12, checkpoint.day());
            assertEquals(currentTimeMs, checkpoint.sourceCursorMs());
            assertEquals(List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, -1)), checkpoint.entries());
        }
    }

    @Test
    void replaysMergedTreatyChangesBeforeComparingCurrentTruth() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-replay.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            long checkpointCursorMs = TimeUtil.getTimeFromDay(10);
            repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    10,
                    checkpointCursorMs,
                    List.of()
            ));
                repository.replaceUnifiedTreatyChanges(List.of(new TreatyVisRuntimeRepository.UnifiedTreatyChangeRow(
                    checkpointCursorMs + 1,
                    TreatyChangeAction.SIGNED,
                    TreatyType.MDP,
                    1,
                    2,
                    -1
            )));
            TreatyVisRuntimeTreatyCheckpointRefreshService service = new TreatyVisRuntimeTreatyCheckpointRefreshService(
                    repository,
                    () -> List.of(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(1, 2, TreatyType.MDP, -1)),
                    () -> TimeUtil.getTimeFromDay(12),
                    7,
                    (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> {
                    }
            );

            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult result = service.refreshTreatyCheckpoint();

            assertTrue(result.matchesCurrentTruth());
            assertFalse(result.driftDetected());
            assertFalse(result.checkpointWritten());
                assertEquals(0, result.repairChangeCount());
            assertEquals(1, result.replayedActiveCount());
        }
    }

            @Test
            void synthesizesMissingExpiryRowsBeforeRefreshingCheckpoint() throws Exception {
            try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime-expiry.db").toFile())) {
                TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
                List<TreatyVisRuntimeTreatyCheckpointRefreshService.RepairChange> repairs = new ArrayList<>();
                long checkpointCursorMs = TimeUtil.getTimeFromDay(10);
                repository.replaceTreatyCheckpoint(new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    10,
                    checkpointCursorMs,
                    List.of(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(1, 2, TreatyType.MDP, 1))
                ));
                long expectedExpiryTimestamp = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(checkpointCursorMs) + 1);
                TreatyVisRuntimeTreatyCheckpointRefreshService service = new TreatyVisRuntimeTreatyCheckpointRefreshService(
                    repository,
                    List::of,
                    () -> TimeUtil.getTimeFromDay(12),
                    7,
                    (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> repairs.add(
                        new TreatyVisRuntimeTreatyCheckpointRefreshService.RepairChange(timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining)
                    )
                );

                TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult result = service.refreshTreatyCheckpoint();

                assertFalse(result.matchesCurrentTruth());
                assertTrue(result.driftDetected());
                assertTrue(result.checkpointWritten());
                assertEquals(1, result.repairChangeCount());
                assertEquals(List.of(
                    new TreatyVisRuntimeTreatyCheckpointRefreshService.RepairChange(expectedExpiryTimestamp, TreatyChangeAction.EXPIRED, TreatyType.MDP, 1, 2, 0)
                ), repairs);
                assertEquals(List.of(), repository.loadLatestTreatyCheckpoint().entries());
            }
            }
}

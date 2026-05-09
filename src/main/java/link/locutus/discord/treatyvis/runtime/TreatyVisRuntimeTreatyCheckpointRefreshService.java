package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.TreatyChangeAction;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class TreatyVisRuntimeTreatyCheckpointRefreshService {
    public static final int DEFAULT_CHECKPOINT_STRIDE_DAYS = 7;

    private final TreatyVisRuntimeRepository repository;
    private final Supplier<List<CurrentTreatyRow>> currentTreatySupplier;
    private final LongSupplier currentTimeMsSupplier;
    private final int checkpointStrideDays;
    private final RepairWriter repairWriter;

    public TreatyVisRuntimeTreatyCheckpointRefreshService() {
        this(
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
                TreatyVisRuntimeTreatyCheckpointRefreshService::loadCurrentTreaties,
                System::currentTimeMillis,
                DEFAULT_CHECKPOINT_STRIDE_DAYS,
                (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) ->
                    Locutus.imp().getNationDB().addUnifiedTreatyChange(timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining)
        );
    }

    TreatyVisRuntimeTreatyCheckpointRefreshService(
            TreatyVisRuntimeRepository repository,
            Supplier<List<CurrentTreatyRow>> currentTreatySupplier,
            LongSupplier currentTimeMsSupplier,
            int checkpointStrideDays,
            RepairWriter repairWriter
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.currentTreatySupplier = Objects.requireNonNull(currentTreatySupplier, "currentTreatySupplier");
        this.currentTimeMsSupplier = Objects.requireNonNull(currentTimeMsSupplier, "currentTimeMsSupplier");
        if (checkpointStrideDays <= 0) {
            throw new IllegalArgumentException("checkpointStrideDays must be positive");
        }
        this.checkpointStrideDays = checkpointStrideDays;
        this.repairWriter = Objects.requireNonNull(repairWriter, "repairWriter");
    }

    public TreatyCheckpointRefreshResult refreshTreatyCheckpoint() throws IOException {
        TreatyVisRuntimeRepository.TreatyCheckpoint latestCheckpoint = repository.loadLatestTreatyCheckpoint();
        if (latestCheckpoint == null) {
            return TreatyCheckpointRefreshResult.skipped("No treaty checkpoint is available for runtime ingestion.");
        }

        TreatyCheckpointComparison comparison = compareCheckpointToCurrentTruth(latestCheckpoint);
        List<RepairChange> repairChanges = buildRepairChanges(comparison);
        writeRepairChanges(repairChanges);
        int currentDay = comparison.currentDay();
        boolean matchesCurrentTruth = comparison.matchesCurrentTruth() && repairChanges.isEmpty();
        boolean checkpointDue = currentDay >= latestCheckpoint.day() + checkpointStrideDays;
        boolean driftDetected = !repairChanges.isEmpty() || !comparison.matchesCurrentTruth();
        boolean shouldWriteCheckpoint = checkpointDue || driftDetected;
        TreatyVisRuntimeRepository.TreatyCheckpoint refreshedCheckpoint = latestCheckpoint;
        if (shouldWriteCheckpoint) {
            refreshedCheckpoint = new TreatyVisRuntimeRepository.TreatyCheckpoint(
                    currentDay,
                    comparison.currentTimeMs(),
                    checkpointEntriesFromCurrentTruth(comparison.currentTreaties())
            );
            repository.replaceTreatyCheckpoint(refreshedCheckpoint);
        }

        return TreatyCheckpointRefreshResult.completed(
                latestCheckpoint.day(),
                refreshedCheckpoint.day(),
                currentDay,
                comparison.replayedActiveCount(),
                comparison.currentActiveCount(),
                matchesCurrentTruth,
                checkpointDue,
                driftDetected,
                shouldWriteCheckpoint,
                repairChanges.size()
        );
    }

    TreatyCheckpointComparison compareCheckpointToCurrentTruth(TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint) {
        long currentTimeMs = currentTimeMsSupplier.getAsLong();
        int currentDay = Math.toIntExact(TimeUtil.getDay(currentTimeMs));
        long currentTurn = TimeUtil.getTurn(currentTimeMs);
            ReplayResult replay = replayFrom(checkpoint, currentTurn);
            Map<TreatyPairKey, ActiveTreatyState> replayedState = replay.activeTreatiesByPair();
        Map<TreatyPairKey, CurrentTreatyRow> currentTruth = currentTreatyTruthByPair();
            boolean matchesCurrentTruth = replay.expiredRepairChanges().isEmpty() && matchesCurrentTruth(replayedState, currentTruth, currentTurn);
        return new TreatyCheckpointComparison(
                currentTimeMs,
                currentDay,
                replayedState.size(),
                currentTruth.size(),
                matchesCurrentTruth,
                List.copyOf(currentTruth.values()),
                Map.copyOf(replayedState),
                List.copyOf(replay.expiredRepairChanges())
        );
    }

            private ReplayResult replayFrom(
            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint,
            long currentTurn
    ) {
        Map<TreatyPairKey, ActiveTreatyState> activeByPair = new LinkedHashMap<>();
            List<RepairChange> expiredRepairChanges = new ArrayList<>();
        long checkpointTurn = TimeUtil.getTurn(checkpoint.sourceCursorMs());
        for (TreatyVisRuntimeRepository.TreatyCheckpointEntry entry : checkpoint.entries()) {
            TreatyPairKey key = TreatyPairKey.fromIds(entry.fromAllianceId(), entry.toAllianceId());
            long turnEnds = entry.turnsRemaining() < 0 ? Long.MAX_VALUE : checkpointTurn + entry.turnsRemaining();
            activeByPair.put(key, new ActiveTreatyState(entry.treatyType(), turnEnds));
        }

        for (DBTreatyChange change : repository.loadUnifiedTreatyChangesSince(checkpoint.sourceCursorMs())) {
            if (change.getTimestamp() <= checkpoint.sourceCursorMs()) {
                continue;
            }
            TreatyPairKey key = TreatyPairKey.fromIds(change.getFromAllianceId(), change.getToAllianceId());
            TreatyChangeAction action = change.getAction();
            if (action == TreatyChangeAction.SIGNED || action == TreatyChangeAction.EXTENDED) {
                long turnEnds = change.getTurnsRemaining() < 0
                        ? Long.MAX_VALUE
                        : TimeUtil.getTurn(change.getTimestamp()) + change.getTurnsRemaining();
                activeByPair.put(key, new ActiveTreatyState(change.getTreatyType(), turnEnds));
            } else if (action == TreatyChangeAction.CANCELLED || action == TreatyChangeAction.EXPIRED || action == TreatyChangeAction.ENDED) {
                ActiveTreatyState state = activeByPair.get(key);
                if (state != null && state.treatyType() == change.getTreatyType()) {
                    activeByPair.remove(key);
                }
            }
        }

        List<TreatyPairKey> expiredKeys = new ArrayList<>();
        for (Map.Entry<TreatyPairKey, ActiveTreatyState> entry : activeByPair.entrySet()) {
            ActiveTreatyState state = entry.getValue();
            if (!state.isPermanent() && state.turnEnds() <= currentTurn) {
                expiredKeys.add(entry.getKey());
                expiredRepairChanges.add(new RepairChange(
                        TimeUtil.getTimeFromTurn(state.turnEnds()),
                        TreatyChangeAction.EXPIRED,
                        state.treatyType(),
                        entry.getKey().leftAllianceId(),
                        entry.getKey().rightAllianceId(),
                        0
                ));
            }
        }
        for (TreatyPairKey expiredKey : expiredKeys) {
            activeByPair.remove(expiredKey);
        }
        expiredRepairChanges.sort(Comparator.comparingLong(RepairChange::timestamp)
                .thenComparingInt(RepairChange::fromAllianceId)
                .thenComparingInt(RepairChange::toAllianceId)
                .thenComparing(change -> change.treatyType().ordinal())
                .thenComparing(change -> change.action().ordinal()));
        return new ReplayResult(Map.copyOf(activeByPair), List.copyOf(expiredRepairChanges));
    }

    private Map<TreatyPairKey, CurrentTreatyRow> currentTreatyTruthByPair() {
        Map<TreatyPairKey, CurrentTreatyRow> currentTruth = new LinkedHashMap<>();
        for (CurrentTreatyRow row : currentTreatySupplier.get()) {
            currentTruth.put(TreatyPairKey.fromIds(row.fromAllianceId(), row.toAllianceId()), row.normalized());
        }
        return currentTruth;
    }

    private static boolean matchesCurrentTruth(
            Map<TreatyPairKey, ActiveTreatyState> replayedState,
            Map<TreatyPairKey, CurrentTreatyRow> currentTruth,
            long currentTurn
    ) {
        if (replayedState.size() != currentTruth.size()) {
            return false;
        }
        for (Map.Entry<TreatyPairKey, ActiveTreatyState> entry : replayedState.entrySet()) {
            CurrentTreatyRow current = currentTruth.get(entry.getKey());
            if (current == null || current.treatyType() != entry.getValue().treatyType()) {
                return false;
            }
            int replayTurnsRemaining = entry.getValue().isPermanent()
                    ? -1
                    : Math.max(0, Math.toIntExact(entry.getValue().turnEnds() - currentTurn));
            if (current.turnsRemaining() != replayTurnsRemaining) {
                return false;
            }
        }
        return true;
    }

    private static List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> checkpointEntriesFromCurrentTruth(
            List<CurrentTreatyRow> currentTruth
    ) {
        return currentTruth.stream()
                .map(row -> new TreatyVisRuntimeRepository.TreatyCheckpointEntry(
                        row.fromAllianceId(),
                        row.toAllianceId(),
                        row.treatyType(),
                        row.turnsRemaining()
                ))
                .sorted(Comparator
                        .comparingInt(TreatyVisRuntimeRepository.TreatyCheckpointEntry::fromAllianceId)
                        .thenComparingInt(TreatyVisRuntimeRepository.TreatyCheckpointEntry::toAllianceId)
                        .thenComparing(entry -> entry.treatyType().ordinal()))
                .toList();
    }

            private List<RepairChange> buildRepairChanges(TreatyCheckpointComparison comparison) {
                return comparison.expiredRepairChanges();
            }

            private void writeRepairChanges(List<RepairChange> repairChanges) throws IOException {
                for (RepairChange repairChange : repairChanges) {
                    repairWriter.write(
                            repairChange.timestamp(),
                            repairChange.action(),
                            repairChange.treatyType(),
                            repairChange.fromAllianceId(),
                            repairChange.toAllianceId(),
                            repairChange.turnsRemaining()
                    );
                }
            }

    private static List<CurrentTreatyRow> loadCurrentTreaties() {
        long currentTurn = TimeUtil.getTurn();
        return Locutus.imp().getNationDB().getTreaties().stream()
                .filter(treaty -> !treaty.isPending())
                .map(treaty -> rowFromTreaty(treaty, currentTurn))
                .sorted(Comparator
                        .comparingInt(CurrentTreatyRow::fromAllianceId)
                        .thenComparingInt(CurrentTreatyRow::toAllianceId)
                        .thenComparing(row -> row.treatyType().ordinal()))
                .toList();
    }

    private static CurrentTreatyRow rowFromTreaty(Treaty treaty, long currentTurn) {
        int turnsRemaining = treaty.isPermanent()
                ? -1
                : Math.max(0, Math.toIntExact(treaty.getTurnEnds() - currentTurn));
        return new CurrentTreatyRow(treaty.getFromId(), treaty.getToId(), treaty.getType(), turnsRemaining);
    }

    public record TreatyCheckpointRefreshResult(
            boolean executed,
            String skippedReason,
            Integer previousCheckpointDay,
            Integer checkpointDay,
            int currentDay,
            int replayedActiveCount,
            int currentActiveCount,
            boolean matchesCurrentTruth,
            boolean checkpointDue,
            boolean driftDetected,
            boolean checkpointWritten,
            int repairChangeCount
    ) {
        static TreatyCheckpointRefreshResult skipped(String reason) {
            return new TreatyCheckpointRefreshResult(false, reason, null, null, 0, 0, 0, false, false, false, false, 0);
        }

        static TreatyCheckpointRefreshResult completed(
                int previousCheckpointDay,
                int checkpointDay,
                int currentDay,
                int replayedActiveCount,
                int currentActiveCount,
                boolean matchesCurrentTruth,
                boolean checkpointDue,
                boolean driftDetected,
                boolean checkpointWritten,
                int repairChangeCount
        ) {
            return new TreatyCheckpointRefreshResult(
                    true,
                    null,
                    previousCheckpointDay,
                    checkpointDay,
                    currentDay,
                    replayedActiveCount,
                    currentActiveCount,
                    matchesCurrentTruth,
                    checkpointDue,
                    driftDetected,
                    checkpointWritten,
                    repairChangeCount
            );
        }
    }

    public record CurrentTreatyRow(int fromAllianceId, int toAllianceId, TreatyType treatyType, int turnsRemaining) {
        CurrentTreatyRow normalized() {
            int normalizedTurnsRemaining = turnsRemaining == Integer.MAX_VALUE ? -1 : turnsRemaining;
            return new CurrentTreatyRow(fromAllianceId, toAllianceId, treatyType, normalizedTurnsRemaining);
        }
    }

    record TreatyCheckpointComparison(
            long currentTimeMs,
            int currentDay,
            int replayedActiveCount,
            int currentActiveCount,
            boolean matchesCurrentTruth,
            List<CurrentTreatyRow> currentTreaties,
            Map<TreatyPairKey, ActiveTreatyState> replayedStateByPair,
            List<RepairChange> expiredRepairChanges
    ) {
    }

        record ReplayResult(
            Map<TreatyPairKey, ActiveTreatyState> activeTreatiesByPair,
            List<RepairChange> expiredRepairChanges
        ) {
        }

        record RepairChange(
            long timestamp,
            TreatyChangeAction action,
            TreatyType treatyType,
            int fromAllianceId,
            int toAllianceId,
            int turnsRemaining
        ) {
        }

        @FunctionalInterface
        interface RepairWriter {
        void write(long timestamp, TreatyChangeAction action, TreatyType treatyType, int fromAllianceId, int toAllianceId, int turnsRemaining) throws IOException;
        }

    private record TreatyPairKey(int leftAllianceId, int rightAllianceId) {
        static TreatyPairKey fromIds(int firstAllianceId, int secondAllianceId) {
            return firstAllianceId <= secondAllianceId
                    ? new TreatyPairKey(firstAllianceId, secondAllianceId)
                    : new TreatyPairKey(secondAllianceId, firstAllianceId);
        }
    }

    private record ActiveTreatyState(TreatyType treatyType, long turnEnds) {
        boolean isPermanent() {
            return turnEnds == Long.MAX_VALUE;
        }
    }
}

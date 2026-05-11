package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

public final class PlannerReplayProjector {
    static final int NATION_MASK_AVG_INFRA_CENTS = 0x1;
    static final int NATION_MASK_UNIT_COUNTS = 0x2;
    static final int WAR_MASK_COMBAT_STATE = 0x1;
    static final int WAR_MASK_FLAGS = 0x2;
    static final int TURN_META_BLOCK_SIZE = 12;
    private static final Comparator<DBNationSnapshot> NATION_ID_ORDER = Comparator.comparingInt(DBNationSnapshot::nationId);

    private PlannerReplayProjector() {
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                Map.of(),
                List.of(),
                List.of(),
                new DamageObjective(),
                participantIdsAscending(nations),
                new int[0],
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        StrategicObjective counterObjective,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                redeclareDeclarers,
                redeclareTargets,
                secondaryRedeclareDeclarers,
                secondaryRedeclareTargets,
                counterObjective,
                participantIdsAscending(nations),
                new int[0],
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                Map.of(),
                counterDeclarers,
                counterTargets,
                new DamageObjective(),
                participantIdsAscending(nations),
                new int[0],
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                Map.of(),
                counterDeclarers,
                counterTargets,
                counterObjective,
                participantIdsAscending(nations),
                new int[0],
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                counterObjective,
                participantIdsAscending(nations),
                new int[0],
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int[] participantIds,
        int[] existingWarPairs,
        int currentTurn,
        int horizonTurns
    ) {
    return capture(
        tuning,
        overrides,
        nations,
        attackerNationIds,
        defenderNationIds,
        assignment,
        warTypeOrdinalsByPair,
        counterDeclarers,
        counterTargets,
        snapshotsForNationIds(nations, attackerNationIds),
        snapshotsForNationIds(nations, defenderNationIds),
        List.of(),
        List.of(),
        counterObjective,
        participantIds,
        existingWarPairs,
        currentTurn,
        horizonTurns
    );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        StrategicObjective counterObjective,
        int[] participantIds,
        int[] existingWarPairs,
        int currentTurn,
        int horizonTurns
    ) {
        return capture(
                tuning,
                overrides,
                nations,
                attackerNationIds,
                defenderNationIds,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                redeclareDeclarers,
                redeclareTargets,
                secondaryRedeclareDeclarers,
                secondaryRedeclareTargets,
                legacyAutonomousPolicy("counterDeclarer", counterObjective, true),
                legacyAutonomousPolicy("counterTarget", counterObjective, false),
                legacyAutonomousPolicy("redeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("redeclareTarget", counterObjective, false),
                legacyAutonomousPolicy("secondaryRedeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("secondaryRedeclareTarget", counterObjective, false),
                participantIds,
                existingWarPairs,
                currentTurn,
                horizonTurns
        );
    }

    public static BlitzReplayTrace capture(
        SimTuning tuning,
        OverrideSet overrides,
        Collection<DBNationSnapshot> nations,
        int[] attackerNationIds,
        int[] defenderNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        SidePolicy counterDeclarerPolicy,
        SidePolicy counterTargetPolicy,
        SidePolicy redeclareDeclarerPolicy,
        SidePolicy redeclareTargetPolicy,
        SidePolicy secondaryRedeclareDeclarerPolicy,
        SidePolicy secondaryRedeclareTargetPolicy,
        int[] participantIds,
        int[] existingWarPairs,
        int currentTurn,
        int horizonTurns
    ) {
        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                overrides,
                nations,
                List.of(),
                currentTurn,
                tuning,
                PlannerTransitionSemantics.REPLAY
        );
        return capture(
                conflict,
                attackerNationIds,
                participantIds,
                existingWarPairs,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                redeclareDeclarers,
                redeclareTargets,
                secondaryRedeclareDeclarers,
                secondaryRedeclareTargets,
                counterDeclarerPolicy,
                counterTargetPolicy,
                redeclareDeclarerPolicy,
                redeclareTargetPolicy,
                secondaryRedeclareDeclarerPolicy,
                secondaryRedeclareTargetPolicy,
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        Map<Integer, List<Integer>> assignment,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                conflict.replayNationIdsAscending(),
                new int[0],
                assignment,
                Map.of(),
                counterDeclarers,
                counterTargets,
                counterObjective,
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                conflict.replayNationIdsAscending(),
                new int[0],
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                counterObjective,
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        StrategicObjective counterObjective,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                conflict.replayNationIdsAscending(),
                new int[0],
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                redeclareDeclarers,
                redeclareTargets,
                secondaryRedeclareDeclarers,
                secondaryRedeclareTargets,
                counterObjective,
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        int[] participantIds,
        int[] existingWarPairs,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        StrategicObjective counterObjective,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                participantIds,
                existingWarPairs,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                nationIds(attackerNationIds),
                nationIds(defenderNationIds(participantIds, attackerNationIds)),
                List.of(),
                List.of(),
                legacyAutonomousPolicy("counterDeclarer", counterObjective, true),
                legacyAutonomousPolicy("counterTarget", counterObjective, false),
                legacyAutonomousPolicy("redeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("redeclareTarget", counterObjective, false),
                legacyAutonomousPolicy("secondaryRedeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("secondaryRedeclareTarget", counterObjective, false),
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        int[] participantIds,
        int[] existingWarPairs,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        SidePolicy counterDeclarerPolicy,
        SidePolicy counterTargetPolicy,
        SidePolicy redeclareDeclarerPolicy,
        SidePolicy redeclareTargetPolicy,
        SidePolicy secondaryRedeclareDeclarerPolicy,
        SidePolicy secondaryRedeclareTargetPolicy,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                participantIds,
                existingWarPairs,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                nationIds(redeclareDeclarers),
                nationIds(redeclareTargets),
                nationIds(secondaryRedeclareDeclarers),
                nationIds(secondaryRedeclareTargets),
                counterDeclarerPolicy,
                counterTargetPolicy,
                redeclareDeclarerPolicy,
                redeclareTargetPolicy,
                secondaryRedeclareDeclarerPolicy,
                secondaryRedeclareTargetPolicy,
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        int[] participantIds,
        int[] existingWarPairs,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        Collection<DBNationSnapshot> redeclareDeclarers,
        Collection<DBNationSnapshot> redeclareTargets,
        Collection<DBNationSnapshot> secondaryRedeclareDeclarers,
        Collection<DBNationSnapshot> secondaryRedeclareTargets,
        StrategicObjective counterObjective,
        int horizonTurns
    ) {
        return capture(
                conflict,
                attackerNationIds,
                participantIds,
                existingWarPairs,
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                nationIds(redeclareDeclarers),
                nationIds(redeclareTargets),
                nationIds(secondaryRedeclareDeclarers),
                nationIds(secondaryRedeclareTargets),
                legacyAutonomousPolicy("counterDeclarer", counterObjective, true),
                legacyAutonomousPolicy("counterTarget", counterObjective, false),
                legacyAutonomousPolicy("redeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("redeclareTarget", counterObjective, false),
                legacyAutonomousPolicy("secondaryRedeclareDeclarer", counterObjective, true),
                legacyAutonomousPolicy("secondaryRedeclareTarget", counterObjective, false),
                horizonTurns
        );
    }

    static BlitzReplayTrace capture(
        PlannerLocalConflict conflict,
        int[] attackerNationIds,
        int[] participantIds,
        int[] existingWarPairs,
        Map<Integer, List<Integer>> assignment,
        Map<Long, Integer> warTypeOrdinalsByPair,
        Collection<DBNationSnapshot> counterDeclarers,
        Collection<DBNationSnapshot> counterTargets,
        List<Integer> redeclareDeclarerIds,
        List<Integer> redeclareTargetIds,
        List<Integer> secondaryRedeclareDeclarerIds,
        List<Integer> secondaryRedeclareTargetIds,
        SidePolicy counterDeclarerPolicy,
        SidePolicy counterTargetPolicy,
        SidePolicy redeclareDeclarerPolicy,
        SidePolicy redeclareTargetPolicy,
        SidePolicy secondaryRedeclareDeclarerPolicy,
        SidePolicy secondaryRedeclareTargetPolicy,
        int horizonTurns
    ) {
    try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.REPLAY_CAPTURE)) {
        int turns = Math.max(1, horizonTurns);
        int startTurn = conflict.currentTurn();
        IntPredicate isAttackerNationId = attackerNationIdLookup(attackerNationIds);
        NationDeltaTracker nationTracker = new NationDeltaTracker(conflict, conflict.replayNationIdsAscending());
        WarTableTracker warTracker = WarTableTracker.seededFromBaseline(conflict, participantIds, existingWarPairs);
        List<Integer> counterDeclarerIds = nationIds(counterDeclarers);
        List<Integer> counterTargetIds = nationIds(counterTargets);

        IntArrayBuilder turnMetaLanes = new IntArrayBuilder(turns * TURN_META_BLOCK_SIZE);
        IntArrayBuilder changedNationIndexes = new IntArrayBuilder();
        IntArrayBuilder changedNationMasks = new IntArrayBuilder();
        IntArrayBuilder changedNationLanes = new IntArrayBuilder();
        IntArrayBuilder changedWarIndexes = new IntArrayBuilder();
        IntArrayBuilder changedWarMasks = new IntArrayBuilder();
        IntArrayBuilder changedWarLanes = new IntArrayBuilder();
        IntArrayBuilder declaredWarPairs = new IntArrayBuilder();
        IntArrayBuilder declaredWarLanes = new IntArrayBuilder();
        IntArrayBuilder concludedWarLanes = new IntArrayBuilder();
        IntArrayBuilder summaryScalarLanes = new IntArrayBuilder();
        IntArrayBuilder summaryWarTypeCounts = new IntArrayBuilder();
        IntArrayBuilder summaryAttackOutcomeCounts = new IntArrayBuilder();
        IntArrayBuilder summaryUnitLossCounts = new IntArrayBuilder();
        IntArrayBuilder summaryInfraLossCents = new IntArrayBuilder();

        PlannerProfiler.addCounter(PlannerProfiler.Scope.REPLAY_CAPTURE, "horizonTurns", turns);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.REPLAY_CAPTURE, "nationBaseline", nationTracker.nationCount());
        PlannerProfiler.addCounter(PlannerProfiler.Scope.REPLAY_CAPTURE, "initialWars", warTracker.initialWarCount());

        for (int turnIndex = 0; turnIndex < turns; turnIndex++) {
        turnMetaLanes.add(changedNationIndexes.size());
        turnMetaLanes.add(changedNationLanes.size());
        turnMetaLanes.add(changedWarIndexes.size());
        turnMetaLanes.add(changedWarLanes.size());
        turnMetaLanes.add(declaredWarPairs.size());
        turnMetaLanes.add(declaredWarLanes.size());
        turnMetaLanes.add(concludedWarLanes.size());
        turnMetaLanes.add(summaryScalarLanes.size());
        turnMetaLanes.add(summaryWarTypeCounts.size());
        turnMetaLanes.add(summaryAttackOutcomeCounts.size());
        turnMetaLanes.add(summaryUnitLossCounts.size());
        turnMetaLanes.add(summaryInfraLossCents.size());

        conflict.beginReplayTurnMetrics(isAttackerNationId);
        conflict.applyReplayTurn(
            assignment,
            warTypeOrdinalsByPair,
            turnIndex == 0,
            counterDeclarerIds,
            counterTargetIds,
            redeclareDeclarerIds,
            redeclareTargetIds,
            secondaryRedeclareDeclarerIds,
            secondaryRedeclareTargetIds,
            counterDeclarerPolicy,
            counterTargetPolicy,
            redeclareDeclarerPolicy,
            redeclareTargetPolicy,
            secondaryRedeclareDeclarerPolicy,
            secondaryRedeclareTargetPolicy,
            turns - turnIndex
        );

        PlannerReplayTurnMetrics metrics = conflict.drainReplayTurnMetrics();
        if (metrics == null) {
            metrics = new PlannerReplayTurnMetrics(isAttackerNationId);
        }

        nationTracker.captureTurn(conflict, changedNationIndexes, changedNationMasks, changedNationLanes);
        warTracker.captureTurn(
            conflict,
            metrics,
            changedWarIndexes,
            changedWarMasks,
            changedWarLanes,
            declaredWarPairs,
            declaredWarLanes,
            concludedWarLanes
        );
        metrics.appendSummaryScalarLanes(summaryScalarLanes);
        metrics.appendSummaryWarTypeCounts(summaryWarTypeCounts);
        metrics.appendSummaryAttackOutcomeCounts(summaryAttackOutcomeCounts);
        metrics.appendSummaryUnitLossCounts(summaryUnitLossCounts);
        metrics.appendSummaryInfraLossCents(summaryInfraLossCents);
        }

        return new BlitzReplayTrace(
            startTurn,
            turnMetaLanes.toArray(),
            changedNationIndexes.toArray(),
            changedNationMasks.toArray(),
            changedNationLanes.toArray(),
            changedWarIndexes.toArray(),
            changedWarMasks.toArray(),
            changedWarLanes.toArray(),
            declaredWarPairs.toArray(),
            declaredWarLanes.toArray(),
            concludedWarLanes.toArray(),
            summaryScalarLanes.toArray(),
            summaryWarTypeCounts.toArray(),
            summaryAttackOutcomeCounts.toArray(),
            summaryUnitLossCounts.toArray(),
            summaryInfraLossCents.toArray()
        );
    }

    }

    private static SidePolicy legacyAutonomousPolicy(String name, StrategicObjective objective, boolean declarerSide) {
        StrategicObjective effectiveObjective = objective == null ? new DamageObjective() : objective;
        return declarerSide
                ? SidePolicy.legacy(name, effectiveObjective)
                : SidePolicy.legacyPassive(name, effectiveObjective);
    }

    private static IntPredicate attackerNationIdLookup(int[] attackerNationIds) {
        IntOpenHashSet ids = new IntOpenHashSet(Math.max(16, attackerNationIds.length * 2));
        for (int attackerNationId : attackerNationIds) {
            ids.add(attackerNationId);
        }
        return ids::contains;
    }

    private static List<Integer> nationIds(Collection<DBNationSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        int[] nationIds = participantIdsAscending(snapshots);
        return nationIds(nationIds);
    }

    private static List<Integer> nationIds(int[] nationIds) {
        if (nationIds.length == 0) {
            return List.of();
        }
        int[] sortedNationIds = Arrays.copyOf(nationIds, nationIds.length);
        Arrays.sort(sortedNationIds);
        ArrayList<Integer> ids = new ArrayList<>(sortedNationIds.length);
        for (int nationId : sortedNationIds) {
            ids.add(nationId);
        }
        return Collections.unmodifiableList(ids);
    }

    private static List<DBNationSnapshot> snapshotsForNationIds(Collection<DBNationSnapshot> snapshots, int[] nationIds) {
        if (snapshots.isEmpty() || nationIds.length == 0) {
            return List.of();
        }
        IntOpenHashSet nationIdSet = new IntOpenHashSet(Math.max(16, nationIds.length * 2));
        for (int nationId : nationIds) {
            nationIdSet.add(nationId);
        }
        ArrayList<DBNationSnapshot> filtered = new ArrayList<>(Math.min(nationIds.length, snapshots.size()));
        for (DBNationSnapshot snapshot : snapshots) {
            if (nationIdSet.contains(snapshot.nationId())) {
                filtered.add(snapshot);
            }
        }
        filtered.sort(NATION_ID_ORDER);
        return Collections.unmodifiableList(filtered);
    }

    private static int[] defenderNationIds(int[] participantIds, int[] attackerNationIds) {
        IntOpenHashSet attackerIdSet = new IntOpenHashSet(Math.max(16, attackerNationIds.length * 2));
        for (int attackerNationId : attackerNationIds) {
            attackerIdSet.add(attackerNationId);
        }
        int[] defenderNationIds = new int[participantIds.length];
        int count = 0;
        for (int participantId : participantIds) {
            if (!attackerIdSet.contains(participantId)) {
                defenderNationIds[count++] = participantId;
            }
        }
        int[] trimmed = Arrays.copyOf(defenderNationIds, count);
        Arrays.sort(trimmed);
        return trimmed;
    }

    private static int[] participantIdsAscending(Collection<DBNationSnapshot> snapshots) {
        int[] participantIds = new int[snapshots.size()];
        int index = 0;
        for (DBNationSnapshot snapshot : snapshots) {
            participantIds[index++] = snapshot.nationId();
        }
        Arrays.sort(participantIds);
        return participantIds;
    }

    private static boolean isActive(int statusOrdinal) {
        return WarStatus.values[statusOrdinal].isActive();
    }

    private static final class NationDeltaTracker {
        private final int[] nationIdsAscending;
        private final int[] previousAvgInfraCents;
        private final int[][] previousUnitsByNationIndex;

        private NationDeltaTracker(PlannerLocalConflict conflict, int[] nationIdsAscending) {
            this.nationIdsAscending = nationIdsAscending;
            this.previousAvgInfraCents = new int[nationIdsAscending.length];
            this.previousUnitsByNationIndex = new int[nationIdsAscending.length][MilitaryUnit.values.length];
            for (int nationIndex = 0; nationIndex < nationIdsAscending.length; nationIndex++) {
                int nationId = nationIdsAscending[nationIndex];
                previousAvgInfraCents[nationIndex] = conflict.replayNationAvgInfraCents(nationId);
                conflict.copyReplayNationUnitCounts(nationId, previousUnitsByNationIndex[nationIndex]);
            }
        }

        private int nationCount() {
            return nationIdsAscending.length;
        }

        private void captureTurn(
            PlannerLocalConflict conflict,
            IntArrayBuilder changedNationIndexes,
            IntArrayBuilder changedNationMasks,
            IntArrayBuilder changedNationLanes
        ) {
            for (int nationIndex = 0; nationIndex < nationIdsAscending.length; nationIndex++) {
                int nationId = nationIdsAscending[nationIndex];
                int currentAvgInfraCents = conflict.replayNationAvgInfraCents(nationId);
                boolean unitCountsChanged = !conflict.replayNationUnitCountsMatch(
                        nationId,
                        previousUnitsByNationIndex[nationIndex]
                );

                int mask = 0;
                if (currentAvgInfraCents != previousAvgInfraCents[nationIndex]) {
                    mask |= NATION_MASK_AVG_INFRA_CENTS;
                }
                if (unitCountsChanged) {
                    mask |= NATION_MASK_UNIT_COUNTS;
                }
                if (mask == 0) {
                    continue;
                }

                changedNationIndexes.add(nationIndex);
                changedNationMasks.add(mask);
                if ((mask & NATION_MASK_AVG_INFRA_CENTS) != 0) {
                    changedNationLanes.add(currentAvgInfraCents);
                    previousAvgInfraCents[nationIndex] = currentAvgInfraCents;
                }
                if (unitCountsChanged) {
                    conflict.appendReplayNationUnitCounts(
                            nationId,
                            previousUnitsByNationIndex[nationIndex],
                            changedNationLanes
                    );
                }
            }
        }
    }

    private static final class WarTableTracker {
        private static final Comparator<WarSnapshot> PAIR_ORDER = Comparator
                .comparingInt(WarSnapshot::declarerNationId)
                .thenComparingInt(WarSnapshot::targetNationId);
        private static final Comparator<ChangedWarLane> CHANGED_WAR_ORDER = Comparator.comparingInt(ChangedWarLane::warIndex);
        private static final Comparator<DeclaredWarLane> DECLARED_WAR_ORDER = Comparator
            .comparingInt(DeclaredWarLane::declarerNationId)
            .thenComparingInt(DeclaredWarLane::targetNationId);
        private static final Comparator<ConcludedWarLane> CONCLUDED_WAR_ORDER = Comparator.comparingInt(ConcludedWarLane::warIndex);
        private static final int MISSING_WAR_STATE = Integer.MIN_VALUE;

        private final Long2IntOpenHashMap previousCombatStateByPair;
        private final Long2IntOpenHashMap previousFlagsByPair;
        private final Long2IntOpenHashMap activeWarIndexByPair;
        private final Int2IntOpenHashMap participantIndexByNationId;
        private final ArrayList<ChangedWarLane> changedScratch = new ArrayList<>();
        private final ArrayList<DeclaredWarLane> declaredScratch = new ArrayList<>();
        private final ArrayList<ConcludedWarLane> concludedScratch = new ArrayList<>();
        private final int initialWarCount;
        private int nextWarIndex;

        private WarTableTracker(
            Long2IntOpenHashMap previousCombatStateByPair,
            Long2IntOpenHashMap previousFlagsByPair,
            Long2IntOpenHashMap activeWarIndexByPair,
                Int2IntOpenHashMap participantIndexByNationId,
                int initialWarCount,
                int nextWarIndex
        ) {
            this.previousCombatStateByPair = previousCombatStateByPair;
            this.previousFlagsByPair = previousFlagsByPair;
            this.activeWarIndexByPair = activeWarIndexByPair;
            this.participantIndexByNationId = participantIndexByNationId;
            this.initialWarCount = initialWarCount;
            this.nextWarIndex = nextWarIndex;
        }

        private static WarTableTracker seededFromBaseline(
                PlannerLocalConflict conflict,
                int[] participantIds,
                int[] existingWarPairs
        ) {
            Long2IntOpenHashMap previousCombatStateByPair = new Long2IntOpenHashMap();
            previousCombatStateByPair.defaultReturnValue(MISSING_WAR_STATE);
            Long2IntOpenHashMap previousFlagsByPair = new Long2IntOpenHashMap();
            previousFlagsByPair.defaultReturnValue(MISSING_WAR_STATE);
            Long2IntOpenHashMap activeWarIndexByPair = new Long2IntOpenHashMap();
            activeWarIndexByPair.defaultReturnValue(-1);
            Int2IntOpenHashMap participantIndexByNationId = new Int2IntOpenHashMap(Math.max(16, participantIds.length * 2));
            participantIndexByNationId.defaultReturnValue(-1);
            for (int index = 0; index < participantIds.length; index++) {
                participantIndexByNationId.put(participantIds[index], index);
            }

            List<WarSnapshot> activeWars = new ArrayList<>();
            conflict.forEachReplayWar((pairKey, declarerNationId, targetNationId, warTypeOrdinal, startTurn,
                                      statusOrdinal, attackerMaps, defenderMaps, attackerResistance,
                                      defenderResistance, groundSuperiorityOwnerOrdinal,
                                      airSuperiorityOwnerOrdinal, blockadeOwnerOrdinal,
                                      attackerFortified, defenderFortified) -> {
                WarSnapshot snapshot = new WarSnapshot(
                        pairKey,
                        declarerNationId,
                        targetNationId,
                        warTypeOrdinal,
                        startTurn,
                        statusOrdinal,
                        attackerMaps,
                        defenderMaps,
                        attackerResistance,
                        defenderResistance,
                        groundSuperiorityOwnerOrdinal,
                        airSuperiorityOwnerOrdinal,
                        blockadeOwnerOrdinal,
                        attackerFortified,
                        defenderFortified
                );
                previousCombatStateByPair.put(pairKey, snapshot.packedCombatState());
                previousFlagsByPair.put(pairKey, snapshot.packedFlags());
                if (snapshot.isActive()) {
                    activeWars.add(snapshot);
                }
            });

            int nextWarIndex = 0;
            if (existingWarPairs.length > 0) {
                for (int offset = 0; offset + 1 < existingWarPairs.length; offset += 2) {
                    int declarerIndex = existingWarPairs[offset];
                    int targetIndex = existingWarPairs[offset + 1];
                    if (declarerIndex < 0 || declarerIndex >= participantIds.length || targetIndex < 0 || targetIndex >= participantIds.length) {
                        continue;
                    }
                    activeWarIndexByPair.put(packPair(participantIds[declarerIndex], participantIds[targetIndex]), nextWarIndex++);
                }
            } else {
                activeWars.sort(PAIR_ORDER);
                for (WarSnapshot snapshot : activeWars) {
                    activeWarIndexByPair.put(snapshot.pairKey(), nextWarIndex++);
                }
            }

            return new WarTableTracker(
                    previousCombatStateByPair,
                    previousFlagsByPair,
                    activeWarIndexByPair,
                    participantIndexByNationId,
                    nextWarIndex,
                    nextWarIndex
            );
        }

        private int initialWarCount() {
            return initialWarCount;
        }

        private void captureTurn(
            PlannerLocalConflict conflict,
            PlannerReplayTurnMetrics metrics,
            IntArrayBuilder changedWarIndexes,
            IntArrayBuilder changedWarMasks,
            IntArrayBuilder changedWarLanes,
            IntArrayBuilder declaredWarPairs,
            IntArrayBuilder declaredWarLanes,
            IntArrayBuilder concludedWarLanes
        ) {
            changedScratch.clear();
            declaredScratch.clear();
            concludedScratch.clear();

            conflict.forEachReplayWar((pairKey, declarerNationId, targetNationId, warTypeOrdinal, startTurn,
                                      statusOrdinal, attackerMaps, defenderMaps, attackerResistance,
                                      defenderResistance, groundSuperiorityOwnerOrdinal,
                                      airSuperiorityOwnerOrdinal, blockadeOwnerOrdinal,
                                      attackerFortified, defenderFortified) -> {
                int packedCombatState = packCombatState(attackerMaps, defenderMaps, attackerResistance, defenderResistance);
                int packedFlags = packFlags(
                        warTypeOrdinal,
                        statusOrdinal,
                        groundSuperiorityOwnerOrdinal,
                        airSuperiorityOwnerOrdinal,
                        blockadeOwnerOrdinal,
                        attackerFortified,
                        defenderFortified
                );
                int previousFlags = previousFlagsByPair.put(pairKey, packedFlags);
                int previousCombatState = previousCombatStateByPair.put(pairKey, packedCombatState);
                boolean previousActive = previousFlags != MISSING_WAR_STATE && PlannerReplayProjector.isActive(unpackStatusOrdinal(previousFlags));
                boolean currentActive = PlannerReplayProjector.isActive(statusOrdinal);
                if (currentActive && !previousActive) {
                    declaredScratch.add(new DeclaredWarLane(
                            pairKey,
                            declarerNationId,
                            targetNationId,
                            warTypeOrdinal,
                            startTurn,
                            packedCombatState,
                            packedFlags
                    ));
                    metrics.recordDeclaredWar(declarerNationId, warTypeOrdinal);
                    return;
                }
                if (!currentActive && previousActive) {
                    int warIndex = activeWarIndexByPair.remove(pairKey);
                    if (warIndex >= 0) {
                        concludedScratch.add(new ConcludedWarLane(warIndex, statusOrdinal));
                        metrics.recordConcludedWar(declarerNationId, targetNationId, statusOrdinal);
                    }
                    return;
                }
                if (!currentActive) {
                    return;
                }
                int warIndex = activeWarIndexByPair.get(pairKey);
                int mask = diffMask(previousCombatState, previousFlags, packedCombatState, packedFlags);
                if (warIndex >= 0 && mask != 0) {
                    changedScratch.add(new ChangedWarLane(warIndex, mask, packedCombatState, packedFlags));
                }
            });

            if (changedScratch.isEmpty() && declaredScratch.isEmpty() && concludedScratch.isEmpty()) {
                return;
            }

            changedScratch.sort(CHANGED_WAR_ORDER);
            declaredScratch.sort(DECLARED_WAR_ORDER);
            concludedScratch.sort(CONCLUDED_WAR_ORDER);

            for (ChangedWarLane lane : changedScratch) {
                changedWarIndexes.add(lane.warIndex());
                changedWarMasks.add(lane.mask());
                if ((lane.mask() & WAR_MASK_COMBAT_STATE) != 0) {
                    changedWarLanes.add(lane.packedCombatState());
                }
                if ((lane.mask() & WAR_MASK_FLAGS) != 0) {
                    changedWarLanes.add(lane.packedFlags());
                }
            }

            for (DeclaredWarLane lane : declaredScratch) {
                activeWarIndexByPair.put(lane.pairKey(), nextWarIndex++);
                int declarerIndex = participantIndexByNationId.get(lane.declarerNationId());
                int targetIndex = participantIndexByNationId.get(lane.targetNationId());
                if (declarerIndex < 0 || targetIndex < 0) {
                    continue;
                }
                declaredWarPairs.add(declarerIndex);
                declaredWarPairs.add(targetIndex);
                declaredWarLanes.add(lane.startTurn());
                declaredWarLanes.add(lane.packedCombatState());
                declaredWarLanes.add(lane.packedFlags());
            }

            for (ConcludedWarLane lane : concludedScratch) {
                concludedWarLanes.add(lane.warIndex());
                concludedWarLanes.add(lane.endStatusOrdinal());
            }
        }

        private static int diffMask(int previousCombatState, int previousFlags, int currentCombatState, int currentFlags) {
            if (previousFlags == MISSING_WAR_STATE) {
                return WAR_MASK_COMBAT_STATE | WAR_MASK_FLAGS;
            }
            int mask = 0;
            if (previousCombatState != currentCombatState) {
                mask |= WAR_MASK_COMBAT_STATE;
            }
            if (previousFlags != currentFlags) {
                mask |= WAR_MASK_FLAGS;
            }
            return mask;
        }

        private static int unpackStatusOrdinal(int packedFlags) {
            return (packedFlags >>> 6) & 0x1F;
        }

        private static int packCombatState(
                int attackerMaps,
                int defenderMaps,
                int attackerResistance,
                int defenderResistance
        ) {
            return (attackerMaps & 0xF)
                    | ((defenderMaps & 0xF) << 4)
                    | ((attackerResistance & 0x7F) << 8)
                    | ((defenderResistance & 0x7F) << 15);
        }

        private static int packFlags(
                int warTypeOrdinal,
                int statusOrdinal,
                int groundSuperiorityOwnerOrdinal,
                int airSuperiorityOwnerOrdinal,
                int blockadeOwnerOrdinal,
                boolean attackerFortified,
                boolean defenderFortified
        ) {
            int flags = (warTypeOrdinal & 0x3F)
                    | ((statusOrdinal & 0x1F) << 6)
                    | ((groundSuperiorityOwnerOrdinal & 0x3) << 11)
                    | ((airSuperiorityOwnerOrdinal & 0x3) << 13)
                    | ((blockadeOwnerOrdinal & 0x3) << 15);
            if (attackerFortified) {
                flags |= (1 << 17);
            }
            if (defenderFortified) {
                flags |= (1 << 18);
            }
            return flags;
        }
    }

    private static long packPair(int left, int right) {
        return ((long) left << 32) ^ (right & 0xffffffffL);
    }

    private record WarSnapshot(
            long pairKey,
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int startTurn,
            int statusOrdinal,
            int attackerMaps,
            int defenderMaps,
            int attackerResistance,
            int defenderResistance,
            int groundSuperiorityOwnerOrdinal,
            int airSuperiorityOwnerOrdinal,
            int blockadeOwnerOrdinal,
            boolean attackerFortified,
            boolean defenderFortified
    ) {
        private boolean isActive() {
            return PlannerReplayProjector.isActive(statusOrdinal);
        }

        private int diffMask(WarSnapshot previous) {
            if (previous == null) {
                return WAR_MASK_COMBAT_STATE | WAR_MASK_FLAGS;
            }
            int mask = 0;
            if (packedCombatState() != previous.packedCombatState()) {
                mask |= WAR_MASK_COMBAT_STATE;
            }
            if (packedFlags() != previous.packedFlags()) {
                mask |= WAR_MASK_FLAGS;
            }
            return mask;
        }

        private int packedCombatState() {
            return (attackerMaps & 0xF)
                    | ((defenderMaps & 0xF) << 4)
                    | ((attackerResistance & 0x7F) << 8)
                    | ((defenderResistance & 0x7F) << 15);
        }

        private int packedFlags() {
            int flags = (warTypeOrdinal & 0x3F)
                    | ((statusOrdinal & 0x1F) << 6)
                    | ((groundSuperiorityOwnerOrdinal & 0x3) << 11)
                    | ((airSuperiorityOwnerOrdinal & 0x3) << 13)
                    | ((blockadeOwnerOrdinal & 0x3) << 15);
            if (attackerFortified) {
                flags |= (1 << 17);
            }
            if (defenderFortified) {
                flags |= (1 << 18);
            }
            return flags;
        }
    }

        private record ChangedWarLane(int warIndex, int mask, int packedCombatState, int packedFlags) {
        }

        private record DeclaredWarLane(
            long pairKey,
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int startTurn,
            int packedCombatState,
            int packedFlags
        ) {
    }

    private record ConcludedWarLane(int warIndex, int endStatusOrdinal) {
    }

    static final class IntArrayBuilder {
        private int[] values;
        private int size;

        private IntArrayBuilder() {
            this(16);
        }

        private IntArrayBuilder(int initialCapacity) {
            values = new int[Math.max(1, initialCapacity)];
        }

        private void add(int value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        private void addAll(int[] source) {
            addAll(source, source.length);
        }

        void addAll(int[] source, int length) {
            addAll(source, 0, length);
        }

        void addAll(int[] source, int offset, int length) {
            if (length <= 0) {
                return;
            }
            ensureCapacity(size + length);
            System.arraycopy(source, offset, values, size, length);
            size += length;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            return Arrays.copyOf(values, size);
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= values.length) {
                return;
            }
            int next = values.length;
            while (next < capacity) {
                next = next < 64 ? next * 2 : next + (next >> 1);
            }
            values = Arrays.copyOf(values, next);
        }
    }
}

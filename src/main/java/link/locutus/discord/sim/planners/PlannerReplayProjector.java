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

        NationDelta nationDelta = nationTracker.captureTurn(conflict);
        WarDelta warDelta = warTracker.captureTurn(conflict, metrics);

        changedNationIndexes.addAll(nationDelta.changedNationIndexes());
        changedNationMasks.addAll(nationDelta.changedNationMasks());
        changedNationLanes.addAll(nationDelta.changedNationLanes());
        changedWarIndexes.addAll(warDelta.changedWarIndexes());
        changedWarMasks.addAll(warDelta.changedWarMasks());
        changedWarLanes.addAll(warDelta.changedWarLanes());
        declaredWarPairs.addAll(warDelta.declaredWarPairs());
        declaredWarLanes.addAll(warDelta.declaredWarLanes());
        concludedWarLanes.addAll(warDelta.concludedWarLanes());
        summaryScalarLanes.addAll(metrics.summaryScalarLanes());
        summaryWarTypeCounts.addAll(metrics.summaryWarTypeCounts());
        summaryAttackOutcomeCounts.addAll(metrics.summaryAttackOutcomeCounts());
        summaryUnitLossCounts.addAll(metrics.summaryUnitLossCounts());
        summaryInfraLossCents.addAll(metrics.summaryInfraLossCents());
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
        return snapshots.stream()
                .map(DBNationSnapshot::nationId)
                .sorted()
                .toList();
    }

    private static List<Integer> nationIds(int[] nationIds) {
        if (nationIds.length == 0) {
            return List.of();
        }
        return Arrays.stream(nationIds)
                .sorted()
                .boxed()
                .toList();
    }

    private static List<DBNationSnapshot> snapshotsForNationIds(Collection<DBNationSnapshot> snapshots, int[] nationIds) {
        if (snapshots.isEmpty() || nationIds.length == 0) {
            return List.of();
        }
        IntOpenHashSet nationIdSet = new IntOpenHashSet(Math.max(16, nationIds.length * 2));
        for (int nationId : nationIds) {
            nationIdSet.add(nationId);
        }
        return snapshots.stream()
                .filter(snapshot -> nationIdSet.contains(snapshot.nationId()))
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static int[] defenderNationIds(int[] participantIds, int[] attackerNationIds) {
        IntOpenHashSet attackerIdSet = new IntOpenHashSet(Math.max(16, attackerNationIds.length * 2));
        for (int attackerNationId : attackerNationIds) {
            attackerIdSet.add(attackerNationId);
        }
        return Arrays.stream(participantIds)
                .filter(participantId -> !attackerIdSet.contains(participantId))
                .sorted()
                .toArray();
    }

    private static int[] participantIdsAscending(Collection<DBNationSnapshot> snapshots) {
        return snapshots.stream().mapToInt(DBNationSnapshot::nationId).sorted().toArray();
    }

    private static boolean isActive(int statusOrdinal) {
        return WarStatus.values[statusOrdinal].isActive();
    }

    private record NationDelta(
            int[] changedNationIndexes,
            int[] changedNationMasks,
            int[] changedNationLanes
    ) {
        private static final NationDelta EMPTY = new NationDelta(new int[0], new int[0], new int[0]);

        private boolean isEmpty() {
            return changedNationIndexes.length == 0;
        }
    }

    private record WarDelta(
            int[] changedWarIndexes,
            int[] changedWarMasks,
            int[] changedWarLanes,
            int[] declaredWarPairs,
            int[] declaredWarLanes,
            int[] concludedWarLanes
    ) {
        private static final WarDelta EMPTY = new WarDelta(new int[0], new int[0], new int[0], new int[0], new int[0], new int[0]);

        private boolean isEmpty() {
            return changedWarIndexes.length == 0
                    && declaredWarPairs.length == 0
                    && declaredWarLanes.length == 0
                    && concludedWarLanes.length == 0;
        }
    }

    private static final class NationDeltaTracker {
        private final int[] nationIdsAscending;
        private final int[] previousAvgInfraCents;
        private final int[][] previousUnitsByNationIndex;
        private final int[] unitScratch = new int[MilitaryUnit.values.length];

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

        private NationDelta captureTurn(PlannerLocalConflict conflict) {
            IntArrayBuilder indexes = new IntArrayBuilder();
            IntArrayBuilder masks = new IntArrayBuilder();
            IntArrayBuilder lanes = new IntArrayBuilder();
            for (int nationIndex = 0; nationIndex < nationIdsAscending.length; nationIndex++) {
                int nationId = nationIdsAscending[nationIndex];
                int currentAvgInfraCents = conflict.replayNationAvgInfraCents(nationId);
                conflict.copyReplayNationUnitCounts(nationId, unitScratch);

                int mask = 0;
                if (currentAvgInfraCents != previousAvgInfraCents[nationIndex]) {
                    mask |= NATION_MASK_AVG_INFRA_CENTS;
                }
                if (!Arrays.equals(previousUnitsByNationIndex[nationIndex], unitScratch)) {
                    mask |= NATION_MASK_UNIT_COUNTS;
                }
                if (mask == 0) {
                    continue;
                }

                indexes.add(nationIndex);
                masks.add(mask);
                if ((mask & NATION_MASK_AVG_INFRA_CENTS) != 0) {
                    lanes.add(currentAvgInfraCents);
                    previousAvgInfraCents[nationIndex] = currentAvgInfraCents;
                }
                if ((mask & NATION_MASK_UNIT_COUNTS) != 0) {
                    lanes.addAll(unitScratch);
                    System.arraycopy(unitScratch, 0, previousUnitsByNationIndex[nationIndex], 0, unitScratch.length);
                }
            }
            if (indexes.isEmpty()) {
                return NationDelta.EMPTY;
            }
            return new NationDelta(indexes.toArray(), masks.toArray(), lanes.toArray());
        }
    }

    private static final class WarTableTracker {
        private static final Comparator<WarSnapshot> PAIR_ORDER = Comparator
                .comparingInt(WarSnapshot::declarerNationId)
                .thenComparingInt(WarSnapshot::targetNationId);

        private final Map<Long, WarSnapshot> previousWarsByPair;
        private final Map<Long, Integer> activeWarIndexByPair;
        private final Int2IntOpenHashMap participantIndexByNationId;
        private final int initialWarCount;
        private int nextWarIndex;

        private WarTableTracker(
                Map<Long, WarSnapshot> previousWarsByPair,
                Map<Long, Integer> activeWarIndexByPair,
                Int2IntOpenHashMap participantIndexByNationId,
                int initialWarCount,
                int nextWarIndex
        ) {
            this.previousWarsByPair = previousWarsByPair;
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
            Map<Long, WarSnapshot> previousWarsByPair = new Long2ObjectOpenHashMap<>();
            Map<Long, Integer> activeWarIndexByPair = new Long2IntOpenHashMap();
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
                previousWarsByPair.put(pairKey, snapshot);
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
                    previousWarsByPair,
                    activeWarIndexByPair,
                    participantIndexByNationId,
                    nextWarIndex,
                    nextWarIndex
            );
        }

        private int initialWarCount() {
            return initialWarCount;
        }

        private WarDelta captureTurn(PlannerLocalConflict conflict, PlannerReplayTurnMetrics metrics) {
            List<ChangedWarLane> changed = new ArrayList<>();
            List<WarSnapshot> declared = new ArrayList<>();
            List<ConcludedWarLane> concluded = new ArrayList<>();

            conflict.forEachReplayWar((pairKey, declarerNationId, targetNationId, warTypeOrdinal, startTurn,
                                      statusOrdinal, attackerMaps, defenderMaps, attackerResistance,
                                      defenderResistance, groundSuperiorityOwnerOrdinal,
                                      airSuperiorityOwnerOrdinal, blockadeOwnerOrdinal,
                                      attackerFortified, defenderFortified) -> {
                WarSnapshot current = new WarSnapshot(
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
                WarSnapshot previous = previousWarsByPair.put(pairKey, current);
                boolean previousActive = previous != null && previous.isActive();
                boolean currentActive = current.isActive();
                if (currentActive && !previousActive) {
                    declared.add(current);
                    metrics.recordDeclaredWar(current.declarerNationId(), current.warTypeOrdinal());
                    return;
                }
                if (!currentActive && previousActive) {
                    Integer warIndex = activeWarIndexByPair.remove(pairKey);
                    if (warIndex != null) {
                        concluded.add(new ConcludedWarLane(warIndex, current.statusOrdinal()));
                        metrics.recordConcludedWar(current.declarerNationId(), current.targetNationId(), current.statusOrdinal());
                    }
                    return;
                }
                if (!currentActive) {
                    return;
                }
                Integer warIndex = activeWarIndexByPair.get(pairKey);
                int mask = current.diffMask(previous);
                if (warIndex != null && mask != 0) {
                    changed.add(new ChangedWarLane(warIndex, mask, current));
                }
            });

            if (changed.isEmpty() && declared.isEmpty() && concluded.isEmpty()) {
                return WarDelta.EMPTY;
            }

            changed.sort(Comparator.comparingInt(ChangedWarLane::warIndex));
            declared.sort(PAIR_ORDER);
            concluded.sort(Comparator.comparingInt(ConcludedWarLane::warIndex));

            IntArrayBuilder changedIndexes = new IntArrayBuilder(changed.size());
            IntArrayBuilder changedMasks = new IntArrayBuilder(changed.size());
            IntArrayBuilder changedLanes = new IntArrayBuilder(changed.size() * 2);
            for (ChangedWarLane lane : changed) {
                changedIndexes.add(lane.warIndex());
                changedMasks.add(lane.mask());
                if ((lane.mask() & WAR_MASK_COMBAT_STATE) != 0) {
                    changedLanes.add(lane.snapshot().packedCombatState());
                }
                if ((lane.mask() & WAR_MASK_FLAGS) != 0) {
                    changedLanes.add(lane.snapshot().packedFlags());
                }
            }

            IntArrayBuilder declaredPairs = new IntArrayBuilder(declared.size() * 2);
            IntArrayBuilder declaredLanes = new IntArrayBuilder(declared.size() * 3);
            for (WarSnapshot snapshot : declared) {
                activeWarIndexByPair.put(snapshot.pairKey(), nextWarIndex++);
                int declarerIndex = participantIndexByNationId.get(snapshot.declarerNationId());
                int targetIndex = participantIndexByNationId.get(snapshot.targetNationId());
                if (declarerIndex < 0 || targetIndex < 0) {
                    continue;
                }
                declaredPairs.add(declarerIndex);
                declaredPairs.add(targetIndex);
                declaredLanes.add(snapshot.startTurn());
                declaredLanes.add(snapshot.packedCombatState());
                declaredLanes.add(snapshot.packedFlags());
            }

            IntArrayBuilder concludedLanes = new IntArrayBuilder(concluded.size() * 2);
            for (ConcludedWarLane lane : concluded) {
                concludedLanes.add(lane.warIndex());
                concludedLanes.add(lane.endStatusOrdinal());
            }

            return new WarDelta(
                    changedIndexes.toArray(),
                    changedMasks.toArray(),
                    changedLanes.toArray(),
                    declaredPairs.toArray(),
                    declaredLanes.toArray(),
                    concludedLanes.toArray()
            );
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

    private record ChangedWarLane(int warIndex, int mask, WarSnapshot snapshot) {
    }

    private record ConcludedWarLane(int warIndex, int endStatusOrdinal) {
    }

    private static final class IntArrayBuilder {
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
            ensureCapacity(size + source.length);
            System.arraycopy(source, 0, values, size, source.length);
            size += source.length;
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

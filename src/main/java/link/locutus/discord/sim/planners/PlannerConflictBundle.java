package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Planner-local changed bundle extracted from a current assignment plus a small candidate delta.
 *
 * <p>The bundle is the connected component of the changed attackers in the union of the
 * current and candidate assignment graphs. That keeps exact validation local without
 * dropping linked war outcomes that can affect the score delta.</p>
 */
record PlannerConflictBundle(
        List<DBNationSnapshot> attackers,
        List<DBNationSnapshot> defenders,
    PlannerConflictBundle.PlannerAssignmentView currentAssignment,
    PlannerConflictBundle.PlannerAssignmentView candidateAssignment
) {
    @FunctionalInterface
    private interface AssignmentIndexer {
        void index(Map<Integer, Set<Integer>> attackerToDefenders, Map<Integer, Set<Integer>> defenderToAttackers);
    }

    @FunctionalInterface
    private interface AssignmentRestrictor {
        Map<Integer, List<Integer>> restrict(Set<Integer> impactedNationIds);
    }

    static final class PlannerAssignmentView {
        private static final PlannerAssignmentView EMPTY = new PlannerAssignmentView(new int[0], new int[0], new int[0], new int[0], new int[0]);

        private final int[] attackerIds;
        private final int[] attackerOffsets;
        private final int[] defenderCounts;
        private final int[] defenderIds;
        private final int[] warTypeOrdinals;

        private PlannerAssignmentView(
                int[] attackerIds,
                int[] attackerOffsets,
                int[] defenderCounts,
                int[] defenderIds,
                int[] warTypeOrdinals
        ) {
            this.attackerIds = attackerIds;
            this.attackerOffsets = attackerOffsets;
            this.defenderCounts = defenderCounts;
            this.defenderIds = defenderIds;
            this.warTypeOrdinals = warTypeOrdinals;
        }

        static PlannerAssignmentView empty() {
            return EMPTY;
        }

        static PlannerAssignmentView fromOrderedAssignment(Map<Integer, List<Integer>> assignment, List<Integer> attackerOrder) {
            return fromOrderedAssignment(assignment, attackerOrder, Map.of());
        }

        static PlannerAssignmentView fromOrderedAssignment(
                Map<Integer, List<Integer>> assignment,
                List<Integer> attackerOrder,
                Map<Long, Integer> warTypeOrdinalsByPair
        ) {
            if (assignment.isEmpty() || attackerOrder.isEmpty()) {
                return EMPTY;
            }
            int attackerCount = attackerOrder.size();
            int[] attackerIds = new int[attackerCount];
            int[] attackerOffsets = new int[attackerCount];
            int[] defenderCounts = new int[attackerCount];

            int edgeCount = 0;
            for (int index = 0; index < attackerCount; index++) {
                int attackerId = attackerOrder.get(index);
                attackerIds[index] = attackerId;
                attackerOffsets[index] = edgeCount;
                List<Integer> defenderIds = assignment.get(attackerId);
                if (defenderIds != null && !defenderIds.isEmpty()) {
                    defenderCounts[index] = defenderIds.size();
                    edgeCount += defenderIds.size();
                }
            }

            if (edgeCount == 0) {
                return EMPTY;
            }

            int[] flatDefenderIds = new int[edgeCount];
            int[] flatWarTypeOrdinals = new int[edgeCount];
            for (int index = 0; index < attackerCount; index++) {
                List<Integer> defenderIds = assignment.get(attackerIds[index]);
                if (defenderIds == null || defenderIds.isEmpty()) {
                    continue;
                }
                int offset = attackerOffsets[index];
                for (int defenderIndex = 0; defenderIndex < defenderIds.size(); defenderIndex++) {
                    int defenderId = defenderIds.get(defenderIndex);
                    flatDefenderIds[offset + defenderIndex] = defenderId;
                    flatWarTypeOrdinals[offset + defenderIndex] = warTypeForPair(warTypeOrdinalsByPair, attackerIds[index], defenderId);
                }
            }

            return new PlannerAssignmentView(attackerIds, attackerOffsets, defenderCounts, flatDefenderIds, flatWarTypeOrdinals);
        }

        boolean isEmpty() {
            return defenderIds.length == 0;
        }

        int attackerCount() {
            return attackerIds.length;
        }

        int attackerIdAt(int attackerIndex) {
            return attackerIds[attackerIndex];
        }

        int defenderCountAt(int attackerIndex) {
            return defenderCounts[attackerIndex];
        }

        int edgeCount() {
            return defenderIds.length;
        }

        int commonPrefixEdgeCount(PlannerAssignmentView other) {
            if (other == null || isEmpty() || other.isEmpty()) {
                return 0;
            }
            int leftAttackerIndex = firstAttackerWithEdges(0);
            int rightAttackerIndex = other.firstAttackerWithEdges(0);
            if (leftAttackerIndex < 0 || rightAttackerIndex < 0) {
                return 0;
            }

            int leftDefenderIndex = 0;
            int rightDefenderIndex = 0;
            int matchedEdges = 0;
            while (leftAttackerIndex >= 0 && rightAttackerIndex >= 0) {
                if (attackerIds[leftAttackerIndex] != other.attackerIds[rightAttackerIndex]) {
                    break;
                }
                if (defenderIdAt(leftAttackerIndex, leftDefenderIndex)
                        != other.defenderIdAt(rightAttackerIndex, rightDefenderIndex)) {
                    break;
                }
                if (warTypeOrdinalAt(leftAttackerIndex, leftDefenderIndex)
                        != other.warTypeOrdinalAt(rightAttackerIndex, rightDefenderIndex)) {
                    break;
                }
                matchedEdges++;

                leftDefenderIndex++;
                if (leftDefenderIndex >= defenderCounts[leftAttackerIndex]) {
                    leftAttackerIndex = firstAttackerWithEdges(leftAttackerIndex + 1);
                    leftDefenderIndex = 0;
                }

                rightDefenderIndex++;
                if (rightDefenderIndex >= other.defenderCounts[rightAttackerIndex]) {
                    rightAttackerIndex = other.firstAttackerWithEdges(rightAttackerIndex + 1);
                    rightDefenderIndex = 0;
                }
            }
            return matchedEdges;
        }

        PlannerAssignmentView prefixEdges(int edgeCount) {
            return sliceEdges(0, edgeCount);
        }

        PlannerAssignmentView suffixEdges(int startEdgeIndex) {
            return sliceEdges(startEdgeIndex, defenderIds.length - startEdgeIndex);
        }

        int defenderIdAt(int attackerIndex, int defenderIndex) {
            if (defenderIndex < 0 || defenderIndex >= defenderCounts[attackerIndex]) {
                throw new IndexOutOfBoundsException(defenderIndex);
            }
            return defenderIds[attackerOffsets[attackerIndex] + defenderIndex];
        }

        int warTypeOrdinalAt(int attackerIndex, int defenderIndex) {
            if (defenderIndex < 0 || defenderIndex >= defenderCounts[attackerIndex]) {
                throw new IndexOutOfBoundsException(defenderIndex);
            }
            return warTypeOrdinals[attackerOffsets[attackerIndex] + defenderIndex];
        }

        Map<Integer, List<Integer>> toAssignmentMap() {
            if (isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<Integer, List<Integer>> assignment = new LinkedHashMap<>(attackerIds.length);
            for (int attackerIndex = 0; attackerIndex < attackerIds.length; attackerIndex++) {
                int defenderCount = defenderCounts[attackerIndex];
                if (defenderCount == 0) {
                    continue;
                }
                ArrayList<Integer> defenderList = new ArrayList<>(defenderCount);
                int offset = attackerOffsets[attackerIndex];
                for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
                    defenderList.add(defenderIds[offset + defenderIndex]);
                }
                assignment.put(attackerIds[attackerIndex], List.copyOf(defenderList));
            }
            return assignment.isEmpty() ? Map.of() : Collections.unmodifiableMap(assignment);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PlannerAssignmentView other)) {
                return false;
            }
            return Arrays.equals(attackerIds, other.attackerIds)
                    && Arrays.equals(attackerOffsets, other.attackerOffsets)
                    && Arrays.equals(defenderCounts, other.defenderCounts)
                    && Arrays.equals(defenderIds, other.defenderIds)
                    && Arrays.equals(warTypeOrdinals, other.warTypeOrdinals);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(attackerIds);
            result = 31 * result + Arrays.hashCode(attackerOffsets);
            result = 31 * result + Arrays.hashCode(defenderCounts);
            result = 31 * result + Arrays.hashCode(defenderIds);
            result = 31 * result + Arrays.hashCode(warTypeOrdinals);
            return result;
        }

        private PlannerAssignmentView sliceEdges(int startEdgeIndex, int edgeCount) {
            if (startEdgeIndex < 0 || edgeCount < 0 || startEdgeIndex + edgeCount > defenderIds.length) {
                throw new IndexOutOfBoundsException("Invalid edge slice");
            }
            if (edgeCount == 0) {
                return EMPTY;
            }

            int remainingSkip = startEdgeIndex;
            int remainingTake = edgeCount;
            int selectedAttackers = 0;
            for (int attackerIndex = 0; attackerIndex < attackerIds.length && remainingTake > 0; attackerIndex++) {
                int defenderCount = defenderCounts[attackerIndex];
                if (remainingSkip >= defenderCount) {
                    remainingSkip -= defenderCount;
                    continue;
                }
                int takenFromAttacker = Math.min(defenderCount - remainingSkip, remainingTake);
                if (takenFromAttacker > 0) {
                    selectedAttackers++;
                    remainingTake -= takenFromAttacker;
                }
                remainingSkip = 0;
            }

            int[] sliceAttackerIds = new int[selectedAttackers];
            int[] sliceAttackerOffsets = new int[selectedAttackers];
            int[] sliceDefenderCounts = new int[selectedAttackers];
            int nextAttacker = 0;
            int nextOffset = 0;
            remainingSkip = startEdgeIndex;
            remainingTake = edgeCount;
            for (int attackerIndex = 0; attackerIndex < attackerIds.length && remainingTake > 0; attackerIndex++) {
                int defenderCount = defenderCounts[attackerIndex];
                if (remainingSkip >= defenderCount) {
                    remainingSkip -= defenderCount;
                    continue;
                }
                int takenFromAttacker = Math.min(defenderCount - remainingSkip, remainingTake);
                if (takenFromAttacker > 0) {
                    sliceAttackerIds[nextAttacker] = attackerIds[attackerIndex];
                    sliceAttackerOffsets[nextAttacker] = nextOffset;
                    sliceDefenderCounts[nextAttacker] = takenFromAttacker;
                    nextOffset += takenFromAttacker;
                    nextAttacker++;
                    remainingTake -= takenFromAttacker;
                }
                remainingSkip = 0;
            }
                int[] sliceDefenderIds = Arrays.copyOfRange(defenderIds, startEdgeIndex, startEdgeIndex + edgeCount);
                int[] sliceWarTypeOrdinals = Arrays.copyOfRange(warTypeOrdinals, startEdgeIndex, startEdgeIndex + edgeCount);
                return new PlannerAssignmentView(
                    sliceAttackerIds,
                    sliceAttackerOffsets,
                    sliceDefenderCounts,
                    sliceDefenderIds,
                    sliceWarTypeOrdinals
                );
        }

        private int firstAttackerWithEdges(int startAttackerIndex) {
            for (int attackerIndex = startAttackerIndex; attackerIndex < attackerIds.length; attackerIndex++) {
                if (defenderCounts[attackerIndex] > 0) {
                    return attackerIndex;
                }
            }
            return -1;
        }

        private static int warTypeForPair(Map<Long, Integer> warTypeOrdinalsByPair, int attackerId, int defenderId) {
            if (warTypeOrdinalsByPair == null || warTypeOrdinalsByPair.isEmpty()) {
                return link.locutus.discord.apiv1.enums.WarType.ORD.ordinal();
            }
            Integer ordinal = warTypeOrdinalsByPair.get(PlannerLocalConflict.pairKey(attackerId, defenderId));
            return ordinal != null ? ordinal : link.locutus.discord.apiv1.enums.WarType.ORD.ordinal();
        }
    }

    PlannerConflictBundle {
        attackers = Objects.requireNonNull(attackers, "attackers");
        defenders = Objects.requireNonNull(defenders, "defenders");
        currentAssignment = Objects.requireNonNull(currentAssignment, "currentAssignment");
        candidateAssignment = Objects.requireNonNull(candidateAssignment, "candidateAssignment");
    }

    static PlannerConflictBundle extract(
            Map<Integer, List<Integer>> currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders
    ) {
        return extract(currentAssignment, candidateChange, attackers, defenders, Map.of());
    }

    static PlannerConflictBundle extract(
            Map<Integer, List<Integer>> currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        Objects.requireNonNull(currentAssignment, "currentAssignment");
        return extract(
            candidateChange,
            attackers,
            defenders,
            (attackerToDefenders, defenderToAttackers) -> indexAssignment(
                currentAssignment,
                attackerToDefenders,
                defenderToAttackers
            ),
            impactedNationIds -> restrictAssignment(currentAssignment, impactedNationIds),
            warTypeOrdinalsByPair
        );
    }

    static PlannerConflictBundle extract(
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders
    ) {
        return extract(currentAssignment, candidateChange, attackers, defenders, Map.of());
    }

    static PlannerConflictBundle extract(
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT)) {
            Objects.requireNonNull(currentAssignment, "currentAssignment");
            Objects.requireNonNull(candidateChange, "candidateChange");
            Objects.requireNonNull(attackers, "attackers");
            Objects.requireNonNull(defenders, "defenders");

            boolean[] impactedAttackerSlots = new boolean[currentAssignment.attackerCount()];
            boolean[] impactedDefenderSlots = new boolean[currentAssignment.defenderCount()];
            PlannerAssignmentSession.DefenderReverseIndex defenderReverseIndex = currentAssignment.defenderReverseIndex();

            if (!extractImpactedSlots(
                currentAssignment,
                candidateChange,
                impactedAttackerSlots,
                impactedDefenderSlots,
                defenderReverseIndex.defenderOffsets(),
                defenderReverseIndex.defenderToAttackerSlots()
            )) {
            return new PlannerConflictBundle(
                List.of(),
                List.of(),
                PlannerAssignmentView.empty(),
                PlannerAssignmentView.empty()
            );
            }

            List<DBNationSnapshot> bundleAttackers = filterAttackerSnapshots(attackers, currentAssignment, impactedAttackerSlots);
            List<DBNationSnapshot> bundleDefenders = filterDefenderSnapshots(defenders, currentAssignment, impactedDefenderSlots);
        PlannerAssignmentView bundleCurrent = restrictAssignment(
            currentAssignment,
            bundleAttackers,
            impactedDefenderSlots,
            warTypeOrdinalsByPair
        );
        PlannerAssignmentView bundleCandidate = applyChange(
            currentAssignment,
            candidateChange,
            bundleAttackers,
            impactedDefenderSlots,
            warTypeOrdinalsByPair
        );
            PlannerProfiler.addCounter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT, "attackers", bundleAttackers.size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT, "defenders", bundleDefenders.size());
            return new PlannerConflictBundle(bundleAttackers, bundleDefenders, bundleCurrent, bundleCandidate);
        }
    }

    private static PlannerConflictBundle extract(
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            AssignmentIndexer assignmentIndexer,
            AssignmentRestrictor assignmentRestrictor,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT)) {
            Objects.requireNonNull(candidateChange, "candidateChange");
            Objects.requireNonNull(attackers, "attackers");
            Objects.requireNonNull(defenders, "defenders");
            Objects.requireNonNull(assignmentIndexer, "assignmentIndexer");
            Objects.requireNonNull(assignmentRestrictor, "assignmentRestrictor");

            Map<Integer, DBNationSnapshot> attackerById = indexSnapshots(attackers);
            Map<Integer, DBNationSnapshot> defenderById = indexSnapshots(defenders);

            Set<Integer> impactedNationIds = extractImpactedNationIds(
                candidateChange,
                attackerById.keySet(),
                defenderById.keySet(),
                assignmentIndexer
            );

            if (impactedNationIds.isEmpty()) {
                return new PlannerConflictBundle(
                        List.of(),
                        List.of(),
                        PlannerAssignmentView.empty(),
                        PlannerAssignmentView.empty()
                );
            }

            List<DBNationSnapshot> bundleAttackers = filterSnapshots(attackers, impactedNationIds);
            List<DBNationSnapshot> bundleDefenders = filterSnapshots(defenders, impactedNationIds);
            List<Integer> attackerOrder = snapshotNationIds(bundleAttackers);
            Map<Integer, List<Integer>> bundleCurrent = orderAssignmentByAttackers(
                assignmentRestrictor.restrict(impactedNationIds),
                attackerOrder
            );
            Map<Integer, List<Integer>> bundleCandidate = orderAssignmentByAttackers(
                applyChange(bundleCurrent, candidateChange, impactedNationIds),
                attackerOrder
            );
            PlannerProfiler.addCounter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT, "attackers", bundleAttackers.size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT, "defenders", bundleDefenders.size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.CONFLICT_BUNDLE_EXTRACT, "impactedNationIds", impactedNationIds.size());

            return new PlannerConflictBundle(
                    bundleAttackers,
                    bundleDefenders,
                    PlannerAssignmentView.fromOrderedAssignment(bundleCurrent, attackerOrder, warTypeOrdinalsByPair),
                    PlannerAssignmentView.fromOrderedAssignment(bundleCandidate, attackerOrder, warTypeOrdinalsByPair)
            );
        }
    }

    boolean isEmpty() {
        return attackers.isEmpty() && defenders.isEmpty();
    }

    private static boolean extractImpactedSlots(
            PlannerAssignmentSession assignment,
            PlannerAssignmentChange candidateChange,
            boolean[] impactedAttackerSlots,
            boolean[] impactedDefenderSlots,
            int[] defenderOffsets,
            int[] defenderToAttackerSlots
    ) {
        int attackerCount = assignment.attackerCount();
        int[] queue = new int[attackerCount + assignment.defenderCount()];
        int queueHead = 0;
        int queueTail = 0;

        for (int index = 0; index < candidateChange.size(); index++) {
            int attackerSlot = assignment.attackerSlot(candidateChange.attackerIdAt(index));
            if (attackerSlot >= 0 && !impactedAttackerSlots[attackerSlot]) {
                impactedAttackerSlots[attackerSlot] = true;
                queue[queueTail++] = attackerSlot;
            }
        }

        if (queueTail == 0) {
            return false;
        }

        while (queueHead < queueTail) {
            int node = queue[queueHead++];
            if (node < attackerCount) {
                int attackerSlot = node;
                for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
                    int defenderSlot = assignment.defenderSlotAt(attackerSlot, defenderIndex);
                    if (!impactedDefenderSlots[defenderSlot]) {
                        impactedDefenderSlots[defenderSlot] = true;
                        queue[queueTail++] = attackerCount + defenderSlot;
                    }
                }
                queueTail = enqueueChangedDefendersForAttacker(
                        assignment,
                        candidateChange,
                        attackerSlot,
                        impactedDefenderSlots,
                        queue,
                        queueTail,
                        attackerCount
                );
            } else {
                int defenderSlot = node - attackerCount;
                for (int index = defenderOffsets[defenderSlot]; index < defenderOffsets[defenderSlot + 1]; index++) {
                    int attackerSlot = defenderToAttackerSlots[index];
                    if (!impactedAttackerSlots[attackerSlot]) {
                        impactedAttackerSlots[attackerSlot] = true;
                        queue[queueTail++] = attackerSlot;
                    }
                }
                queueTail = enqueueChangedAttackersForDefender(
                        assignment,
                        candidateChange,
                        defenderSlot,
                        impactedAttackerSlots,
                        queue,
                        queueTail
                );
            }
        }
        return true;
    }

    private static int enqueueChangedDefendersForAttacker(
            PlannerAssignmentSession assignment,
            PlannerAssignmentChange candidateChange,
            int attackerSlot,
            boolean[] impactedDefenderSlots,
            int[] queue,
            int queueTail,
            int attackerCount
    ) {
        int attackerId = assignment.attackerNationIdAt(attackerSlot);
        for (int changeIndex = 0; changeIndex < candidateChange.size(); changeIndex++) {
            if (candidateChange.attackerIdAt(changeIndex) != attackerId) {
                continue;
            }
            for (int defenderIndex = 0; defenderIndex < candidateChange.defenderCountAt(changeIndex); defenderIndex++) {
                int defenderSlot = assignment.defenderSlot(candidateChange.defenderIdAt(changeIndex, defenderIndex));
                if (defenderSlot >= 0 && !impactedDefenderSlots[defenderSlot]) {
                    impactedDefenderSlots[defenderSlot] = true;
                    queue[queueTail++] = attackerCount + defenderSlot;
                }
            }
            return queueTail;
        }
        return queueTail;
    }

    private static int enqueueChangedAttackersForDefender(
            PlannerAssignmentSession assignment,
            PlannerAssignmentChange candidateChange,
            int defenderSlot,
            boolean[] impactedAttackerSlots,
            int[] queue,
            int queueTail
    ) {
        int defenderId = assignment.defenderNationIdAt(defenderSlot);
        for (int changeIndex = 0; changeIndex < candidateChange.size(); changeIndex++) {
            boolean matches = false;
            for (int defenderIndex = 0; defenderIndex < candidateChange.defenderCountAt(changeIndex); defenderIndex++) {
                if (candidateChange.defenderIdAt(changeIndex, defenderIndex) == defenderId) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            int attackerSlot = assignment.attackerSlot(candidateChange.attackerIdAt(changeIndex));
            if (attackerSlot >= 0 && !impactedAttackerSlots[attackerSlot]) {
                impactedAttackerSlots[attackerSlot] = true;
                queue[queueTail++] = attackerSlot;
            }
        }
        return queueTail;
    }

    private static Map<Integer, DBNationSnapshot> indexSnapshots(Collection<DBNationSnapshot> snapshots) {
        Map<Integer, DBNationSnapshot> indexed = new Int2ObjectLinkedOpenHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            indexed.put(snapshot.nationId(), snapshot);
        }
        return indexed;
    }

    private static Set<Integer> extractImpactedNationIds(
            PlannerAssignmentChange candidateChange,
            Set<Integer> attackerIds,
            Set<Integer> defenderIds,
            AssignmentIndexer assignmentIndexer
    ) {
        Map<Integer, Set<Integer>> attackerToDefenders = new Int2ObjectOpenHashMap<>();
        Map<Integer, Set<Integer>> defenderToAttackers = new Int2ObjectOpenHashMap<>();
        assignmentIndexer.index(attackerToDefenders, defenderToAttackers);
        indexChange(candidateChange, attackerToDefenders, defenderToAttackers);

        IntLinkedOpenHashSet seeds = new IntLinkedOpenHashSet();
        for (int i = 0; i < candidateChange.size(); i++) {
            int attackerId = candidateChange.attackerIdAt(i);
            if (attackerIds.contains(attackerId)) {
                seeds.add(attackerId);
            }
        }

        if (seeds.isEmpty()) {
            return Set.of();
        }

        IntOpenHashSet impacted = new IntOpenHashSet(seeds);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue(seeds.size());
        for (int seed : seeds) {
            queue.enqueue(seed);
        }
        while (!queue.isEmpty()) {
            int nodeId = queue.dequeueInt();
            if (attackerIds.contains(nodeId)) {
                for (Integer defenderId : attackerToDefenders.getOrDefault(nodeId, Set.of())) {
                    if (impacted.add(defenderId)) {
                        queue.enqueue(defenderId);
                    }
                }
            }
            if (defenderIds.contains(nodeId)) {
                for (Integer attackerId : defenderToAttackers.getOrDefault(nodeId, Set.of())) {
                    if (impacted.add(attackerId)) {
                        queue.enqueue(attackerId);
                    }
                }
            }
        }
        return impacted;
    }

    private static void indexAssignment(
            Map<Integer, List<Integer>> assignment,
            Map<Integer, Set<Integer>> attackerToDefenders,
            Map<Integer, Set<Integer>> defenderToAttackers
    ) {
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerId = entry.getKey();
            List<Integer> defenderIds = entry.getValue();
            if (attackerId == null || defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            Set<Integer> defenders = attackerToDefenders.computeIfAbsent(attackerId, ignored -> new IntLinkedOpenHashSet());
            for (Integer defenderId : defenderIds) {
                if (defenderId == null) {
                    continue;
                }
                defenders.add(defenderId);
                defenderToAttackers.computeIfAbsent(defenderId, ignored -> new IntLinkedOpenHashSet()).add(attackerId);
            }
        }
    }

    private static void indexAssignment(
            PlannerAssignmentSession assignment,
            Map<Integer, Set<Integer>> attackerToDefenders,
            Map<Integer, Set<Integer>> defenderToAttackers
    ) {
        for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
            if (!assignment.hasAssignments(attackerSlot)) {
                continue;
            }
            int attackerId = assignment.attackerNationIdAt(attackerSlot);
            Set<Integer> defenders = attackerToDefenders.computeIfAbsent(attackerId, ignored -> new IntLinkedOpenHashSet());
            for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
                int defenderId = assignment.defenderNationIdAt(assignment.defenderSlotAt(attackerSlot, defenderIndex));
                defenders.add(defenderId);
                defenderToAttackers.computeIfAbsent(defenderId, ignored -> new IntLinkedOpenHashSet()).add(attackerId);
            }
        }
    }

    private static void indexChange(
            PlannerAssignmentChange candidateChange,
            Map<Integer, Set<Integer>> attackerToDefenders,
            Map<Integer, Set<Integer>> defenderToAttackers
    ) {
        for (int i = 0; i < candidateChange.size(); i++) {
            Integer attackerId = candidateChange.attackerIdAt(i);
            int defenderCount = candidateChange.defenderCountAt(i);
            if (defenderCount == 0) {
                continue;
            }
            Set<Integer> defenders = attackerToDefenders.computeIfAbsent(attackerId, ignored -> new IntLinkedOpenHashSet());
            for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
                int defenderId = candidateChange.defenderIdAt(i, defenderIndex);
                defenders.add(defenderId);
                defenderToAttackers.computeIfAbsent(defenderId, ignored -> new IntLinkedOpenHashSet()).add(attackerId);
            }
        }
    }

    private static List<DBNationSnapshot> filterSnapshots(
            Collection<DBNationSnapshot> snapshots,
            Set<Integer> impactedNationIds
    ) {
        List<DBNationSnapshot> filtered = new ObjectArrayList<>();
        for (DBNationSnapshot snapshot : snapshots) {
            if (impactedNationIds.contains(snapshot.nationId())) {
                filtered.add(snapshot);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<DBNationSnapshot> filterAttackerSnapshots(
            Collection<DBNationSnapshot> snapshots,
            PlannerAssignmentSession assignment,
            boolean[] impactedAttackerSlots
    ) {
        List<DBNationSnapshot> filtered = new ObjectArrayList<>();
        for (DBNationSnapshot snapshot : snapshots) {
            int attackerSlot = assignment.attackerSlot(snapshot.nationId());
            if (attackerSlot >= 0 && impactedAttackerSlots[attackerSlot]) {
                filtered.add(snapshot);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<DBNationSnapshot> filterDefenderSnapshots(
            Collection<DBNationSnapshot> snapshots,
            PlannerAssignmentSession assignment,
            boolean[] impactedDefenderSlots
    ) {
        List<DBNationSnapshot> filtered = new ObjectArrayList<>();
        for (DBNationSnapshot snapshot : snapshots) {
            int defenderSlot = assignment.defenderSlot(snapshot.nationId());
            if (defenderSlot >= 0 && impactedDefenderSlots[defenderSlot]) {
                filtered.add(snapshot);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<Integer> snapshotNationIds(List<DBNationSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<Integer> nationIds = new IntArrayList(snapshots.size());
        for (DBNationSnapshot snapshot : snapshots) {
            nationIds.add(snapshot.nationId());
        }
        return List.copyOf(nationIds);
    }

    private static Map<Integer, List<Integer>> orderAssignmentByAttackers(
            Map<Integer, List<Integer>> assignment,
            List<Integer> attackerOrder
    ) {
        if (assignment.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Integer>> ordered = new Int2ObjectLinkedOpenHashMap<>(assignment.size());
        for (int attackerId : attackerOrder) {
            List<Integer> defenderIds = assignment.get(attackerId);
            if (defenderIds != null && !defenderIds.isEmpty()) {
                ordered.put(attackerId, defenderIds);
            }
        }
        if (ordered.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static Map<Integer, List<Integer>> restrictAssignment(
            Map<Integer, List<Integer>> assignment,
            Set<Integer> impactedNationIds
    ) {
        if (assignment.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Integer>> filtered = new Int2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerId = entry.getKey();
            if (attackerId == null || !impactedNationIds.contains(attackerId)) {
                continue;
            }
            List<Integer> defenderIds = entry.getValue();
            if (defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            IntLinkedOpenHashSet compacted = new IntLinkedOpenHashSet();
            for (Integer defenderId : defenderIds) {
                if (defenderId != null && impactedNationIds.contains(defenderId)) {
                    compacted.add(defenderId);
                }
            }
            if (!compacted.isEmpty()) {
                filtered.put(attackerId, List.copyOf(compacted));
            }
        }
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(filtered);
    }

    private static Map<Integer, List<Integer>> restrictAssignment(
            PlannerAssignmentSession assignment,
            Set<Integer> impactedNationIds
    ) {
        Map<Integer, List<Integer>> filtered = new Int2ObjectLinkedOpenHashMap<>();
        for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
            int attackerId = assignment.attackerNationIdAt(attackerSlot);
            if (!impactedNationIds.contains(attackerId) || !assignment.hasAssignments(attackerSlot)) {
                continue;
            }
            IntLinkedOpenHashSet compacted = new IntLinkedOpenHashSet();
            for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
                int defenderId = assignment.defenderNationIdAt(assignment.defenderSlotAt(attackerSlot, defenderIndex));
                if (impactedNationIds.contains(defenderId)) {
                    compacted.add(defenderId);
                }
            }
            if (!compacted.isEmpty()) {
                filtered.put(attackerId, List.copyOf(compacted));
            }
        }
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(filtered);
    }

    private static PlannerAssignmentView restrictAssignment(
            PlannerAssignmentSession assignment,
            List<DBNationSnapshot> orderedAttackers,
            boolean[] impactedDefenderSlots,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        if (orderedAttackers.isEmpty()) {
            return PlannerAssignmentView.empty();
        }
        int attackerCount = orderedAttackers.size();
        int[] attackerIds = new int[attackerCount];
        int[] attackerOffsets = new int[attackerCount];
        int[] defenderCounts = new int[attackerCount];
        int edgeCount = 0;

        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            int attackerId = orderedAttackers.get(attackerIndex).nationId();
            attackerIds[attackerIndex] = attackerId;
            attackerOffsets[attackerIndex] = edgeCount;
            int attackerSlot = assignment.attackerSlot(attackerId);
            int defenderCount = 0;
            for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
                int defenderSlot = assignment.defenderSlotAt(attackerSlot, defenderIndex);
                if (impactedDefenderSlots[defenderSlot]) {
                    defenderCount++;
                }
            }
            defenderCounts[attackerIndex] = defenderCount;
            edgeCount += defenderCount;
        }

        if (edgeCount == 0) {
            return PlannerAssignmentView.empty();
        }

        int[] defenderIds = new int[edgeCount];
        int[] warTypeOrdinals = new int[edgeCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            int attackerSlot = assignment.attackerSlot(attackerIds[attackerIndex]);
            int writeIndex = attackerOffsets[attackerIndex];
            for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
                int defenderSlot = assignment.defenderSlotAt(attackerSlot, defenderIndex);
                if (impactedDefenderSlots[defenderSlot]) {
                    int defenderId = assignment.defenderNationIdAt(defenderSlot);
                    defenderIds[writeIndex] = defenderId;
                    warTypeOrdinals[writeIndex] = PlannerAssignmentView.warTypeForPair(
                            warTypeOrdinalsByPair,
                            attackerIds[attackerIndex],
                            defenderId
                    );
                    writeIndex++;
                }
            }
        }
        return new PlannerAssignmentView(attackerIds, attackerOffsets, defenderCounts, defenderIds, warTypeOrdinals);
    }

    private static Map<Integer, List<Integer>> applyChange(
            Map<Integer, List<Integer>> currentAssignment,
            PlannerAssignmentChange candidateChange,
            Set<Integer> impactedNationIds
    ) {
        if (currentAssignment.isEmpty() && candidateChange.size() == 0) {
            return Map.of();
        }
        Map<Integer, List<Integer>> updated = new Int2ObjectLinkedOpenHashMap<>(currentAssignment);
        for (int i = 0; i < candidateChange.size(); i++) {
            int attackerId = candidateChange.attackerIdAt(i);
            if (!impactedNationIds.contains(attackerId)) {
                continue;
            }
            List<Integer> filtered = filterDefenders(candidateChange, i, impactedNationIds);
            if (filtered.isEmpty()) {
                updated.remove(attackerId);
            } else {
                updated.put(attackerId, filtered);
            }
        }
        if (updated.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(updated);
    }

    private static PlannerAssignmentView applyChange(
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            List<DBNationSnapshot> orderedAttackers,
            boolean[] impactedDefenderSlots,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        if (orderedAttackers.isEmpty()) {
            return PlannerAssignmentView.empty();
        }
        int attackerCount = orderedAttackers.size();
        int[] attackerIds = new int[attackerCount];
        int[] attackerOffsets = new int[attackerCount];
        int[] defenderCounts = new int[attackerCount];
        int edgeCount = 0;

        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            int attackerId = orderedAttackers.get(attackerIndex).nationId();
            attackerIds[attackerIndex] = attackerId;
            attackerOffsets[attackerIndex] = edgeCount;
            int changeIndex = changeIndex(candidateChange, attackerId);
            int defenderCount = changeIndex >= 0
                    ? changedDefenderCount(currentAssignment, candidateChange, changeIndex, impactedDefenderSlots)
                    : currentDefenderCount(currentAssignment, attackerId, impactedDefenderSlots);
            defenderCounts[attackerIndex] = defenderCount;
            edgeCount += defenderCount;
        }

        if (edgeCount == 0) {
            return PlannerAssignmentView.empty();
        }

        int[] defenderIds = new int[edgeCount];
        int[] warTypeOrdinals = new int[edgeCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            int attackerId = attackerIds[attackerIndex];
            int writeIndex = attackerOffsets[attackerIndex];
            int changeIndex = changeIndex(candidateChange, attackerId);
            if (changeIndex >= 0) {
                for (int defenderIndex = 0; defenderIndex < candidateChange.defenderCountAt(changeIndex); defenderIndex++) {
                    int defenderId = candidateChange.defenderIdAt(changeIndex, defenderIndex);
                    int defenderSlot = currentAssignment.defenderSlot(defenderId);
                    if (defenderSlot >= 0 && impactedDefenderSlots[defenderSlot]) {
                        defenderIds[writeIndex] = defenderId;
                        warTypeOrdinals[writeIndex] = PlannerAssignmentView.warTypeForPair(
                                warTypeOrdinalsByPair,
                                attackerId,
                                defenderId
                        );
                        writeIndex++;
                    }
                }
            } else {
                int attackerSlot = currentAssignment.attackerSlot(attackerId);
                for (int defenderIndex = 0; defenderIndex < currentAssignment.assignedCount(attackerSlot); defenderIndex++) {
                    int defenderSlot = currentAssignment.defenderSlotAt(attackerSlot, defenderIndex);
                    if (impactedDefenderSlots[defenderSlot]) {
                        int defenderId = currentAssignment.defenderNationIdAt(defenderSlot);
                        defenderIds[writeIndex] = defenderId;
                        warTypeOrdinals[writeIndex] = PlannerAssignmentView.warTypeForPair(
                                warTypeOrdinalsByPair,
                                attackerId,
                                defenderId
                        );
                        writeIndex++;
                    }
                }
            }
        }
        return new PlannerAssignmentView(attackerIds, attackerOffsets, defenderCounts, defenderIds, warTypeOrdinals);
    }

    private static int currentDefenderCount(
            PlannerAssignmentSession assignment,
            int attackerId,
            boolean[] impactedDefenderSlots
    ) {
        int attackerSlot = assignment.attackerSlot(attackerId);
        int count = 0;
        for (int defenderIndex = 0; defenderIndex < assignment.assignedCount(attackerSlot); defenderIndex++) {
            int defenderSlot = assignment.defenderSlotAt(attackerSlot, defenderIndex);
            if (impactedDefenderSlots[defenderSlot]) {
                count++;
            }
        }
        return count;
    }

    private static int changedDefenderCount(
            PlannerAssignmentSession assignment,
            PlannerAssignmentChange candidateChange,
            int changeIndex,
            boolean[] impactedDefenderSlots
    ) {
        int count = 0;
        for (int defenderIndex = 0; defenderIndex < candidateChange.defenderCountAt(changeIndex); defenderIndex++) {
            int defenderSlot = assignment.defenderSlot(candidateChange.defenderIdAt(changeIndex, defenderIndex));
            if (defenderSlot >= 0 && impactedDefenderSlots[defenderSlot]) {
                count++;
            }
        }
        return count;
    }

    private static int changeIndex(PlannerAssignmentChange candidateChange, int attackerId) {
        for (int index = 0; index < candidateChange.size(); index++) {
            if (candidateChange.attackerIdAt(index) == attackerId) {
                return index;
            }
        }
        return -1;
    }

    private static List<Integer> filterDefenders(
            PlannerAssignmentChange candidateChange,
            int changeIndex,
            Set<Integer> impactedNationIds
    ) {
        int defenderCount = candidateChange.defenderCountAt(changeIndex);
        if (defenderCount == 0) {
            return List.of();
        }
        LinkedHashSet<Integer> compacted = new LinkedHashSet<>();
        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            int defenderId = candidateChange.defenderIdAt(changeIndex, defenderIndex);
            if (impactedNationIds.contains(defenderId)) {
                compacted.add(defenderId);
            }
        }
        return compacted.isEmpty() ? List.of() : List.copyOf(compacted);
    }
}

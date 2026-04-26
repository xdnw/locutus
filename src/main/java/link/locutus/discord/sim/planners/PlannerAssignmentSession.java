package link.locutus.discord.sim.planners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dense mutable assignment owner for planner-local refinement.
 */
final class PlannerAssignmentSession {
    private final int[] attackerNationIds;
    private final int[] defenderNationIds;
    private final int[] attackerCaps;
    private final int[] defenderCaps;
    private final Map<Integer, Integer> attackerSlotByNationId;
    private final Map<Integer, Integer> defenderSlotByNationId;
    private final int[] attackerOffsets;
    private final int[] assignmentLengths;
    private final int[] assignedDefenderSlots;
    private final boolean[] lockedAssignments;
    private final int[] defenderAssignedCount;
    private final PlannerAssignmentChange changeScratch;

    private PlannerAssignmentSession(
            int[] attackerNationIds,
            int[] defenderNationIds,
            int[] attackerCaps,
            int[] defenderCaps,
            Map<Integer, Integer> attackerSlotByNationId,
            Map<Integer, Integer> defenderSlotByNationId,
            int[] attackerOffsets,
            int[] assignmentLengths,
            int[] assignedDefenderSlots,
            boolean[] lockedAssignments,
            int[] defenderAssignedCount,
            PlannerAssignmentChange changeScratch
    ) {
        this.attackerNationIds = attackerNationIds;
        this.defenderNationIds = defenderNationIds;
        this.attackerCaps = attackerCaps;
        this.defenderCaps = defenderCaps;
        this.attackerSlotByNationId = attackerSlotByNationId;
        this.defenderSlotByNationId = defenderSlotByNationId;
        this.attackerOffsets = attackerOffsets;
        this.assignmentLengths = assignmentLengths;
        this.assignedDefenderSlots = assignedDefenderSlots;
        this.lockedAssignments = lockedAssignments;
        this.defenderAssignedCount = defenderAssignedCount;
        this.changeScratch = changeScratch;
    }

    static PlannerAssignmentSession create(
            Map<Integer, List<Integer>> assignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, Integer> attackerCapsByNationId,
            Map<Integer, Integer> defenderCapsByNationId
    ) {
        return create(assignment, attackers, defenders, attackerCapsByNationId, defenderCapsByNationId, List.of());
    }

    static PlannerAssignmentSession create(
            Map<Integer, List<Integer>> assignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, Integer> attackerCapsByNationId,
            Map<Integer, Integer> defenderCapsByNationId,
            List<BlitzFixedEdge> lockedEdges
    ) {
        int attackerCount = attackers.size();
        int defenderCount = defenders.size();

        int[] attackerNationIds = new int[attackerCount];
        int[] defenderNationIds = new int[defenderCount];
        int[] attackerCaps = new int[attackerCount];
        int[] defenderCaps = new int[defenderCount];
        Map<Integer, Integer> attackerSlotByNationId = new HashMap<>(Math.max(16, attackerCount * 2));
        Map<Integer, Integer> defenderSlotByNationId = new HashMap<>(Math.max(16, defenderCount * 2));

        int totalCapacity = 0;
        int maxAttackerCapacity = 0;
        for (int attackerSlot = 0; attackerSlot < attackerCount; attackerSlot++) {
            int nationId = attackers.get(attackerSlot).nationId();
            attackerNationIds[attackerSlot] = nationId;
            attackerSlotByNationId.put(nationId, attackerSlot);
            int assignedCount = assignment.getOrDefault(nationId, List.of()).size();
            int cap = Math.max(attackerCapsByNationId.getOrDefault(nationId, 0), assignedCount);
            attackerCaps[attackerSlot] = cap;
            totalCapacity += cap;
            maxAttackerCapacity = Math.max(maxAttackerCapacity, cap);
        }
        for (int defenderSlot = 0; defenderSlot < defenderCount; defenderSlot++) {
            int nationId = defenders.get(defenderSlot).nationId();
            defenderNationIds[defenderSlot] = nationId;
            defenderSlotByNationId.put(nationId, defenderSlot);
            defenderCaps[defenderSlot] = Math.max(defenderCapsByNationId.getOrDefault(nationId, 0), 0);
        }

        int[] attackerOffsets = new int[attackerCount];
        int nextOffset = 0;
        for (int attackerSlot = 0; attackerSlot < attackerCount; attackerSlot++) {
            attackerOffsets[attackerSlot] = nextOffset;
            nextOffset += attackerCaps[attackerSlot];
        }

        int[] assignmentLengths = new int[attackerCount];
        int[] assignedDefenderSlots = new int[totalCapacity];
        boolean[] lockedAssignments = new boolean[totalCapacity];
        int[] defenderAssignedCount = new int[defenderCount];
        Map<Long, Integer> remainingLockedEdges = lockedEdgeCounts(lockedEdges);

        for (int attackerSlot = 0; attackerSlot < attackerCount; attackerSlot++) {
            List<Integer> defenderIds = assignment.get(attackerNationIds[attackerSlot]);
            if (defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            int offset = attackerOffsets[attackerSlot];
            int length = 0;
            for (int defenderId : defenderIds) {
                Integer defenderSlot = defenderSlotByNationId.get(defenderId);
                if (defenderSlot == null) {
                    continue;
                }
                assignedDefenderSlots[offset + length] = defenderSlot;
                long pairKey = pairKey(attackerNationIds[attackerSlot], defenderId);
                int lockedCount = remainingLockedEdges.getOrDefault(pairKey, 0);
                if (lockedCount > 0) {
                    lockedAssignments[offset + length] = true;
                    if (lockedCount == 1) {
                        remainingLockedEdges.remove(pairKey);
                    } else {
                        remainingLockedEdges.put(pairKey, lockedCount - 1);
                    }
                }
                length++;
                defenderAssignedCount[defenderSlot]++;
            }
            assignmentLengths[attackerSlot] = length;
        }

        for (int defenderSlot = 0; defenderSlot < defenderCount; defenderSlot++) {
            defenderCaps[defenderSlot] = Math.max(defenderCaps[defenderSlot], defenderAssignedCount[defenderSlot]);
        }

        return new PlannerAssignmentSession(
                attackerNationIds,
                defenderNationIds,
                attackerCaps,
                defenderCaps,
                attackerSlotByNationId,
                defenderSlotByNationId,
                attackerOffsets,
                assignmentLengths,
                assignedDefenderSlots,
                lockedAssignments,
                defenderAssignedCount,
                PlannerAssignmentChange.scratch(maxAttackerCapacity, maxAttackerCapacity)
        );
    }

    int attackerCount() {
        return attackerNationIds.length;
    }

    int defenderCount() {
        return defenderNationIds.length;
    }

    int attackerNationIdAt(int attackerSlot) {
        return attackerNationIds[attackerSlot];
    }

    int defenderNationIdAt(int defenderSlot) {
        return defenderNationIds[defenderSlot];
    }

    int defenderSlot(int nationId) {
        return defenderSlotByNationId.getOrDefault(nationId, -1);
    }

    int attackerSlot(int nationId) {
        return attackerSlotByNationId.getOrDefault(nationId, -1);
    }

    int attackerCap(int attackerSlot) {
        return attackerCaps[attackerSlot];
    }

    int defenderCap(int defenderSlot) {
        return defenderCaps[defenderSlot];
    }

    int assignedCount(int attackerSlot) {
        return assignmentLengths[attackerSlot];
    }

    boolean hasAssignments(int attackerSlot) {
        return assignmentLengths[attackerSlot] > 0;
    }

    int defenderSlotAt(int attackerSlot, int assignmentIndex) {
        return assignedDefenderSlots[attackerOffsets[attackerSlot] + assignmentIndex];
    }

    boolean isLocked(int attackerSlot, int assignmentIndex) {
        return lockedAssignments[attackerOffsets[attackerSlot] + assignmentIndex];
    }

    int defenderAssignedCount(int defenderSlot) {
        return defenderAssignedCount[defenderSlot];
    }

    boolean containsDefenderSlot(int attackerSlot, int defenderSlot) {
        return containsDefenderSlotExcept(attackerSlot, defenderSlot, -1);
    }

    boolean containsDefenderSlotExcept(int attackerSlot, int defenderSlot, int excludedIndex) {
        int length = assignmentLengths[attackerSlot];
        int offset = attackerOffsets[attackerSlot];
        for (int index = 0; index < length; index++) {
            if (index != excludedIndex && assignedDefenderSlots[offset + index] == defenderSlot) {
                return true;
            }
        }
        return false;
    }

    PlannerAssignmentChange swapChange(
            int attackerOneSlot,
            int attackerOneIndex,
            int newDefenderOneSlot,
            int attackerTwoSlot,
            int attackerTwoIndex,
            int newDefenderTwoSlot
    ) {
        changeScratch.setPair(attackerNationIds[attackerOneSlot], attackerNationIds[attackerTwoSlot]);
        appendReplacedDefenderIds(attackerOneSlot, attackerOneIndex, newDefenderOneSlot, true);
        appendReplacedDefenderIds(attackerTwoSlot, attackerTwoIndex, newDefenderTwoSlot, false);
        return changeScratch;
    }

    PlannerAssignmentChange moveChange(int attackerSlot, int assignedIndex, int newDefenderSlot) {
        changeScratch.setSingle(attackerNationIds[attackerSlot]);
        appendReplacedDefenderIds(attackerSlot, assignedIndex, newDefenderSlot, true);
        return changeScratch;
    }

    PlannerAssignmentChange addChange(int attackerSlot, int defenderSlot) {
        changeScratch.setSingle(attackerNationIds[attackerSlot]);
        appendDefenderIds(attackerSlot, true);
        changeScratch.addPrimaryDefender(defenderNationIds[defenderSlot]);
        return changeScratch;
    }

    PlannerAssignmentChange dropChange(int attackerSlot, int assignedIndex) {
        changeScratch.setSingle(attackerNationIds[attackerSlot]);
        int length = assignmentLengths[attackerSlot];
        int offset = attackerOffsets[attackerSlot];
        for (int index = 0; index < length; index++) {
            if (index != assignedIndex) {
                changeScratch.addPrimaryDefender(defenderNationIds[assignedDefenderSlots[offset + index]]);
            }
        }
        return changeScratch;
    }

    void applySwap(
            int attackerOneSlot,
            int attackerOneIndex,
            int newDefenderOneSlot,
            int attackerTwoSlot,
            int attackerTwoIndex,
            int newDefenderTwoSlot
    ) {
        assignedDefenderSlots[attackerOffsets[attackerOneSlot] + attackerOneIndex] = newDefenderOneSlot;
        assignedDefenderSlots[attackerOffsets[attackerTwoSlot] + attackerTwoIndex] = newDefenderTwoSlot;
    }

    void applyMove(int attackerSlot, int assignedIndex, int newDefenderSlot) {
        int position = attackerOffsets[attackerSlot] + assignedIndex;
        if (lockedAssignments[position]) {
            throw new IllegalStateException("locked assignment cannot be moved");
        }
        int previousDefenderSlot = assignedDefenderSlots[position];
        if (previousDefenderSlot == newDefenderSlot) {
            return;
        }
        assignedDefenderSlots[position] = newDefenderSlot;
        defenderAssignedCount[previousDefenderSlot]--;
        defenderAssignedCount[newDefenderSlot]++;
    }

    void applyAdd(int attackerSlot, int defenderSlot) {
        int length = assignmentLengths[attackerSlot];
        if (length >= attackerCaps[attackerSlot]) {
            throw new IllegalStateException("attacker slot capacity exceeded");
        }
        assignedDefenderSlots[attackerOffsets[attackerSlot] + length] = defenderSlot;
        lockedAssignments[attackerOffsets[attackerSlot] + length] = false;
        assignmentLengths[attackerSlot] = length + 1;
        defenderAssignedCount[defenderSlot]++;
    }

    void applyDrop(int attackerSlot, int assignedIndex) {
        int offset = attackerOffsets[attackerSlot];
        if (lockedAssignments[offset + assignedIndex]) {
            throw new IllegalStateException("locked assignment cannot be dropped");
        }
        int length = assignmentLengths[attackerSlot];
        int removedDefenderSlot = assignedDefenderSlots[offset + assignedIndex];
        for (int index = assignedIndex + 1; index < length; index++) {
            assignedDefenderSlots[offset + index - 1] = assignedDefenderSlots[offset + index];
            lockedAssignments[offset + index - 1] = lockedAssignments[offset + index];
        }
        lockedAssignments[offset + length - 1] = false;
        assignmentLengths[attackerSlot] = length - 1;
        defenderAssignedCount[removedDefenderSlot]--;
    }

    Map<Integer, List<Integer>> toAssignmentMap() {
        if (attackerNationIds.length == 0) {
            return Map.of();
        }
        LinkedHashMap<Integer, List<Integer>> assignment = new LinkedHashMap<>();
        for (int attackerSlot = 0; attackerSlot < attackerNationIds.length; attackerSlot++) {
            if (assignmentLengths[attackerSlot] == 0) {
                continue;
            }
            assignment.put(attackerNationIds[attackerSlot], defenderIdsForAttackerSlot(attackerSlot));
        }
        if (assignment.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(assignment);
    }

    private List<Integer> defenderIdsForAttackerSlot(int attackerSlot) {
        ArrayList<Integer> defenderIds = new ArrayList<>(assignmentLengths[attackerSlot]);
        appendDefenderIds(attackerSlot, defenderIds);
        return List.copyOf(defenderIds);
    }

    private void appendDefenderIds(int attackerSlot, List<Integer> out) {
        int length = assignmentLengths[attackerSlot];
        int offset = attackerOffsets[attackerSlot];
        for (int index = 0; index < length; index++) {
            out.add(defenderNationIds[assignedDefenderSlots[offset + index]]);
        }
    }

    private void appendDefenderIds(int attackerSlot, boolean primary) {
        int length = assignmentLengths[attackerSlot];
        int offset = attackerOffsets[attackerSlot];
        for (int index = 0; index < length; index++) {
            addChangeDefender(primary, defenderNationIds[assignedDefenderSlots[offset + index]]);
        }
    }

    private void appendReplacedDefenderIds(
            int attackerSlot,
            int replacedIndex,
            int newDefenderSlot,
            boolean primary
    ) {
        int length = assignmentLengths[attackerSlot];
        int offset = attackerOffsets[attackerSlot];
        for (int index = 0; index < length; index++) {
            int defenderSlot = index == replacedIndex ? newDefenderSlot : assignedDefenderSlots[offset + index];
            addChangeDefender(primary, defenderNationIds[defenderSlot]);
        }
    }

    private void addChangeDefender(boolean primary, int defenderId) {
        if (primary) {
            changeScratch.addPrimaryDefender(defenderId);
        } else {
            changeScratch.addSecondaryDefender(defenderId);
        }
    }

    private static Map<Long, Integer> lockedEdgeCounts(List<BlitzFixedEdge> lockedEdges) {
        Map<Long, Integer> counts = new HashMap<>(Math.max(16, lockedEdges.size() * 2));
        for (BlitzFixedEdge lockedEdge : lockedEdges) {
            long key = pairKey(lockedEdge.attackerNationId(), lockedEdge.defenderNationId());
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }
}

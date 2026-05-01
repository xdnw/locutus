package link.locutus.discord.sim.planners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Direct min-cost-flow solve for the long-horizon projection objective.
 *
 * <p>The objective is separable: each assigned edge contributes its base opening score,
 * each used attacker slot contributes an attacker-commitment marginal value, and each
 * used defender slot contributes a defender-pressure marginal value. Expanding attacker
 * and defender capacity into one node per residual slot lets the flow solve optimize those
 * marginal values in one pass instead of iterating through prior-round score patches.</p>
 */
final class LongHorizonMarginalFlowSolver {
    private static final double EPS1 = 1e-3;
    private static final double EPS2 = 1e-6;

    private LongHorizonMarginalFlowSolver() {
    }

    static Result solve(
            CandidateEdgeTable edges,
            LongHorizonMarginalScorer scorer,
            int attackerCount,
            int defenderCount,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges
    ) {
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        int[] attackerCounts = new int[attackerCount];
        int[] defenderCounts = new int[defenderCount];
        int[] residualAttackerCaps = Arrays.copyOf(attackerCaps, attackerCaps.length);
        int[] residualDefenderCaps = Arrays.copyOf(defenderCaps, defenderCaps.length);
        Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
        Map<Integer, Integer> attackerSlotByNationId = slotByNationId(attackerNationIds);
        Map<Integer, Integer> defenderSlotByNationId = slotByNationId(defenderNationIds);
        Map<Long, Integer> edgeIndexByPair = edgeIndexByPair(edges, attackerNationIds, defenderNationIds);
        Set<Long> fixedPairKeys = new HashSet<>(Math.max(16, fixedEdges.size() * 2));

        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            Integer attackerSlot = attackerSlotByNationId.get(fixedEdge.attackerNationId());
            Integer defenderSlot = defenderSlotByNationId.get(fixedEdge.defenderNationId());
            if (attackerSlot == null || defenderSlot == null) {
                continue;
            }
            assignment.computeIfAbsent(fixedEdge.attackerNationId(), ignored -> new ArrayList<>())
                    .add(fixedEdge.defenderNationId());
            residualAttackerCaps[attackerSlot] = Math.max(0, residualAttackerCaps[attackerSlot] - 1);
            residualDefenderCaps[defenderSlot] = Math.max(0, residualDefenderCaps[defenderSlot] - 1);
            attackerCounts[attackerSlot]++;
            defenderCounts[defenderSlot]++;
            long pairKey = pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId());
            fixedPairKeys.add(pairKey);
            Integer edgeIndex = edgeIndexByPair.get(pairKey);
            if (edgeIndex != null) {
                edgeAssigned[edgeIndex] = true;
            }
        }

        int[] attackerSlotOffsets = offsets(residualAttackerCaps);
        int[] defenderSlotOffsets = offsets(residualDefenderCaps);
        int attackerSlotCount = attackerSlotOffsets[attackerSlotOffsets.length - 1];
        int defenderSlotCount = defenderSlotOffsets[defenderSlotOffsets.length - 1];
        if (attackerSlotCount == 0 || defenderSlotCount == 0 || edges.edgeCount() == 0) {
            return new Result(assignment, edgeAssigned, attackerCounts, defenderCounts);
        }

        int source = 0;
        int attackerSlotStart = 1;
        int defenderSlotStart = attackerSlotStart + attackerSlotCount;
        int edgeInStart = defenderSlotStart + defenderSlotCount;
        int edgeOutStart = edgeInStart + edges.edgeCount();
        int sink = edgeOutStart + edges.edgeCount();
        int vertexCount = sink + 1;
        int edgePairCapacity = expandedEdgePairCapacity(edges, residualAttackerCaps, residualDefenderCaps, fixedPairKeys, attackerNationIds, defenderNationIds);
        int[] to = new int[edgePairCapacity * 2 + 4];
        int[] capacity = new int[to.length];
        double[] cost = new double[to.length];
        int[] next = new int[to.length];
        int[] head = new int[vertexCount];
        Arrays.fill(head, -1);
        int[] originalEdgeForwardSlot = new int[edges.edgeCount()];
        Arrays.fill(originalEdgeForwardSlot, -1);
        int pointer = 0;

        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            for (int slot = attackerSlotOffsets[attackerIndex]; slot < attackerSlotOffsets[attackerIndex + 1]; slot++) {
                int assignedBefore = attackerCounts[attackerIndex] + (slot - attackerSlotOffsets[attackerIndex]);
                double marginalScore = scorer.attackerCommitmentMarginalScore(attackerIndex, assignedBefore)
                    + scorer.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore);
                pointer = addEdgePair(to, capacity, cost, next, head, pointer, source, attackerSlotStart + slot, 1, -marginalScore);
            }
        }

        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            for (int slot = defenderSlotOffsets[defenderIndex]; slot < defenderSlotOffsets[defenderIndex + 1]; slot++) {
                int assignedBefore = defenderCounts[defenderIndex] + (slot - defenderSlotOffsets[defenderIndex]);
                double marginalScore = scorer.defenderPressureMarginalScore(defenderIndex, assignedBefore);
                pointer = addEdgePair(to, capacity, cost, next, head, pointer, defenderSlotStart + slot, sink, 1, -marginalScore);
            }
        }

        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            if (residualAttackerCaps[attackerIndex] <= 0 || residualDefenderCaps[defenderIndex] <= 0) {
                continue;
            }
            if (fixedPairKeys.contains(pairKey(attackerNationIds[attackerIndex], defenderNationIds[defenderIndex]))) {
                continue;
            }
            int rank = attackerStrengthRanks != null && attackerIndex < attackerStrengthRanks.length
                    ? attackerStrengthRanks[attackerIndex]
                    : 0;
                double edgeCost = -scorer.edgeScore(edgeIndex) + EPS1 * edges.counterRisk(edgeIndex) + EPS2 * rank;
            int edgeIn = edgeInStart + edgeIndex;
            int edgeOut = edgeOutStart + edgeIndex;
            originalEdgeForwardSlot[edgeIndex] = pointer;
            pointer = addEdgePair(to, capacity, cost, next, head, pointer, edgeIn, edgeOut, 1, edgeCost);
            for (int slot = attackerSlotOffsets[attackerIndex]; slot < attackerSlotOffsets[attackerIndex + 1]; slot++) {
                pointer = addEdgePair(to, capacity, cost, next, head, pointer, attackerSlotStart + slot, edgeIn, 1, 0d);
            }
            for (int slot = defenderSlotOffsets[defenderIndex]; slot < defenderSlotOffsets[defenderIndex + 1]; slot++) {
                pointer = addEdgePair(to, capacity, cost, next, head, pointer, edgeOut, defenderSlotStart + slot, 1, 0d);
            }
        }

        solveNegativePaths(to, capacity, cost, next, head, source, sink, vertexCount);

        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int forwardSlot = originalEdgeForwardSlot[edgeIndex];
            if (forwardSlot < 0 || capacity[forwardSlot] != 0) {
                continue;
            }
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            int attackerNationId = attackerNationIds[attackerIndex];
            int defenderNationId = defenderNationIds[defenderIndex];
            assignment.computeIfAbsent(attackerNationId, ignored -> new ArrayList<>()).add(defenderNationId);
            edgeAssigned[edgeIndex] = true;
            attackerCounts[attackerIndex]++;
            defenderCounts[defenderIndex]++;
        }

        return new Result(assignment, edgeAssigned, attackerCounts, defenderCounts);
    }

    private static void solveNegativePaths(
            int[] to,
            int[] capacity,
            double[] cost,
            int[] next,
            int[] head,
            int source,
            int sink,
            int vertexCount
    ) {
        double[] distance = new double[vertexCount];
        int[] previousEdge = new int[vertexCount];
        boolean[] inQueue = new boolean[vertexCount];
        ArrayDeque<Integer> queue = new ArrayDeque<>(vertexCount);
        while (true) {
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            Arrays.fill(previousEdge, -1);
            distance[source] = 0d;
            queue.clear();
            queue.add(source);
            inQueue[source] = true;
            while (!queue.isEmpty()) {
                int current = queue.poll();
                inQueue[current] = false;
                for (int edge = head[current]; edge != -1; edge = next[edge]) {
                    if (capacity[edge] <= 0) {
                        continue;
                    }
                    int nextVertex = to[edge];
                    double nextDistance = distance[current] + cost[edge];
                    if (nextDistance < distance[nextVertex] - 1e-12) {
                        distance[nextVertex] = nextDistance;
                        previousEdge[nextVertex] = edge;
                        if (!inQueue[nextVertex]) {
                            queue.add(nextVertex);
                            inQueue[nextVertex] = true;
                        }
                    }
                }
            }
            if (distance[sink] == Double.POSITIVE_INFINITY || distance[sink] >= -1e-12) {
                return;
            }
            int vertex = sink;
            while (vertex != source) {
                int edge = previousEdge[vertex];
                capacity[edge]--;
                capacity[edge ^ 1]++;
                vertex = to[edge ^ 1];
            }
        }
    }

    private static int expandedEdgePairCapacity(
            CandidateEdgeTable edges,
            int[] residualAttackerCaps,
            int[] residualDefenderCaps,
            Set<Long> fixedPairKeys,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        int edgePairs = 0;
        for (int cap : residualAttackerCaps) {
            edgePairs += cap;
        }
        for (int cap : residualDefenderCaps) {
            edgePairs += cap;
        }
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            if (residualAttackerCaps[attackerIndex] <= 0 || residualDefenderCaps[defenderIndex] <= 0) {
                continue;
            }
            if (fixedPairKeys.contains(pairKey(attackerNationIds[attackerIndex], defenderNationIds[defenderIndex]))) {
                continue;
            }
            edgePairs += 1 + residualAttackerCaps[attackerIndex] + residualDefenderCaps[defenderIndex];
        }
        return Math.max(1, edgePairs);
    }

    private static int[] offsets(int[] counts) {
        int[] offsets = new int[counts.length + 1];
        for (int index = 0; index < counts.length; index++) {
            offsets[index + 1] = offsets[index] + Math.max(0, counts[index]);
        }
        return offsets;
    }

    private static Map<Integer, Integer> slotByNationId(int[] nationIds) {
        Map<Integer, Integer> slots = new LinkedHashMap<>(Math.max(16, nationIds.length * 2));
        for (int index = 0; index < nationIds.length; index++) {
            slots.put(nationIds[index], index);
        }
        return slots;
    }

    private static Map<Long, Integer> edgeIndexByPair(CandidateEdgeTable edges, int[] attackerNationIds, int[] defenderNationIds) {
        Map<Long, Integer> indexes = new HashMap<>(Math.max(16, edges.edgeCount() * 2));
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            indexes.put(pairKey(attackerNationIds[edges.attackerIndex(edgeIndex)], defenderNationIds[edges.defenderIndex(edgeIndex)]), edgeIndex);
        }
        return indexes;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }

    private static int addEdgePair(
            int[] to,
            int[] capacity,
            double[] cost,
            int[] next,
            int[] head,
            int pointer,
            int from,
            int target,
            int edgeCapacity,
            double edgeCost
    ) {
        to[pointer] = target;
        capacity[pointer] = edgeCapacity;
        cost[pointer] = edgeCost;
        next[pointer] = head[from];
        head[from] = pointer++;
        to[pointer] = from;
        capacity[pointer] = 0;
        cost[pointer] = -edgeCost;
        next[pointer] = head[target];
        head[target] = pointer++;
        return pointer;
    }

    record Result(
            Map<Integer, List<Integer>> assignment,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
    }
}
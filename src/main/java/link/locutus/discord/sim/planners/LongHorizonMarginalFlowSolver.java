package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        return solve(
                edges,
                scorer,
                attackerCount,
                defenderCount,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                staticSolveInputs(attackerNationIds, defenderNationIds, fixedEdges),
                new GraphBuildBuffers()
        );
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
            List<BlitzFixedEdge> fixedEdges,
            StaticSolveInputs staticSolveInputs
    ) {
        return solve(
                edges,
                scorer,
                attackerCount,
                defenderCount,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                staticSolveInputs,
                new GraphBuildBuffers()
        );
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
            List<BlitzFixedEdge> fixedEdges,
            StaticSolveInputs staticSolveInputs,
            GraphBuildBuffers graphBuildBuffers
    ) {
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        int[] attackerCounts = new int[attackerCount];
        int[] defenderCounts = new int[defenderCount];
        int[] residualAttackerCaps = Arrays.copyOf(attackerCaps, attackerCaps.length);
        int[] residualDefenderCaps = Arrays.copyOf(defenderCaps, defenderCaps.length);
        Map<Integer, List<Integer>> assignment = new Int2ObjectLinkedOpenHashMap<>();
        Int2IntOpenHashMap attackerSlotByNationId = staticSolveInputs.attackerSlotByNationId();
        Int2IntOpenHashMap defenderSlotByNationId = staticSolveInputs.defenderSlotByNationId();
        long[] edgePairKeys = edges.edgePairKeys(attackerNationIds, defenderNationIds);
        Long2IntOpenHashMap edgeIndexByPair = edges.edgeIndexByPair(attackerNationIds, defenderNationIds);
        LongOpenHashSet fixedPairKeys = staticSolveInputs.fixedPairKeys();

        if (edgePairKeys.length != edges.edgeCount()) {
            throw new IllegalArgumentException("Prepared marginal-flow topology does not match edge table");
        }

        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            int attackerSlot = attackerSlotByNationId.get(fixedEdge.attackerNationId());
            int defenderSlot = defenderSlotByNationId.get(fixedEdge.defenderNationId());
            if (attackerSlot < 0 || defenderSlot < 0) {
                continue;
            }
            assignment.computeIfAbsent(fixedEdge.attackerNationId(), ignored -> new IntArrayList())
                    .add(fixedEdge.defenderNationId());
            residualAttackerCaps[attackerSlot] = Math.max(0, residualAttackerCaps[attackerSlot] - 1);
            residualDefenderCaps[defenderSlot] = Math.max(0, residualDefenderCaps[defenderSlot] - 1);
            attackerCounts[attackerSlot]++;
            defenderCounts[defenderSlot]++;
            long pairKey = pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId());
            fixedPairKeys.add(pairKey);
            int edgeIndex = edgeIndexByPair.get(pairKey);
            if (edgeIndex >= 0) {
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
        int edgePairCapacity = expandedEdgePairCapacity(edges, residualAttackerCaps, residualDefenderCaps, fixedPairKeys, edgePairKeys);
        graphBuildBuffers.prepare(vertexCount, edgePairCapacity * 2 + 4, edges.edgeCount());
        int[] to = graphBuildBuffers.to();
        int[] capacity = graphBuildBuffers.capacity();
        double[] cost = graphBuildBuffers.cost();
        int[] next = graphBuildBuffers.next();
        int[] head = graphBuildBuffers.head();
        int[] originalEdgeForwardSlot = graphBuildBuffers.originalEdgeForwardSlot();
        int pointer = 0;

        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            for (int slot = attackerSlotOffsets[attackerIndex]; slot < attackerSlotOffsets[attackerIndex + 1]; slot++) {
                int assignedBefore = attackerCounts[attackerIndex] + (slot - attackerSlotOffsets[attackerIndex]);
                double marginalScore = scorer.attackerCommitmentMarginalScore(attackerIndex, assignedBefore)
                    + scorer.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore);
                if (assignedBefore == 0) {
                    marginalScore += scorer.attackerIdlePressureMarginalScore(attackerIndex);
                }
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
            if (fixedPairKeys.contains(edgePairKeys[edgeIndex])) {
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

        solveNegativePaths(to, capacity, cost, next, head, source, sink, vertexCount, graphBuildBuffers.shortestPathScratch());

        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int forwardSlot = originalEdgeForwardSlot[edgeIndex];
            if (forwardSlot < 0 || capacity[forwardSlot] != 0) {
                continue;
            }
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            int attackerNationId = attackerNationIds[attackerIndex];
            int defenderNationId = defenderNationIds[defenderIndex];
            assignment.computeIfAbsent(attackerNationId, ignored -> new IntArrayList()).add(defenderNationId);
            edgeAssigned[edgeIndex] = true;
            attackerCounts[attackerIndex]++;
            defenderCounts[defenderIndex]++;
        }

        return new Result(assignment, edgeAssigned, attackerCounts, defenderCounts);
    }

    static StaticSolveInputs staticSolveInputs(
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges
    ) {
        return new StaticSolveInputs(
                slotByNationId(attackerNationIds),
                slotByNationId(defenderNationIds),
                fixedPairKeys(fixedEdges)
        );
    }

    private static void solveNegativePaths(
            int[] to,
            int[] capacity,
            double[] cost,
            int[] next,
            int[] head,
            int source,
            int sink,
            int vertexCount,
            ShortestPathScratch scratch
    ) {
        double[] potential = initialPotentials(to, capacity, cost, next, head, source, vertexCount, scratch);
        DistanceHeap queue = scratch.distanceHeap();
        while (true) {
            int searchVersion = scratch.beginSearch();
            scratch.setReducedDistance(source, searchVersion, 0d);
            queue.add(source, 0d);
            while (!queue.isEmpty()) {
                int current = queue.removeMinVertex();
                double currentDistance = queue.lastDistance();
                double bestCurrentDistance = scratch.reducedDistance(current, searchVersion);
                if (currentDistance > bestCurrentDistance + 1e-12) {
                    continue;
                }
                for (int edge = head[current]; edge != -1; edge = next[edge]) {
                    if (capacity[edge] <= 0) {
                        continue;
                    }
                    int nextVertex = to[edge];
                    double reducedCost = cost[edge] + potential[current] - potential[nextVertex];
                    if (reducedCost < 0d && reducedCost > -1e-9) {
                        reducedCost = 0d;
                    }
                    double nextDistance = bestCurrentDistance + reducedCost;
                    if (nextDistance < scratch.reducedDistance(nextVertex, searchVersion) - 1e-12) {
                        scratch.setReducedDistance(nextVertex, searchVersion, nextDistance);
                        scratch.setPreviousEdge(nextVertex, edge);
                        queue.add(nextVertex, nextDistance);
                    }
                }
            }
            if (!scratch.wasReached(sink, searchVersion) || scratch.previousEdge(sink) < 0) {
                return;
            }
            int[] previousEdge = scratch.previousEdgeArray();
            double originalPathCost = pathCost(previousEdge, to, cost, source, sink);
            if (originalPathCost >= -1e-12) {
                return;
            }
            for (int index = 0; index < scratch.touchedVertexCount(); index++) {
                int vertex = scratch.touchedVertex(index);
                potential[vertex] += scratch.reducedDistance(vertex, searchVersion);
            }
            int vertex = sink;
            int amount = Integer.MAX_VALUE;
            while (vertex != source) {
                int edge = previousEdge[vertex];
                amount = Math.min(amount, capacity[edge]);
                vertex = to[edge ^ 1];
            }
            vertex = sink;
            while (vertex != source) {
                int edge = previousEdge[vertex];
                capacity[edge] -= amount;
                capacity[edge ^ 1] += amount;
                vertex = to[edge ^ 1];
            }
        }
    }

    private static double[] initialPotentials(
            int[] to,
            int[] capacity,
            double[] cost,
            int[] next,
            int[] head,
            int source,
            int vertexCount,
            ShortestPathScratch scratch
    ) {
        double[] distance = scratch.initialDistance();
        boolean[] inQueue = scratch.initialInQueue();
        IntArrayFIFOQueue queue = scratch.initialQueue();
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(inQueue, false);
        queue.clear();
        distance[source] = 0d;
        queue.enqueue(source);
        inQueue[source] = true;
        while (!queue.isEmpty()) {
            int current = queue.dequeueInt();
            inQueue[current] = false;
            for (int edge = head[current]; edge != -1; edge = next[edge]) {
                if (capacity[edge] <= 0) {
                    continue;
                }
                int nextVertex = to[edge];
                double nextDistance = distance[current] + cost[edge];
                if (nextDistance < distance[nextVertex] - 1e-12) {
                    distance[nextVertex] = nextDistance;
                    if (!inQueue[nextVertex]) {
                        queue.enqueue(nextVertex);
                        inQueue[nextVertex] = true;
                    }
                }
            }
        }
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (distance[vertex] == Double.POSITIVE_INFINITY) {
                distance[vertex] = 0d;
            }
        }
        return distance;
    }

    private static double pathCost(
            int[] previousEdge,
            int[] to,
            double[] cost,
            int source,
            int sink
    ) {
        double total = 0d;
        int vertex = sink;
        while (vertex != source) {
            int edge = previousEdge[vertex];
            total += cost[edge];
            vertex = to[edge ^ 1];
        }
        return total;
    }

    private static final class DistanceHeap {
        private int[] vertices;
        private double[] distances;
        private int size;
        private double lastDistance;

        private DistanceHeap(int initialCapacity) {
            int capacity = Math.max(16, initialCapacity);
            vertices = new int[capacity];
            distances = new double[capacity];
        }

        private void clear() {
            size = 0;
            lastDistance = 0d;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void add(int vertex, double distance) {
            ensureCapacity(size + 1);
            vertices[size] = vertex;
            distances[size] = distance;
            siftUp(size++);
        }

        private int removeMinVertex() {
            int result = vertices[0];
            lastDistance = distances[0];
            int lastIndex = --size;
            if (lastIndex > 0) {
                vertices[0] = vertices[lastIndex];
                distances[0] = distances[lastIndex];
                siftDown(0);
            }
            return result;
        }

        private double lastDistance() {
            return lastDistance;
        }

        private void ensureCapacity(int required) {
            if (required <= vertices.length) {
                return;
            }
            int nextCapacity = Math.max(required, vertices.length * 2);
            vertices = Arrays.copyOf(vertices, nextCapacity);
            distances = Arrays.copyOf(distances, nextCapacity);
        }

        private void siftUp(int index) {
            int child = index;
            while (child > 0) {
                int parent = (child - 1) >>> 1;
                if (distances[parent] <= distances[child]) {
                    return;
                }
                swap(parent, child);
                child = parent;
            }
        }

        private void siftDown(int index) {
            int parent = index;
            while (true) {
                int left = (parent << 1) + 1;
                if (left >= size) {
                    return;
                }
                int smallest = left;
                int right = left + 1;
                if (right < size && distances[right] < distances[left]) {
                    smallest = right;
                }
                if (distances[parent] <= distances[smallest]) {
                    return;
                }
                swap(parent, smallest);
                parent = smallest;
            }
        }

        private void swap(int left, int right) {
            int vertexSwap = vertices[left];
            vertices[left] = vertices[right];
            vertices[right] = vertexSwap;
            double distanceSwap = distances[left];
            distances[left] = distances[right];
            distances[right] = distanceSwap;
        }
    }

    private static int expandedEdgePairCapacity(
            CandidateEdgeTable edges,
            int[] residualAttackerCaps,
            int[] residualDefenderCaps,
            LongOpenHashSet fixedPairKeys,
            long[] edgePairKeys
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
            if (fixedPairKeys.contains(edgePairKeys[edgeIndex])) {
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

    private static Int2IntOpenHashMap slotByNationId(int[] nationIds) {
        Int2IntOpenHashMap slots = new Int2IntOpenHashMap(Math.max(16, nationIds.length * 2));
        slots.defaultReturnValue(-1);
        for (int index = 0; index < nationIds.length; index++) {
            slots.put(nationIds[index], index);
        }
        return slots;
    }

    private static LongOpenHashSet fixedPairKeys(List<BlitzFixedEdge> fixedEdges) {
        LongOpenHashSet fixedPairKeys = new LongOpenHashSet(Math.max(16, fixedEdges.size() * 2));
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            fixedPairKeys.add(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
        }
        return fixedPairKeys;
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

    static final class GraphBuildBuffers {
        private int[] to = new int[0];
        private int[] capacity = new int[0];
        private double[] cost = new double[0];
        private int[] next = new int[0];
        private int[] head = new int[0];
        private int[] originalEdgeForwardSlot = new int[0];
        private final ShortestPathScratch shortestPathScratch = new ShortestPathScratch();

        void prepare(int vertexCount, int edgeArrayLength, int edgeCount) {
            if (to.length < edgeArrayLength) {
                to = new int[edgeArrayLength];
                capacity = new int[edgeArrayLength];
                cost = new double[edgeArrayLength];
                next = new int[edgeArrayLength];
            }
            if (head.length < vertexCount) {
                head = new int[vertexCount];
            }
            if (originalEdgeForwardSlot.length < edgeCount) {
                originalEdgeForwardSlot = new int[edgeCount];
            }
            Arrays.fill(head, 0, vertexCount, -1);
            Arrays.fill(originalEdgeForwardSlot, 0, edgeCount, -1);
            shortestPathScratch.prepare(vertexCount);
        }

        int[] to() {
            return to;
        }

        int[] capacity() {
            return capacity;
        }

        double[] cost() {
            return cost;
        }

        int[] next() {
            return next;
        }

        int[] head() {
            return head;
        }

        int[] originalEdgeForwardSlot() {
            return originalEdgeForwardSlot;
        }

        ShortestPathScratch shortestPathScratch() {
            return shortestPathScratch;
        }
    }

    private static final class ShortestPathScratch {
        private double[] reducedDistance = new double[0];
        private int[] reducedDistanceVersion = new int[0];
        private int[] previousEdge = new int[0];
        private int[] touchedVertices = new int[0];
        private double[] initialDistance = new double[0];
        private boolean[] initialInQueue = new boolean[0];
        private IntArrayFIFOQueue initialQueue = new IntArrayFIFOQueue();
        private DistanceHeap distanceHeap = new DistanceHeap(16);
        private int searchVersion;
        private int touchedVertexCount;

        void prepare(int vertexCount) {
            if (reducedDistance.length < vertexCount) {
                reducedDistance = new double[vertexCount];
                reducedDistanceVersion = new int[vertexCount];
                previousEdge = new int[vertexCount];
                touchedVertices = new int[vertexCount];
                initialDistance = new double[vertexCount];
                initialInQueue = new boolean[vertexCount];
                initialQueue = new IntArrayFIFOQueue(vertexCount);
                distanceHeap = new DistanceHeap(vertexCount);
                searchVersion = 0;
            }
        }

        int beginSearch() {
            searchVersion++;
            if (searchVersion == 0) {
                Arrays.fill(reducedDistanceVersion, 0);
                searchVersion = 1;
            }
            touchedVertexCount = 0;
            distanceHeap.clear();
            return searchVersion;
        }

        double reducedDistance(int vertex, int version) {
            return reducedDistanceVersion[vertex] == version
                    ? reducedDistance[vertex]
                    : Double.POSITIVE_INFINITY;
        }

        void setReducedDistance(int vertex, int version, double value) {
            if (reducedDistanceVersion[vertex] != version) {
                reducedDistanceVersion[vertex] = version;
                touchedVertices[touchedVertexCount++] = vertex;
            }
            reducedDistance[vertex] = value;
        }

        boolean wasReached(int vertex, int version) {
            return reducedDistanceVersion[vertex] == version;
        }

        void setPreviousEdge(int vertex, int edge) {
            previousEdge[vertex] = edge;
        }

        int previousEdge(int vertex) {
            return previousEdge[vertex];
        }

        int[] previousEdgeArray() {
            return previousEdge;
        }

        int touchedVertexCount() {
            return touchedVertexCount;
        }

        int touchedVertex(int index) {
            return touchedVertices[index];
        }

        double[] initialDistance() {
            return initialDistance;
        }

        boolean[] initialInQueue() {
            return initialInQueue;
        }

        IntArrayFIFOQueue initialQueue() {
            return initialQueue;
        }

        DistanceHeap distanceHeap() {
            return distanceHeap;
        }
    }

    record StaticSolveInputs(
            Int2IntOpenHashMap attackerSlotByNationId,
            Int2IntOpenHashMap defenderSlotByNationId,
            LongOpenHashSet fixedPairKeys
    ) {
    }

}

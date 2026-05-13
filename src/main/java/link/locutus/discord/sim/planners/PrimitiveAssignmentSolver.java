package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Primitive-array capacity-constrained bipartite assignment solver.
 *
 * <p>Replaces JGraphT {@code CapacityScalingMinimumCostFlow} as the default solver path.
 * Operates entirely on primitive int/double arrays; no object allocation in the inner loop.
 *
 * <p><b>Problem form:</b>
 * <ul>
 *   <li>Attacker supply = {@code supply[ai]} free offensive slots per scenario attacker index.</li>
 *   <li>Defender capacity = {@code capacity[di]} free defensive slots per scenario defender index.</li>
 *   <li>Each candidate edge has capacity 1 and cost = {@code -scalarScore + eps1*counterRisk + eps2*attRank}.</li>
 *   <li>Unused supply is optional flow: the solver simply stops once no negative-cost
 *       augmenting path remains.</li>
 * </ul>
 *
 * <p><b>Algorithm:</b> successive shortest paths with Johnson potentials and a primitive
 * Dijkstra heap. Negative candidate-edge costs are supported through an initial feasible
 * potential on the layered source/attacker/defender/sink graph, and each augmentation sends
 * 1 unit of flow along the cheapest simple source-to-sink path. Stops once no beneficial
 * path remains (actual path cost ≥ 0).
 *
 * <p><b>Complexity:</b> O(supply_total × (E log V)) where V = nAtt + nDef + 2 and E = edge count.
 */
final class PrimitiveAssignmentSolver {

    /** Tie-breaker weight for counter-risk in edge cost. Primary score dominates. */
    private static final double EPS1 = 1e-3;

    /** Tie-breaker weight for attacker strength rank in edge cost. */
    private static final double EPS2 = 1e-6;

    private static final double COST_EPS = 1e-12;

    private static final double REDUCED_COST_EPS = 1e-9;

    private PrimitiveAssignmentSolver() {
    }

    // ---- Public entry point ------------------------------------------------

    /**
     * Solves the bipartite assignment and returns a map from attacker nation ID to the list
     * of assigned defender nation IDs.
     *
     * @param edges             candidate edge table (scenario indexes, scores, counter-risks)
     * @param nAtt              total attacker count in the scenario
     * @param nDef              total defender count in the scenario
     * @param supply            free offensive slots per attacker (scenario index)
     * @param capacity          free defensive slots per defender (scenario index)
     * @param attStrengthRank   strength rank per attacker (0 = strongest); may be null
     * @param attackerNationIds scenario attacker index to nation ID
     * @param defenderNationIds scenario defender index to nation ID
     * @return assignment map from attacker nation ID to list of defender nation IDs
     */
    static Map<Integer, List<Integer>> solveAssignment(
            CandidateEdgeTable edges,
            int nAtt,
            int nDef,
            int[] supply,
            int[] capacity,
            int[] attStrengthRank,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        return solveAssignment(edges, nAtt, nDef, supply, capacity, attStrengthRank, attackerNationIds, defenderNationIds, List.of());
    }

    static Map<Integer, List<Integer>> solveAssignment(
            CandidateEdgeTable edges,
            int nAtt,
            int nDef,
            int[] supply,
            int[] capacity,
            int[] attStrengthRank,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges
    ) {
        return solveAssignment(edges, null, nAtt, nDef, supply, capacity, attStrengthRank, attackerNationIds, defenderNationIds, fixedEdges, null, null, null);
    }

    /**
     * Dense overload: optional {@code scoreOverride} replaces edge-table scores for the cost
     * function (length must equal {@code edges.edgeCount()}); optional output buffers receive
     * which edges carried flow plus per-side assignment counts. Buffers, if non-null, must be
     * sized to {@code edges.edgeCount()}, {@code nAtt}, {@code nDef}. They are reset before use.
     */
    static Map<Integer, List<Integer>> solveAssignment(
            CandidateEdgeTable edges,
            float[] scoreOverride,
            int nAtt,
            int nDef,
            int[] supply,
            int[] capacity,
            int[] attStrengthRank,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            boolean[] edgeAssignedOut,
            int[] attackerAssignedCountsOut,
            int[] defenderAssignedCountsOut
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE)) {
            PlannerProfiler.addCounter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE, "edges", edges.edgeCount());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE, "attackers", nAtt);
            PlannerProfiler.addCounter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE, "defenders", nDef);
            PlannerProfiler.addCounter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE, "fixedEdges", fixedEdges.size());
            if (edgeAssignedOut != null) Arrays.fill(edgeAssignedOut, false);
            if (attackerAssignedCountsOut != null) Arrays.fill(attackerAssignedCountsOut, 0);
            if (defenderAssignedCountsOut != null) Arrays.fill(defenderAssignedCountsOut, 0);

            int[] residualSupply = Arrays.copyOf(supply, supply.length);
            int[] residualCapacity = Arrays.copyOf(capacity, capacity.length);
            Int2IntOpenHashMap attackerSlotByNationId = slotByNationId(attackerNationIds);
            Int2IntOpenHashMap defenderSlotByNationId = slotByNationId(defenderNationIds);
            Map<Integer, List<Integer>> fixedAssignment = new Int2ObjectLinkedOpenHashMap<>();
            for (BlitzFixedEdge fixedEdge : fixedEdges) {
                int attackerSlot = attackerSlotByNationId.get(fixedEdge.attackerNationId());
                int defenderSlot = defenderSlotByNationId.get(fixedEdge.defenderNationId());
                if (attackerSlot < 0 || defenderSlot < 0) {
                    continue;
                }
                fixedAssignment.computeIfAbsent(fixedEdge.attackerNationId(), unused -> new IntArrayList())
                        .add(fixedEdge.defenderNationId());
                residualSupply[attackerSlot] = Math.max(0, residualSupply[attackerSlot] - 1);
                residualCapacity[defenderSlot] = Math.max(0, residualCapacity[defenderSlot] - 1);
                if (attackerAssignedCountsOut != null) attackerAssignedCountsOut[attackerSlot]++;
                if (defenderAssignedCountsOut != null) defenderAssignedCountsOut[defenderSlot]++;
            }

            Map<Integer, List<Integer>> residualAssignment = solveResidualAssignment(
                    edges,
                    scoreOverride,
                    nAtt,
                    nDef,
                    residualSupply,
                    residualCapacity,
                    attStrengthRank,
                    attackerNationIds,
                    defenderNationIds,
                    fixedPairKeys(fixedEdges),
                    edgeAssignedOut,
                    attackerAssignedCountsOut,
                    defenderAssignedCountsOut
            );
            Map<Integer, List<Integer>> result;
            if (fixedAssignment.isEmpty()) {
                result = residualAssignment;
            } else if (residualAssignment.isEmpty()) {
                result = fixedAssignment;
            } else {
                Map<Integer, List<Integer>> merged = new Int2ObjectLinkedOpenHashMap<>(fixedAssignment);
                for (Map.Entry<Integer, List<Integer>> entry : residualAssignment.entrySet()) {
                    merged.computeIfAbsent(entry.getKey(), unused -> new IntArrayList()).addAll(entry.getValue());
                }
                result = merged;
            }
            PlannerProfiler.addCounter(PlannerProfiler.Scope.PRIMITIVE_ASSIGNMENT_SOLVE, "assignmentPairs", assignmentPairCount(result));
            return result;
        }
    }

    private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
        int pairCount = 0;
        for (List<Integer> defenders : assignment.values()) {
            pairCount += defenders.size();
        }
        return pairCount;
    }

    private static Map<Integer, List<Integer>> solveResidualAssignment(
            CandidateEdgeTable edges,
            float[] scoreOverride,
            int nAtt,
            int nDef,
            int[] supply,
            int[] capacity,
            int[] attStrengthRank,
            int[] attackerNationIds,
            int[] defenderNationIds,
            LongOpenHashSet excludedPairKeys,
            boolean[] edgeAssignedOut,
            int[] attackerAssignedCountsOut,
            int[] defenderAssignedCountsOut
    ) {
        int totalSupply = 0;
        for (int ai = 0; ai < nAtt; ai++) {
            totalSupply += supply[ai];
        }
        if (totalSupply == 0 || edges.edgeCount() == 0) {
            return Map.of();
        }

        final int SOURCE = 0;
        final int SINK = nAtt + nDef + 1;
        final int nV = nAtt + nDef + 2;

        int arraySize = (nAtt + nDef + edges.edgeCount()) * 2 + 4;
        int[] eTo = new int[arraySize];
        int[] eCap = new int[arraySize];
        double[] eCost = new double[arraySize];
        int[] eNext = new int[arraySize];
        int[] head = new int[nV];
        Arrays.fill(head, -1);

        int[] candidateFwdSlot = new int[edges.edgeCount()];
        Arrays.fill(candidateFwdSlot, -1);

        double[] potential = new double[nV];
        Arrays.fill(potential, Double.POSITIVE_INFINITY);
        potential[SOURCE] = 0.0;

        int ptr = 0;

        for (int ai = 0; ai < nAtt; ai++) {
            if (supply[ai] > 0) {
                ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, SOURCE, ai + 1, supply[ai], 0.0);
                potential[ai + 1] = 0.0;
            }
        }

        for (int di = 0; di < nDef; di++) {
            if (capacity[di] > 0) {
                ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, nAtt + di + 1, SINK, capacity[di], 0.0);
            }
        }

        int eligibleCandidateCount = 0;
        for (int e = 0; e < edges.edgeCount(); e++) {
            int ai = edges.attackerIndex(e);
            int di = edges.defenderIndex(e);
            if (supply[ai] <= 0 || capacity[di] <= 0) {
                continue;
            }
            if (excludedPairKeys.contains(pairKey(attackerNationIds[ai], defenderNationIds[di]))) {
                continue;
            }
            int rank = (attStrengthRank != null && ai < attStrengthRank.length) ? attStrengthRank[ai] : 0;
            double edgeCost = (scoreOverride == null)
                    ? edges.edgeCost(e, EPS1, EPS2, rank)
                    : (-scoreOverride[e] + EPS1 * edges.counterRisk(e) + EPS2 * rank);

            if (!Double.isFinite(edgeCost)) {
                throw new IllegalArgumentException("Non-finite edge cost for candidate edge " + e + ": " + edgeCost);
            }
            if (edgeCost >= -COST_EPS) {
                continue;
            }

            int attackerVertex = ai + 1;
            int defenderVertex = nAtt + di + 1;
            candidateFwdSlot[e] = ptr;
            ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, attackerVertex, defenderVertex, 1, edgeCost);
            if (edgeCost < potential[defenderVertex]) {
                potential[defenderVertex] = edgeCost;
            }
            eligibleCandidateCount++;
        }

        if (eligibleCandidateCount == 0) {
            return Map.of();
        }

        double sinkPotential = Double.POSITIVE_INFINITY;
        for (int di = 0; di < nDef; di++) {
            int defenderVertex = nAtt + di + 1;
            if (capacity[di] > 0 && potential[defenderVertex] < sinkPotential) {
                sinkPotential = potential[defenderVertex];
            }
        }
        if (!Double.isFinite(sinkPotential)) {
            return Map.of();
        }
        potential[SINK] = sinkPotential;
        for (int v = 0; v < nV; v++) {
            if (!Double.isFinite(potential[v])) {
                potential[v] = 0.0;
            }
        }

        double[] dist = new double[nV];
        int[] prevEdge = new int[nV];
        IntDoubleMinHeap heap = new IntDoubleMinHeap(Math.max(16, ptr + nV + 8));

        while (true) {
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(prevEdge, -1);
            dist[SOURCE] = 0.0;

            heap.clear();
            heap.push(SOURCE, 0.0);

            while (!heap.isEmpty()) {
                int u = heap.popVertex();
                double du = heap.poppedKey();
                if (du > dist[u] + COST_EPS) {
                    continue;
                }
                if (u == SINK) {
                    continue;
                }
                for (int eid = head[u]; eid != -1; eid = eNext[eid]) {
                    if (eCap[eid] <= 0) {
                        continue;
                    }
                    int v = eTo[eid];
                    double reducedCost = eCost[eid] + potential[u] - potential[v];
                    if (reducedCost < 0.0) {
                        if (reducedCost > -REDUCED_COST_EPS) {
                            reducedCost = 0.0;
                        } else {
                            throw new IllegalStateException(
                                    "Negative reduced cost invariant violated: eid=" + eid
                                            + ", from=" + u
                                            + ", to=" + v
                                            + ", cost=" + eCost[eid]
                                            + ", reducedCost=" + reducedCost
                            );
                        }
                    }
                    double nd = du + reducedCost;
                    if (nd < dist[v] - COST_EPS) {
                        dist[v] = nd;
                        prevEdge[v] = eid;
                        heap.push(v, nd);
                    }
                }
            }

            if (!Double.isFinite(dist[SINK])) {
                break;
            }

            double actualPathCost = dist[SINK] + potential[SINK] - potential[SOURCE];
            if (actualPathCost >= -COST_EPS) {
                break;
            }

            for (int v = 0; v < nV; v++) {
                if (Double.isFinite(dist[v])) {
                    potential[v] += dist[v];
                }
            }

            int v = SINK;
            int hops = 0;
            while (v != SOURCE) {
                if (++hops > nV) {
                    throw new IllegalStateException("Cyclic prevEdge chain while augmenting; residual path is invalid");
                }
                int eid = prevEdge[v];
                if (eid < 0) {
                    throw new IllegalStateException("Missing prevEdge for vertex " + v + " while augmenting");
                }
                int rev = eid ^ 1;
                if (rev < 0 || rev >= ptr) {
                    throw new IllegalStateException("Invalid reverse edge slot: eid=" + eid + ", rev=" + rev);
                }
                if (eTo[eid] != v) {
                    throw new IllegalStateException(
                            "prevEdge does not point into current vertex: vertex=" + v
                                    + ", eid=" + eid
                                    + ", eTo[eid]=" + eTo[eid]
                    );
                }
                if (eCap[eid] <= 0) {
                    throw new IllegalStateException("Augmenting through non-positive-capacity edge: eid=" + eid);
                }
                eCap[eid]--;
                eCap[rev]++;
                v = eTo[rev];
            }
        }

        Map<Integer, List<Integer>> assignment = new Int2ObjectLinkedOpenHashMap<>();
        for (int e = 0; e < edges.edgeCount(); e++) {
            int fwdSlot = candidateFwdSlot[e];
            if (fwdSlot < 0) continue;
            if (eCap[fwdSlot] == 0) {
                int ai = edges.attackerIndex(e);
                int di = edges.defenderIndex(e);
                int attNationId = attackerNationIds[ai];
                int defNationId = defenderNationIds[di];
                assignment.computeIfAbsent(attNationId, k -> new IntArrayList()).add(defNationId);
                if (edgeAssignedOut != null) edgeAssignedOut[e] = true;
                if (attackerAssignedCountsOut != null) attackerAssignedCountsOut[ai]++;
                if (defenderAssignedCountsOut != null) defenderAssignedCountsOut[di]++;
            }
        }
        return assignment;
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
        LongOpenHashSet keys = new LongOpenHashSet(Math.max(16, fixedEdges.size() * 2));
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            keys.add(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
        }
        return keys;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }

    // ---- Graph helper ------------------------------------------------------

    /**
     * Adds forward edge u→v and residual v→u.
     * Forward edge is at {@code ptr}; residual at {@code ptr+1}. Returns ptr + 2.
     *
     * <p>Consecutive storage means {@code eid ^ 1} always gives the paired edge.
     */
    private static int addEdgePair(
            int[] eTo, int[] eCap, double[] eCost, int[] eNext, int[] head,
            int ptr, int u, int v, int edgeCap, double edgeCost
    ) {
        eTo[ptr] = v; eCap[ptr] = edgeCap; eCost[ptr] = edgeCost; eNext[ptr] = head[u]; head[u] = ptr++;
        eTo[ptr] = u; eCap[ptr] = 0;       eCost[ptr] = -edgeCost; eNext[ptr] = head[v]; head[v] = ptr++;
        return ptr;
    }

    private static final class IntDoubleMinHeap {
        private int[] vertices;
        private double[] keys;
        private int size;
        private double poppedKey;

        private IntDoubleMinHeap(int initialCapacity) {
            this.vertices = new int[Math.max(4, initialCapacity)];
            this.keys = new double[this.vertices.length];
        }

        void clear() {
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        double poppedKey() {
            return poppedKey;
        }

        void push(int vertex, double key) {
            if (size == vertices.length) {
                int newLength = vertices.length << 1;
                vertices = Arrays.copyOf(vertices, newLength);
                keys = Arrays.copyOf(keys, newLength);
            }

            int index = size++;
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                if (lessOrEqual(keys[parent], vertices[parent], key, vertex)) {
                    break;
                }
                keys[index] = keys[parent];
                vertices[index] = vertices[parent];
                index = parent;
            }

            keys[index] = key;
            vertices[index] = vertex;
        }

        int popVertex() {
            int resultVertex = vertices[0];
            poppedKey = keys[0];

            int lastVertex = vertices[--size];
            double lastKey = keys[size];
            if (size > 0) {
                int index = 0;
                while (true) {
                    int left = (index << 1) + 1;
                    if (left >= size) {
                        break;
                    }
                    int right = left + 1;
                    int child = left;
                    if (right < size && less(keys[right], vertices[right], keys[left], vertices[left])) {
                        child = right;
                    }
                    if (lessOrEqual(lastKey, lastVertex, keys[child], vertices[child])) {
                        break;
                    }
                    keys[index] = keys[child];
                    vertices[index] = vertices[child];
                    index = child;
                }
                keys[index] = lastKey;
                vertices[index] = lastVertex;
            }

            return resultVertex;
        }

        private static boolean less(double aKey, int aVertex, double bKey, int bVertex) {
            int compare = Double.compare(aKey, bKey);
            return compare < 0 || (compare == 0 && aVertex < bVertex);
        }

        private static boolean lessOrEqual(double aKey, int aVertex, double bKey, int bVertex) {
            int compare = Double.compare(aKey, bKey);
            return compare < 0 || (compare == 0 && aVertex <= bVertex);
        }
    }
}

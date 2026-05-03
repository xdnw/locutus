package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
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
 *   <li>A slack edge from source to sink allows unused supply to drain at zero marginal cost.</li>
 * </ul>
 *
 * <p><b>Algorithm:</b> successive shortest paths with SPFA (queue-based Bellman-Ford).
 * Handles negative-cost edges from the score-negate transform. Each augmentation sends 1 unit
 * of flow along the cheapest source-to-sink path. Stops when no beneficial path remains
 * (shortest-path cost ≥ 0, meaning only zero-cost slack paths remain).
 *
 * <p><b>Complexity:</b> O(supply_total × (V + E)) where V = nAtt + nDef + 2 and E = edge count.
 * For typical blitz sizes (50×200, ~400 candidate edges, supply ≤ 5) this is substantially
 * cheaper than object-based MCMF.
 */
final class PrimitiveAssignmentSolver {

    /** Tie-breaker weight for counter-risk in edge cost. Primary score dominates. */
    private static final double EPS1 = 1e-3;

    /** Tie-breaker weight for attacker strength rank in edge cost. */
    private static final double EPS2 = 1e-6;

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
        for (int ai = 0; ai < nAtt; ai++) totalSupply += supply[ai];
        if (totalSupply == 0 || edges.edgeCount() == 0) return Map.of();

        // Vertex layout:
        //   0          = source
        //   1..nAtt    = attacker nodes  (attacker ai → vertex ai+1)
        //   nAtt+1..nAtt+nDef = defender nodes (defender di → vertex nAtt+di+1)
        //   nAtt+nDef+1 = sink
        final int SOURCE = 0;
        final int SINK = nAtt + nDef + 1;
        final int nV = nAtt + nDef + 2;

        // Pre-allocate edge arrays: each addEdgePair uses 2 slots.
        int arraySize = (nAtt + nDef + 1 + edges.edgeCount()) * 2 + 4;
        int[] eTo = new int[arraySize];
        int[] eCap = new int[arraySize];
        double[] eCost = new double[arraySize];
        int[] eNext = new int[arraySize];
        int[] head = new int[nV];
        Arrays.fill(head, -1);

        // Map candidate edge index → its forward edge slot (for result extraction)
        int[] candidateFwdSlot = new int[edges.edgeCount()];
        Arrays.fill(candidateFwdSlot, -1);

        int ptr = 0;

        // Source → attacker supply edges
        for (int ai = 0; ai < nAtt; ai++) {
            if (supply[ai] > 0) {
                ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, SOURCE, ai + 1, supply[ai], 0.0);
            }
        }

        // Defender → sink demand edges
        for (int di = 0; di < nDef; di++) {
            if (capacity[di] > 0) {
                ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, nAtt + di + 1, SINK, capacity[di], 0.0);
            }
        }

        // Slack edge source → sink (unused supply drains for free)
        ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, SOURCE, SINK, totalSupply, 0.0);

        // Candidate edges: attacker → defender
        for (int e = 0; e < edges.edgeCount(); e++) {
            int ai = edges.attackerIndex(e);
            int di = edges.defenderIndex(e);
            if (supply[ai] <= 0 || capacity[di] <= 0) continue;
            if (excludedPairKeys.contains(pairKey(attackerNationIds[ai], defenderNationIds[di]))) continue;
            int rank = (attStrengthRank != null && ai < attStrengthRank.length) ? attStrengthRank[ai] : 0;
            double edgeCost = (scoreOverride == null)
                    ? edges.edgeCost(e, EPS1, EPS2, rank)
                    : (-scoreOverride[e] + EPS1 * edges.counterRisk(e) + EPS2 * rank);
            candidateFwdSlot[e] = ptr;
            ptr = addEdgePair(eTo, eCap, eCost, eNext, head, ptr, ai + 1, nAtt + di + 1, 1, edgeCost);
        }

        // ---- SPFA successive shortest paths --------------------------------
        double[] dist = new double[nV];
        int[] prevEdge = new int[nV];
        boolean[] inQueue = new boolean[nV];
        ArrayDeque<Integer> q = new ArrayDeque<>(nV);

        while (true) {
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(prevEdge, -1);
            dist[SOURCE] = 0.0;
            q.clear();
            q.add(SOURCE);
            inQueue[SOURCE] = true;

            while (!q.isEmpty()) {
                int u = q.poll();
                inQueue[u] = false;
                for (int eid = head[u]; eid != -1; eid = eNext[eid]) {
                    if (eCap[eid] > 0) {
                        double nd = dist[u] + eCost[eid];
                        int v = eTo[eid];
                        if (nd < dist[v] - 1e-12) {
                            dist[v] = nd;
                            prevEdge[v] = eid;
                            if (!inQueue[v]) {
                                q.add(v);
                                inQueue[v] = true;
                            }
                        }
                    }
                }
            }

            // Stop when no strictly-negative-cost augmenting path remains
            if (dist[SINK] == Double.POSITIVE_INFINITY || dist[SINK] >= -1e-12) break;

            // Augment 1 unit along the shortest path
            int v = SINK;
            while (v != SOURCE) {
                int eid = prevEdge[v];
                eCap[eid]--;
                eCap[eid ^ 1]++;
                v = eTo[eid ^ 1];
            }
        }

        // ---- Extract assignment from consumed forward edges ----------------
        Map<Integer, List<Integer>> assignment = new Int2ObjectLinkedOpenHashMap<>();
        for (int e = 0; e < edges.edgeCount(); e++) {
            int fwdSlot = candidateFwdSlot[e];
            if (fwdSlot < 0) continue;
            // Forward capacity was 1; if now 0, the edge carried flow (was assigned)
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
}

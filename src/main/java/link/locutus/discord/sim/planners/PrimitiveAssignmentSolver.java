package link.locutus.discord.sim.planners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
            int rank = (attStrengthRank != null && ai < attStrengthRank.length) ? attStrengthRank[ai] : 0;
            double edgeCost = edges.edgeCost(e, EPS1, EPS2, rank);
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
        Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
        for (int e = 0; e < edges.edgeCount(); e++) {
            int fwdSlot = candidateFwdSlot[e];
            if (fwdSlot < 0) continue;
            // Forward capacity was 1; if now 0, the edge carried flow (was assigned)
            if (eCap[fwdSlot] == 0) {
                int attNationId = attackerNationIds[edges.attackerIndex(e)];
                int defNationId = defenderNationIds[edges.defenderIndex(e)];
                assignment.computeIfAbsent(attNationId, k -> new ArrayList<>()).add(defNationId);
            }
        }
        return assignment;
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

package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

import java.util.Arrays;

/**
 * Dense primitive-backed storage for scored (attacker, defender) candidate edges.
 *
 * <p>Edges are keyed by <em>scenario indexes</em> into {@link link.locutus.discord.sim.planners.compile.CompiledScenario},
 * not by nation IDs. This enables O(1) lookup into compiled arrays during scoring and solving.
 *
 * <p>Per-edge authoritative fields:
 * <ul>
 *   <li>{@code attackerIndexes} — scenario attacker index</li>
 *   <li>{@code defenderIndexes} — scenario defender index</li>
 *   <li>{@code preferredWarTypeIds} — preferred war-type ordinal</li>
 *   <li>{@code bestAttackTypeIds} — first attack-type ordinal chosen by the bounded opening rollout</li>
 *   <li>{@code scalarScores} — primary maximization score</li>
 *   <li>{@code counterRisks} — counter-risk penalty [0, 1]</li>
 * </ul>

 * <p>Optional retained component fields are allocated only when the active objective asks for
 * them via its candidate-edge component policy.
 *
 * <p>Assignment-context aggregates (pressure, commitment) are NOT stored per edge; they are
 * maintained in the solver/refiner as attacker/defender-level arrays.
 *
 * <p>Pruning and effort-tier decisions do not live on the edge: they are re-derived from
 * a cheap kernel-EV probe at evaluation time. No {@code ViabilityBand} enum is stored.
 *
 * <p>Thread-safety: not thread-safe; each planner invocation uses its own instance.
 */
final class CandidateEdgeTable {

    private static final int INITIAL_CAPACITY = 64;

    private int edgeCount;
    private CandidateEdgeStorage edges;
    private int[] cachedPairKeyAttackerNationIds;
    private int[] cachedPairKeyDefenderNationIds;
    private long[] cachedEdgePairKeys;
    private Long2IntOpenHashMap cachedEdgeIndexByPair;
    private int[] cachedAttackerEdgeOffsets;
    private int[] cachedAttackerEdgeIndexes;

    CandidateEdgeTable() {
        this(INITIAL_CAPACITY);
    }

    CandidateEdgeTable(int initialCapacity) {
        int cap = Math.max(4, initialCapacity);
        edges = new CandidateEdgeStorage(cap, CandidateEdgeComponentPolicy.none());
        edgeCount = 0;
    }

    void configureComponentRetention(CandidateEdgeComponentPolicy policy) {
        edges.reconfigureComponentRetention(policy);
    }

    void ensureCapacity(int capacity) {
        edges.ensureCapacity(capacity);
    }

    // ---- Mutation -----------------------------------------------------------

    /**
     * Appends a new edge. Returns the edge index.
     */
    int add(int attackerIndex, int defenderIndex, float score, float counterRisk) {
        return add(attackerIndex, defenderIndex, (byte) 0, (byte) 0, score, counterRisk, 0f, 0f, 0f, 0f, 0f);
    }

    int add(
            int attackerIndex,
            int defenderIndex,
            byte preferredWarTypeId,
            byte bestAttackTypeId,
            float score,
            float counterRisk
    ) {
        return add(
            attackerIndex,
            defenderIndex,
            preferredWarTypeId,
            bestAttackTypeId,
            score,
            counterRisk,
            0f,
            0f,
            0f,
            0f,
            0f
        );
    }

    int add(
            int attackerIndex,
            int defenderIndex,
            byte preferredWarTypeId,
            byte bestAttackTypeId,
            float score,
            float counterRisk,
            float immediateHarm,
            float selfExposure,
            float resourceSwing,
            float controlLeverage,
            float futureWarLeverage
    ) {
            return add(
                attackerIndex,
                defenderIndex,
                preferredWarTypeId,
                bestAttackTypeId,
                score,
                counterRisk,
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                futureWarLeverage,
                0f
        );
    }

    int add(
            int attackerIndex,
            int defenderIndex,
            byte preferredWarTypeId,
            byte bestAttackTypeId,
            float score,
            float counterRisk,
            float immediateHarm,
            float selfExposure,
            float resourceSwing,
            float controlLeverage,
            float futureWarLeverage,
            float declarationReadiness
    ) {
        edges.ensureCapacity(edgeCount + 1);
        int i = edgeCount++;
        invalidateLookupCaches();
        edges.write(
                i,
                attackerIndex,
                defenderIndex,
                preferredWarTypeId,
                bestAttackTypeId,
                score,
                counterRisk,
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                futureWarLeverage,
                declarationReadiness
        );
        return i;
    }

    /**
     * Removes all edges, resetting the count to 0 without releasing backing arrays.
     */
    void clear() {
        if (edgeCount != 0) {
            invalidateLookupCaches();
        }
        edgeCount = 0;
    }

    /**
     * Returns a deep copy of {@code source} suitable for per-edge rescoring without aliasing the
     * original storage. Callers who need to rebuild candidate edge scores from projected
     * mid-horizon state can clone the seed table, then mutate scores on the clone via
     * {@link #scaleScalarScore(int, float)}.
     */
    static CandidateEdgeTable copyOf(CandidateEdgeTable source) {
        CandidateEdgeTable copy = new CandidateEdgeTable(Math.max(INITIAL_CAPACITY, source.edgeCount));
        copy.edgeCount = source.edgeCount;
        copy.edges = source.edges.deepCopy();
        copy.cachedPairKeyAttackerNationIds = source.cachedPairKeyAttackerNationIds;
        copy.cachedPairKeyDefenderNationIds = source.cachedPairKeyDefenderNationIds;
        copy.cachedEdgePairKeys = source.cachedEdgePairKeys;
        copy.cachedEdgeIndexByPair = source.cachedEdgeIndexByPair;
        copy.cachedAttackerEdgeOffsets = source.cachedAttackerEdgeOffsets;
        copy.cachedAttackerEdgeIndexes = source.cachedAttackerEdgeIndexes;
        return copy;
    }

    // ---- Accessors ----------------------------------------------------------

    int edgeCount() {
        return edgeCount;
    }

    int attackerIndex(int edge) {
        return edges.attackerIndexAt(edge);
    }

    int defenderIndex(int edge) {
        return edges.defenderIndexAt(edge);
    }

    long[] edgePairKeys(int[] attackerNationIds, int[] defenderNationIds) {
        if (cachedEdgePairKeys != null
                && cachedPairKeyAttackerNationIds == attackerNationIds
                && cachedPairKeyDefenderNationIds == defenderNationIds) {
            return cachedEdgePairKeys;
        }
        long[] edgePairKeys = new long[edgeCount];
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            edgePairKeys[edgeIndex] = pairKey(
                    attackerNationIds[attackerIndex(edgeIndex)],
                    defenderNationIds[defenderIndex(edgeIndex)]
            );
        }
        cachedPairKeyAttackerNationIds = attackerNationIds;
        cachedPairKeyDefenderNationIds = defenderNationIds;
        cachedEdgePairKeys = edgePairKeys;
        return edgePairKeys;
    }

    Long2IntOpenHashMap edgeIndexByPair(int[] attackerNationIds, int[] defenderNationIds) {
        if (cachedEdgeIndexByPair != null
                && cachedPairKeyAttackerNationIds == attackerNationIds
                && cachedPairKeyDefenderNationIds == defenderNationIds) {
            return cachedEdgeIndexByPair;
        }
        long[] edgePairKeys = edgePairKeys(attackerNationIds, defenderNationIds);
        Long2IntOpenHashMap edgeIndexByPair = new Long2IntOpenHashMap(Math.max(16, edgeCount * 2));
        edgeIndexByPair.defaultReturnValue(-1);
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            edgeIndexByPair.put(edgePairKeys[edgeIndex], edgeIndex);
        }
        cachedPairKeyAttackerNationIds = attackerNationIds;
        cachedPairKeyDefenderNationIds = defenderNationIds;
        cachedEdgeIndexByPair = edgeIndexByPair;
        return edgeIndexByPair;
    }

    byte preferredWarTypeId(int edge) {
        return edges.preferredWarTypeIdAt(edge);
    }

    byte bestAttackTypeId(int edge) {
        return edges.bestAttackTypeIdAt(edge);
    }

    boolean sameProjectionTopology(CandidateEdgeTable other) {
        if (this == other) {
            return true;
        }
        if (other == null || edgeCount != other.edgeCount) {
            return false;
        }
        for (int edge = 0; edge < edgeCount; edge++) {
            if (attackerIndex(edge) != other.attackerIndex(edge)
                    || defenderIndex(edge) != other.defenderIndex(edge)
                    || preferredWarTypeId(edge) != other.preferredWarTypeId(edge)
                    || bestAttackTypeId(edge) != other.bestAttackTypeId(edge)) {
                return false;
            }
        }
        return true;
    }

    float scalarScore(int edge) {
        return edges.scoreAt(edge);
    }

    void setScalarScore(int edge, float score) {
        edges.setScalarScore(edge, score);
    }

    void scaleScalarScore(int edge, float factor) {
        edges.scaleScalarScore(edge, factor);
    }

    /**
     * Multiplies the scalar score and every retained component value for an edge by {@code factor}.
     *
     * <p>This is the dense primitive equivalent of rebuilding a candidate edge from a projected
     * mid-horizon {@link link.locutus.discord.sim.planners.LongHorizonForwardProjection.MidHorizonSnapshot}:
     * an attacker whose projected combat strength and score have been ground down by incoming wars
     * has its outgoing edges' immediate harm, control leverage, future-war leverage, etc. all
     * proportionally reduced, not just the scalar opening score.</p>
     */
    void rescaleEdgeFromProjectedState(int edge, float factor) {
        edges.rescaleFromProjectedState(edge, factor);
    }

    void rescaleAttackerEdgesFromProjectedState(int attackerIndex, float factor) {
        ensureAttackerEdgeIndex();
        if (attackerIndex < 0 || attackerIndex + 1 >= cachedAttackerEdgeOffsets.length) {
            return;
        }
        int start = cachedAttackerEdgeOffsets[attackerIndex];
        int end = cachedAttackerEdgeOffsets[attackerIndex + 1];
        for (int offset = start; offset < end; offset++) {
            edges.rescaleFromProjectedState(cachedAttackerEdgeIndexes[offset], factor);
        }
    }

    float counterRisk(int edge) {
        return edges.counterRiskAt(edge);
    }

    boolean retainsImmediateHarm() {
        return edges.retainsImmediateHarm();
    }

    boolean retainsSelfExposure() {
        return edges.retainsSelfExposure();
    }

    boolean retainsResourceSwing() {
        return edges.retainsResourceSwing();
    }

    boolean retainsControlLeverage() {
        return edges.retainsControlLeverage();
    }

    boolean retainsFutureWarLeverage() {
        return edges.retainsFutureWarLeverage();
    }

    boolean retainsDeclarationReadiness() {
        return edges.retainsDeclarationReadiness();
    }

    float immediateHarm(int edge) {
        return edges.immediateHarmAt(edge);
    }

    float selfExposure(int edge) {
        return edges.selfExposureAt(edge);
    }

    float resourceSwing(int edge) {
        return edges.resourceSwingAt(edge);
    }

    float controlLeverage(int edge) {
        return edges.controlLeverageAt(edge);
    }

    float declarationReadiness(int edge) {
        return edges.declarationReadinessAt(edge);
    }

    private void invalidateLookupCaches() {
        cachedPairKeyAttackerNationIds = null;
        cachedPairKeyDefenderNationIds = null;
        cachedEdgePairKeys = null;
        cachedEdgeIndexByPair = null;
        cachedAttackerEdgeOffsets = null;
        cachedAttackerEdgeIndexes = null;
    }

    private void ensureAttackerEdgeIndex() {
        if (cachedAttackerEdgeOffsets != null && cachedAttackerEdgeIndexes != null) {
            return;
        }
        int maxAttackerIndex = -1;
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            maxAttackerIndex = Math.max(maxAttackerIndex, attackerIndex(edgeIndex));
        }
        cachedAttackerEdgeOffsets = new int[maxAttackerIndex + 2];
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            cachedAttackerEdgeOffsets[attackerIndex(edgeIndex) + 1]++;
        }
        for (int attackerIndex = 0; attackerIndex < maxAttackerIndex + 1; attackerIndex++) {
            cachedAttackerEdgeOffsets[attackerIndex + 1] += cachedAttackerEdgeOffsets[attackerIndex];
        }
        cachedAttackerEdgeIndexes = new int[edgeCount];
        int[] writeOffsets = Arrays.copyOf(cachedAttackerEdgeOffsets, cachedAttackerEdgeOffsets.length);
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            int attackerIndex = attackerIndex(edgeIndex);
            cachedAttackerEdgeIndexes[writeOffsets[attackerIndex]++] = edgeIndex;
        }
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) ^ (defenderNationId & 0xffffffffL);
    }

    float futureWarLeverage(int edge) {
        return edges.futureWarLeverageAt(edge);
    }

    /**
     * Returns the edge cost used by the solver: {@code -scalarScore + eps1 * counterRisk + eps2 * attackerStrengthRank}.
     *
     * <p>eps1/eps2 are small enough that primary score dominates but ties are strictly ordered.
     */
    double edgeCost(int edge, double eps1, double eps2, int attackerStrengthRank) {
        return -edges.scoreAt(edge) + eps1 * edges.counterRiskAt(edge) + eps2 * attackerStrengthRank;
    }

    @Override
    public String toString() {
        return "CandidateEdgeTable{edgeCount=" + edgeCount + "}";
    }
}

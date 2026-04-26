package link.locutus.discord.sim.planners;

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
    private int[] attackerIndexes;
    private int[] defenderIndexes;
    private byte[] preferredWarTypeIds;
    private byte[] bestAttackTypeIds;
    private float[] scalarScores;
    private float[] counterRisks;
    private CandidateEdgeComponents retainedComponents;

    CandidateEdgeTable() {
        this(INITIAL_CAPACITY);
    }

    CandidateEdgeTable(int initialCapacity) {
        int cap = Math.max(4, initialCapacity);
        attackerIndexes = new int[cap];
        defenderIndexes = new int[cap];
        preferredWarTypeIds = new byte[cap];
        bestAttackTypeIds = new byte[cap];
        scalarScores = new float[cap];
        counterRisks = new float[cap];
        retainedComponents = new CandidateEdgeComponents(cap, CandidateEdgeComponentPolicy.none());
        edgeCount = 0;
    }

    void configureComponentRetention(CandidateEdgeComponentPolicy policy) {
        retainedComponents = new CandidateEdgeComponents(attackerIndexes.length, policy);
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
        ensureCapacity(edgeCount + 1);
        int i = edgeCount++;
        attackerIndexes[i] = attackerIndex;
        defenderIndexes[i] = defenderIndex;
        preferredWarTypeIds[i] = preferredWarTypeId;
        bestAttackTypeIds[i] = bestAttackTypeId;
        scalarScores[i] = score;
        counterRisks[i] = counterRisk;
        retainedComponents.set(i, immediateHarm, selfExposure, resourceSwing, controlLeverage, futureWarLeverage);
        return i;
    }

    /**
     * Removes all edges, resetting the count to 0 without releasing backing arrays.
     */
    void clear() {
        edgeCount = 0;
    }

    // ---- Accessors ----------------------------------------------------------

    int edgeCount() {
        return edgeCount;
    }

    int attackerIndex(int edge) {
        return attackerIndexes[edge];
    }

    int defenderIndex(int edge) {
        return defenderIndexes[edge];
    }

    byte preferredWarTypeId(int edge) {
        return preferredWarTypeIds[edge];
    }

    byte bestAttackTypeId(int edge) {
        return bestAttackTypeIds[edge];
    }

    float scalarScore(int edge) {
        return scalarScores[edge];
    }

    void scaleScalarScore(int edge, float factor) {
        scalarScores[edge] *= factor;
    }

    float counterRisk(int edge) {
        return counterRisks[edge];
    }

    boolean retainsImmediateHarm() {
        return retainedComponents.retainsImmediateHarm();
    }

    boolean retainsSelfExposure() {
        return retainedComponents.retainsSelfExposure();
    }

    boolean retainsResourceSwing() {
        return retainedComponents.retainsResourceSwing();
    }

    boolean retainsControlLeverage() {
        return retainedComponents.retainsControlLeverage();
    }

    boolean retainsFutureWarLeverage() {
        return retainedComponents.retainsFutureWarLeverage();
    }

    float immediateHarm(int edge) {
        return retainedComponents.immediateHarm(edge);
    }

    float selfExposure(int edge) {
        return retainedComponents.selfExposure(edge);
    }

    float resourceSwing(int edge) {
        return retainedComponents.resourceSwing(edge);
    }

    float controlLeverage(int edge) {
        return retainedComponents.controlLeverage(edge);
    }

    float futureWarLeverage(int edge) {
        return retainedComponents.futureWarLeverage(edge);
    }

    /**
     * Returns the edge cost used by the solver: {@code -scalarScore + eps1 * counterRisk + eps2 * attackerStrengthRank}.
     *
     * <p>eps1/eps2 are small enough that primary score dominates but ties are strictly ordered.
     */
    double edgeCost(int edge, double eps1, double eps2, int attackerStrengthRank) {
        return -scalarScores[edge] + eps1 * counterRisks[edge] + eps2 * attackerStrengthRank;
    }

    // ---- Internal -----------------------------------------------------------

    private void ensureCapacity(int needed) {
        if (needed <= attackerIndexes.length) return;
        int newCap = Math.max(needed, attackerIndexes.length * 2);
        attackerIndexes = Arrays.copyOf(attackerIndexes, newCap);
        defenderIndexes = Arrays.copyOf(defenderIndexes, newCap);
        preferredWarTypeIds = Arrays.copyOf(preferredWarTypeIds, newCap);
        bestAttackTypeIds = Arrays.copyOf(bestAttackTypeIds, newCap);
        scalarScores = Arrays.copyOf(scalarScores, newCap);
        counterRisks = Arrays.copyOf(counterRisks, newCap);
        retainedComponents.ensureCapacity(newCap);
    }

    @Override
    public String toString() {
        return "CandidateEdgeTable{edgeCount=" + edgeCount + "}";
    }
}

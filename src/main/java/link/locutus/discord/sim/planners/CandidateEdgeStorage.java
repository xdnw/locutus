package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

import java.util.Arrays;

final class CandidateEdgeStorage {
    private int[] attackerIndexes;
    private int[] defenderIndexes;
    private byte[] preferredWarTypeIds;
    private byte[] bestAttackTypeIds;
    private float[] scalarScores;
    private float[] counterRisks;
    private CandidateEdgeComponents retainedComponents;

    CandidateEdgeStorage(int capacity, CandidateEdgeComponentPolicy componentPolicy) {
        int effectiveCapacity = Math.max(0, capacity);
        attackerIndexes = new int[effectiveCapacity];
        defenderIndexes = new int[effectiveCapacity];
        preferredWarTypeIds = new byte[effectiveCapacity];
        bestAttackTypeIds = new byte[effectiveCapacity];
        scalarScores = new float[effectiveCapacity];
        counterRisks = new float[effectiveCapacity];
        retainedComponents = new CandidateEdgeComponents(effectiveCapacity, componentPolicy);
    }

    int capacity() {
        return attackerIndexes.length;
    }

    void reconfigureComponentRetention(CandidateEdgeComponentPolicy componentPolicy) {
        retainedComponents = new CandidateEdgeComponents(attackerIndexes.length, componentPolicy);
    }

    void ensureCapacity(int needed) {
        if (needed <= attackerIndexes.length) {
            return;
        }
        int newCapacity = Math.max(needed, attackerIndexes.length * 2);
        attackerIndexes = Arrays.copyOf(attackerIndexes, newCapacity);
        defenderIndexes = Arrays.copyOf(defenderIndexes, newCapacity);
        preferredWarTypeIds = Arrays.copyOf(preferredWarTypeIds, newCapacity);
        bestAttackTypeIds = Arrays.copyOf(bestAttackTypeIds, newCapacity);
        scalarScores = Arrays.copyOf(scalarScores, newCapacity);
        counterRisks = Arrays.copyOf(counterRisks, newCapacity);
        retainedComponents.ensureCapacity(newCapacity);
    }

    void write(
            int index,
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
        attackerIndexes[index] = attackerIndex;
        defenderIndexes[index] = defenderIndex;
        preferredWarTypeIds[index] = preferredWarTypeId;
        bestAttackTypeIds[index] = bestAttackTypeId;
        scalarScores[index] = score;
        counterRisks[index] = counterRisk;
        retainedComponents.set(index, immediateHarm, selfExposure, resourceSwing, controlLeverage, futureWarLeverage);
    }

    int attackerIndexAt(int index) {
        return attackerIndexes[index];
    }

    int defenderIndexAt(int index) {
        return defenderIndexes[index];
    }

    byte preferredWarTypeIdAt(int index) {
        return preferredWarTypeIds[index];
    }

    byte bestAttackTypeIdAt(int index) {
        return bestAttackTypeIds[index];
    }

    float scoreAt(int index) {
        return scalarScores[index];
    }

    void scaleScalarScore(int index, float factor) {
        scalarScores[index] *= factor;
    }

    float counterRiskAt(int index) {
        return counterRisks[index];
    }

    void rescaleFromProjectedState(int index, float factor) {
        scalarScores[index] *= factor;
        retainedComponents.scale(index, factor);
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

    float immediateHarmAt(int index) {
        return retainedComponents.immediateHarm(index);
    }

    float selfExposureAt(int index) {
        return retainedComponents.selfExposure(index);
    }

    float resourceSwingAt(int index) {
        return retainedComponents.resourceSwing(index);
    }

    float controlLeverageAt(int index) {
        return retainedComponents.controlLeverage(index);
    }

    float futureWarLeverageAt(int index) {
        return retainedComponents.futureWarLeverage(index);
    }

    void swap(int lhs, int rhs) {
        int attackerSwap = attackerIndexes[lhs];
        attackerIndexes[lhs] = attackerIndexes[rhs];
        attackerIndexes[rhs] = attackerSwap;

        int defenderSwap = defenderIndexes[lhs];
        defenderIndexes[lhs] = defenderIndexes[rhs];
        defenderIndexes[rhs] = defenderSwap;

        byte warTypeSwap = preferredWarTypeIds[lhs];
        preferredWarTypeIds[lhs] = preferredWarTypeIds[rhs];
        preferredWarTypeIds[rhs] = warTypeSwap;

        byte attackTypeSwap = bestAttackTypeIds[lhs];
        bestAttackTypeIds[lhs] = bestAttackTypeIds[rhs];
        bestAttackTypeIds[rhs] = attackTypeSwap;

        float scoreSwap = scalarScores[lhs];
        scalarScores[lhs] = scalarScores[rhs];
        scalarScores[rhs] = scoreSwap;

        float counterRiskSwap = counterRisks[lhs];
        counterRisks[lhs] = counterRisks[rhs];
        counterRisks[rhs] = counterRiskSwap;

        retainedComponents.swap(lhs, rhs);
    }

    CandidateEdgeStorage deepCopy() {
        CandidateEdgeStorage copy = new CandidateEdgeStorage(0, CandidateEdgeComponentPolicy.none());
        copy.attackerIndexes = Arrays.copyOf(attackerIndexes, attackerIndexes.length);
        copy.defenderIndexes = Arrays.copyOf(defenderIndexes, defenderIndexes.length);
        copy.preferredWarTypeIds = Arrays.copyOf(preferredWarTypeIds, preferredWarTypeIds.length);
        copy.bestAttackTypeIds = Arrays.copyOf(bestAttackTypeIds, bestAttackTypeIds.length);
        copy.scalarScores = Arrays.copyOf(scalarScores, scalarScores.length);
        copy.counterRisks = Arrays.copyOf(counterRisks, counterRisks.length);
        copy.retainedComponents = retainedComponents.deepCopy();
        return copy;
    }
}
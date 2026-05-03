package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.StrategicAssetValue;

import java.util.function.IntPredicate;

final class PlannerReplayTurnMetrics {
    private static final int SIDE_COUNT = 2;
    private static final int ATTACKER_SIDE_INDEX = 0;
    private static final int DEFENDER_SIDE_INDEX = 1;
    private static final int ATTACK_TYPE_COUNT = AttackType.values().length;
    private static final int SUCCESS_TYPE_COUNT = SuccessType.values.length;
    private static final int WAR_TYPE_COUNT = WarType.values.length;
    private static final int PURCHASABLE_UNIT_COUNT = SimUnits.PURCHASABLE_UNITS.length;

    private final IntPredicate isAttackerNationId;
    private final int[] summaryScalarLanes = new int[8];
    private final int[] summaryWarTypeCounts = new int[SIDE_COUNT * WAR_TYPE_COUNT];
    private final int[] summaryAttackOutcomeCounts = new int[SIDE_COUNT * ATTACK_TYPE_COUNT * SUCCESS_TYPE_COUNT];
    private final int[] summaryUnitLossCounts = new int[SIDE_COUNT * PURCHASABLE_UNIT_COUNT];
    private final int[] summaryInfraLossCents = new int[SIDE_COUNT];
    private final int[] summaryStrategicUnitLossCents = new int[SIDE_COUNT];
    private boolean touched;

    PlannerReplayTurnMetrics(IntPredicate isAttackerNationId) {
        this.isAttackerNationId = isAttackerNationId;
    }

    void record(
            int actorNationId,
            int targetNationId,
            AttackType attackType,
            SuccessType success,
            int[] actorUnitLosses,
            int actorResearchBits,
            int[] targetUnitLosses,
            int targetResearchBits,
            double targetInfraDestroyed
    ) {
        int actorSideIndex = sideIndex(actorNationId);
        int targetSideIndex = sideIndex(targetNationId);
        int attackBucket = ((actorSideIndex * ATTACK_TYPE_COUNT) + attackType.ordinal()) * SUCCESS_TYPE_COUNT + success.ordinal();
        summaryAttackOutcomeCounts[attackBucket]++;
        addPurchasableLosses(actorSideIndex, actorUnitLosses, actorResearchBits);
        addPurchasableLosses(targetSideIndex, targetUnitLosses, targetResearchBits);
        long infraLossCents = Math.max(0L, Math.round(targetInfraDestroyed * 100d));
        summaryInfraLossCents[targetSideIndex] += Math.toIntExact(infraLossCents);
        touched = true;
    }

    void recordDeclaredWar(int declarerNationId, int warTypeOrdinal) {
        int sideIndex = sideIndex(declarerNationId);
        summaryScalarLanes[sideScalarOffset(sideIndex)]++;
        summaryWarTypeCounts[(sideIndex * WAR_TYPE_COUNT) + warTypeOrdinal]++;
        touched = true;
    }

    void recordConcludedWar(int attackerNationId, int defenderNationId, int endStatusOrdinal) {
        WarStatus status = WarStatus.values[endStatusOrdinal];
        int attackerSideIndex = sideIndex(attackerNationId);
        int defenderSideIndex = sideIndex(defenderNationId);
        if (status == WarStatus.ATTACKER_VICTORY) {
            summaryScalarLanes[sideScalarOffset(attackerSideIndex) + 1]++;
            summaryScalarLanes[sideScalarOffset(defenderSideIndex) + 2]++;
        } else if (status == WarStatus.DEFENDER_VICTORY) {
            summaryScalarLanes[sideScalarOffset(defenderSideIndex) + 1]++;
            summaryScalarLanes[sideScalarOffset(attackerSideIndex) + 2]++;
        } else if (!status.isActive()) {
            summaryScalarLanes[sideScalarOffset(attackerSideIndex) + 3]++;
            summaryScalarLanes[sideScalarOffset(defenderSideIndex) + 3]++;
        }
        touched = true;
    }

    boolean isEmpty() {
        return !touched;
    }

    int[] summaryScalarLanes() {
        return summaryScalarLanes.clone();
    }

    int[] summaryWarTypeCounts() {
        return summaryWarTypeCounts.clone();
    }

    int[] summaryAttackOutcomeCounts() {
        return summaryAttackOutcomeCounts.clone();
    }

    int[] summaryUnitLossCounts() {
        return summaryUnitLossCounts.clone();
    }

    int[] summaryInfraLossCents() {
        return summaryInfraLossCents.clone();
    }

    int[] summaryStrategicUnitLossCents() {
        return summaryStrategicUnitLossCents.clone();
    }

    private int sideIndex(int nationId) {
        return isAttackerNationId.test(nationId) ? ATTACKER_SIDE_INDEX : DEFENDER_SIDE_INDEX;
    }

    private static int sideScalarOffset(int sideIndex) {
        return sideIndex == ATTACKER_SIDE_INDEX ? 0 : 4;
    }

    private void addPurchasableLosses(int sideIndex, int[] source, int researchBits) {
        if (source == null) {
            return;
        }
        int sideOffset = sideIndex * PURCHASABLE_UNIT_COUNT;
        double strategicValue = 0d;
        for (int unitIndex = 0; unitIndex < PURCHASABLE_UNIT_COUNT; unitIndex++) {
            MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
            int ordinal = unit.ordinal();
            if (ordinal < source.length) {
                int loss = Math.max(0, source[ordinal]);
                if (loss > 0) {
                    summaryUnitLossCounts[sideOffset + unitIndex] += loss;
                    strategicValue += StrategicAssetValue.unitValue(unit, loss, researchBits);
                }
            }
        }
        if (strategicValue > 0d) {
            long cents = Math.max(0L, Math.round(strategicValue * 100d));
            summaryStrategicUnitLossCents[sideIndex] = Math.toIntExact(
                    Math.min((long) Integer.MAX_VALUE, summaryStrategicUnitLossCents[sideIndex] + cents)
            );
        }
        touched = true;
    }
}
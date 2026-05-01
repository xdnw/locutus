package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.SimUnits;

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
            int[] targetUnitLosses,
            double targetInfraDestroyed
    ) {
        int actorSideIndex = sideIndex(actorNationId);
        int targetSideIndex = sideIndex(targetNationId);
        int attackBucket = ((actorSideIndex * ATTACK_TYPE_COUNT) + attackType.ordinal()) * SUCCESS_TYPE_COUNT + success.ordinal();
        summaryAttackOutcomeCounts[attackBucket]++;
        addPurchasableLosses(actorSideIndex, actorUnitLosses);
        addPurchasableLosses(targetSideIndex, targetUnitLosses);
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

    private int sideIndex(int nationId) {
        return isAttackerNationId.test(nationId) ? ATTACKER_SIDE_INDEX : DEFENDER_SIDE_INDEX;
    }

    private static int sideScalarOffset(int sideIndex) {
        return sideIndex == ATTACKER_SIDE_INDEX ? 0 : 4;
    }

    private void addPurchasableLosses(int sideIndex, int[] source) {
        if (source == null) {
            return;
        }
        int sideOffset = sideIndex * PURCHASABLE_UNIT_COUNT;
        for (int unitIndex = 0; unitIndex < PURCHASABLE_UNIT_COUNT; unitIndex++) {
            MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
            int ordinal = unit.ordinal();
            if (ordinal < source.length) {
                summaryUnitLossCounts[sideOffset + unitIndex] += Math.max(0, source[ordinal]);
            }
        }
        touched = true;
    }
}
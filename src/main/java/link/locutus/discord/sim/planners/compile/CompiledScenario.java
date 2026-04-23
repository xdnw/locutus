package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.util.PW;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.Arrays;

/**
 * Dense compiled planner input for legality and range filtering.
 *
 * <p>This first substrate intentionally stays narrow: it owns the hot candidate-generation
 * indexes while exact combat evaluation still runs through the existing sim path.</p>
 */
public final class CompiledScenario {
    private final List<DBNationSnapshot> attackers;
    private final List<DBNationSnapshot> defenders;
    private final int[] attackerNationIds;
    private final int[] defenderNationIds;
    private final double[] attackerScores;
    private final double[] defenderScores;
    private final int[] attackerUnitOffsets;
    private final int[] defenderUnitOffsets;
    private final int[] attackerUnitsFlat;
    private final int[] defenderUnitsFlat;
    private final int[] attackerCityInfraOffsets;
    private final int[] defenderCityInfraOffsets;
    private final double[] attackerCityInfraFlat;
    private final double[] defenderCityInfraFlat;
    private final byte[] attackerFreeOffSlots;
    private final byte[] defenderFreeDefSlots;
    private final int[] defenderAllianceIds;
    private final float[] attackerActivityWeights;
    private final float[] defenderActivityWeights;
    private final int[] attackerResearchBits;
    private final int[] defenderResearchBits;
    private final long[] attackerProjectBits;
    private final long[] defenderProjectBits;
    private final Map<Integer, Integer> attackerIndexByNationId;
    private final Map<Integer, Integer> defenderIndexByNationId;
    private final int[][] treatedDefenderIndexesByAttacker;
    private final Set<Long> activePairConflicts;
    private final int[][] defenderIndexesByScoreBucket;
    private final int minDefenderBucket;
    private final Map<Integer, int[]> defenderIndexesByAllianceId;
    private final int[][] relevantDefenderIndexesByAttacker;

    CompiledScenario(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            int[] attackerNationIds,
            int[] defenderNationIds,
            double[] attackerScores,
            double[] defenderScores,
            int[] attackerUnitOffsets,
            int[] defenderUnitOffsets,
            int[] attackerUnitsFlat,
            int[] defenderUnitsFlat,
            int[] attackerCityInfraOffsets,
            int[] defenderCityInfraOffsets,
            double[] attackerCityInfraFlat,
            double[] defenderCityInfraFlat,
            byte[] attackerFreeOffSlots,
            byte[] defenderFreeDefSlots,
            int[] defenderAllianceIds,
            float[] attackerActivityWeights,
            float[] defenderActivityWeights,
            int[] attackerResearchBits,
            int[] defenderResearchBits,
            long[] attackerProjectBits,
            long[] defenderProjectBits,
            Map<Integer, Integer> attackerIndexByNationId,
            Map<Integer, Integer> defenderIndexByNationId,
            int[][] treatedDefenderIndexesByAttacker,
            Set<Long> activePairConflicts,
            int[][] defenderIndexesByScoreBucket,
            int minDefenderBucket,
            Map<Integer, int[]> defenderIndexesByAllianceId,
            int[][] relevantDefenderIndexesByAttacker
    ) {
        this.attackers = attackers;
        this.defenders = defenders;
        this.attackerNationIds = attackerNationIds;
        this.defenderNationIds = defenderNationIds;
        this.attackerScores = attackerScores;
        this.defenderScores = defenderScores;
        this.attackerUnitOffsets = attackerUnitOffsets;
        this.defenderUnitOffsets = defenderUnitOffsets;
        this.attackerUnitsFlat = attackerUnitsFlat;
        this.defenderUnitsFlat = defenderUnitsFlat;
        this.attackerCityInfraOffsets = attackerCityInfraOffsets;
        this.defenderCityInfraOffsets = defenderCityInfraOffsets;
        this.attackerCityInfraFlat = attackerCityInfraFlat;
        this.defenderCityInfraFlat = defenderCityInfraFlat;
        this.attackerFreeOffSlots = attackerFreeOffSlots;
        this.defenderFreeDefSlots = defenderFreeDefSlots;
        this.defenderAllianceIds = defenderAllianceIds;
        this.attackerActivityWeights = attackerActivityWeights;
        this.defenderActivityWeights = defenderActivityWeights;
        this.attackerResearchBits = attackerResearchBits;
        this.defenderResearchBits = defenderResearchBits;
        this.attackerProjectBits = attackerProjectBits;
        this.defenderProjectBits = defenderProjectBits;
        this.attackerIndexByNationId = attackerIndexByNationId;
        this.defenderIndexByNationId = defenderIndexByNationId;
        this.treatedDefenderIndexesByAttacker = treatedDefenderIndexesByAttacker;
        this.activePairConflicts = activePairConflicts;
        this.defenderIndexesByScoreBucket = defenderIndexesByScoreBucket;
        this.minDefenderBucket = minDefenderBucket;
        this.defenderIndexesByAllianceId = defenderIndexesByAllianceId;
        this.relevantDefenderIndexesByAttacker = relevantDefenderIndexesByAttacker;
    }

    public int attackerCount() {
        return attackers.size();
    }

    public int defenderCount() {
        return defenders.size();
    }

    public DBNationSnapshot attacker(int attackerIndex) {
        return attackers.get(attackerIndex);
    }

    public DBNationSnapshot defender(int defenderIndex) {
        return defenders.get(defenderIndex);
    }

    public int attackerNationId(int attackerIndex) {
        return attackerNationIds[attackerIndex];
    }

    public int defenderNationId(int defenderIndex) {
        return defenderNationIds[defenderIndex];
    }

    public int attackerUnitCount(int attackerIndex, MilitaryUnit unit) {
        int unitOffset = SimUnits.purchasableIndex(unit);
        if (unitOffset < 0) {
            return 0;
        }
        return attackerUnitsFlat[attackerUnitOffsets[attackerIndex] + unitOffset];
    }

    public int defenderUnitCount(int defenderIndex, MilitaryUnit unit) {
        int unitOffset = SimUnits.purchasableIndex(unit);
        if (unitOffset < 0) {
            return 0;
        }
        return defenderUnitsFlat[defenderUnitOffsets[defenderIndex] + unitOffset];
    }

    public int attackerCityCount(int attackerIndex) {
        return attackerCityInfraOffsets[attackerIndex + 1] - attackerCityInfraOffsets[attackerIndex];
    }

    public int defenderCityCount(int defenderIndex) {
        return defenderCityInfraOffsets[defenderIndex + 1] - defenderCityInfraOffsets[defenderIndex];
    }

    public double attackerCityInfraAt(int attackerIndex, int cityOrdinal) {
        int base = attackerCityInfraOffsets[attackerIndex];
        return attackerCityInfraFlat[base + cityOrdinal];
    }

    public double defenderCityInfraAt(int defenderIndex, int cityOrdinal) {
        int base = defenderCityInfraOffsets[defenderIndex];
        return defenderCityInfraFlat[base + cityOrdinal];
    }

    public byte attackerFreeOffSlots(int attackerIndex) {
        return attackerFreeOffSlots[attackerIndex];
    }

    public byte defenderFreeDefSlots(int defenderIndex) {
        return defenderFreeDefSlots[defenderIndex];
    }

    public float attackerActivityWeight(int attackerIndex) {
        return attackerActivityWeights[attackerIndex];
    }

    public float defenderActivityWeight(int defenderIndex) {
        return defenderActivityWeights[defenderIndex];
    }

    public int attackerResearchBits(int attackerIndex) {
        return attackerResearchBits[attackerIndex];
    }

    public int defenderResearchBits(int defenderIndex) {
        return defenderResearchBits[defenderIndex];
    }

    public long attackerProjectBits(int attackerIndex) {
        return attackerProjectBits[attackerIndex];
    }

    public long defenderProjectBits(int defenderIndex) {
        return defenderProjectBits[defenderIndex];
    }

    public int[] relevantDefenderIndexes(int attackerIndex) {
        return relevantDefenderIndexesByAttacker[attackerIndex].clone();
    }

    public boolean isTreated(int attackerIndex, int defenderIndex) {
        return Arrays.binarySearch(treatedDefenderIndexesByAttacker[attackerIndex], defenderIndex) >= 0;
    }

    public boolean hasActivePairConflict(int attackerIndex, int defenderIndex) {
        return activePairConflicts.contains(pairKey(attackerNationIds[attackerIndex], defenderNationIds[defenderIndex]));
    }

    public void forEachDefenderIndexInRange(int attackerIndex, IntConsumer consumer) {
        double attackerScore = attackerScores[attackerIndex];
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        int minBucket = scoreBucket(minScore);
        int maxBucket = scoreBucket(maxScore);
        for (int bucket = minBucket; bucket <= maxBucket; bucket++) {
            int bucketIndex = bucket - minDefenderBucket;
            if (bucketIndex < 0 || bucketIndex >= defenderIndexesByScoreBucket.length) {
                continue;
            }
            int[] defenderIndexes = defenderIndexesByScoreBucket[bucketIndex];
            for (int defenderIndex : defenderIndexes) {
                double defenderScore = defenderScores[defenderIndex];
                if (defenderScore >= minScore && defenderScore <= maxScore) {
                    consumer.accept(defenderIndex);
                }
            }
        }
    }

    public double estimateAllianceCounterRisk(int attackerIndex, int defenderIndex) {
        int allianceId = defenderAllianceIds[defenderIndex];
        if (allianceId == 0) {
            return 0.0;
        }
        int[] sameAllianceDefenders = defenderIndexesByAllianceId.get(allianceId);
        if (sameAllianceDefenders == null || sameAllianceDefenders.length == 0) {
            return 0.0;
        }
        double attackerScore = attackerScores[attackerIndex];
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        double totalWeight = 0.0;
        double inRangeWeight = 0.0;
        for (int sameAllianceDefenderIndex : sameAllianceDefenders) {
            double weight = defenderActivityWeights[sameAllianceDefenderIndex];
            totalWeight += weight;
            double candidateScore = defenderScores[sameAllianceDefenderIndex];
            if (candidateScore >= minScore && candidateScore <= maxScore) {
                inRangeWeight += weight;
            }
        }
        return totalWeight == 0.0 ? 0.0 : inRangeWeight / totalWeight;
    }

    public Integer attackerIndex(int nationId) {
        return attackerIndexByNationId.get(nationId);
    }

    public Integer defenderIndex(int nationId) {
        return defenderIndexByNationId.get(nationId);
    }

    public static long pairKey(int attackerId, int defenderId) {
        return ((long) attackerId << 32) | (defenderId & 0xFFFFFFFFL);
    }

    static int scoreBucket(double score) {
        return (int) Math.floor(score / 25.0d);
    }
}
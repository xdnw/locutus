package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles immutable planner snapshots into dense legality and range indexes. */
public final class ScenarioCompiler {
    public CompiledScenario compile(
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            OverrideSet overrides,
            TreatyProvider treatyProvider,
            Map<Integer, Float> activityWeightsByNationId
    ) {
        List<DBNationSnapshot> attackerList = List.copyOf(attackers);
        List<DBNationSnapshot> defenderList = List.copyOf(defenders);

        int[] attackerNationIds = new int[attackerList.size()];
        int[] defenderNationIds = new int[defenderList.size()];
        double[] attackerScores = new double[attackerList.size()];
        double[] defenderScores = new double[defenderList.size()];
        int[] attackerUnitOffsets = compileUnitOffsets(attackerList);
        int[] defenderUnitOffsets = compileUnitOffsets(defenderList);
        int[] attackerUnitsFlat = compileUnitsFlat(attackerList, attackerUnitOffsets);
        int[] defenderUnitsFlat = compileUnitsFlat(defenderList, defenderUnitOffsets);
        int[] attackerCityInfraOffsets = compileCityInfraOffsets(attackerList);
        int[] defenderCityInfraOffsets = compileCityInfraOffsets(defenderList);
        double[] attackerCityInfraFlat = compileCityInfraFlat(attackerList, attackerCityInfraOffsets);
        double[] defenderCityInfraFlat = compileCityInfraFlat(defenderList, defenderCityInfraOffsets);
        byte[] attackerFreeOffSlots = new byte[attackerList.size()];
        byte[] defenderFreeDefSlots = new byte[defenderList.size()];
        int[] defenderAllianceIds = new int[defenderList.size()];
        float[] attackerActivityWeights = new float[attackerList.size()];
        float[] defenderActivityWeights = new float[defenderList.size()];
        int[] attackerResearchBits = new int[attackerList.size()];
        int[] defenderResearchBits = new int[defenderList.size()];
        long[] attackerProjectBits = new long[attackerList.size()];
        long[] defenderProjectBits = new long[defenderList.size()];
        Map<Integer, Integer> attackerIndexByNationId = new LinkedHashMap<>();
        Map<Integer, Integer> defenderIndexByNationId = new LinkedHashMap<>();

        for (int attackerIndex = 0; attackerIndex < attackerList.size(); attackerIndex++) {
            DBNationSnapshot attacker = attackerList.get(attackerIndex);
            attackerNationIds[attackerIndex] = attacker.nationId();
            attackerScores[attackerIndex] = attacker.score();
            attackerFreeOffSlots[attackerIndex] = (byte) overrides.effectiveFreeOff(attacker);
            attackerActivityWeights[attackerIndex] = activityWeightsByNationId.getOrDefault(attacker.nationId(), 1.0f);
            attackerResearchBits[attackerIndex] = attacker.researchBits();
            attackerProjectBits[attackerIndex] = attacker.projectBits();
            attackerIndexByNationId.put(attacker.nationId(), attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderList.size(); defenderIndex++) {
            DBNationSnapshot defender = defenderList.get(defenderIndex);
            defenderNationIds[defenderIndex] = defender.nationId();
            defenderScores[defenderIndex] = defender.score();
            defenderFreeDefSlots[defenderIndex] = (byte) overrides.effectiveFreeDef(defender);
            defenderAllianceIds[defenderIndex] = defender.allianceId();
            defenderActivityWeights[defenderIndex] = activityWeightsByNationId.getOrDefault(defender.nationId(), 1.0f);
            defenderResearchBits[defenderIndex] = defender.researchBits();
            defenderProjectBits[defenderIndex] = defender.projectBits();
            defenderIndexByNationId.put(defender.nationId(), defenderIndex);
        }

        int minDefenderBucket = minDefenderBucket(defenderScores);
        int[][] defenderIndexesByScoreBucket = compileDefenderScoreBuckets(defenderScores, minDefenderBucket);
        int[][] treatedDefenderIndexesByAttacker = compileTreatedDefenderIndexesByAttacker(
                attackerList,
                defenderList,
                attackerScores,
                defenderScores,
                defenderIndexesByScoreBucket,
                minDefenderBucket,
                treatyProvider
        );
        Set<Long> activePairConflicts = compileActivePairConflicts(attackerList, defenderList, attackerIndexByNationId, defenderIndexByNationId);
        Map<Integer, int[]> defenderIndexesByAllianceId = compileDefendersByAlliance(defenderList);
        int[][] relevantDefenderIndexesByAttacker = compileRelevantDefenderIndexes(attackerList, defenderIndexByNationId);

        return new CompiledScenario(
                attackerList,
                defenderList,
                attackerNationIds,
                defenderNationIds,
                attackerScores,
                defenderScores,
                attackerUnitOffsets,
                defenderUnitOffsets,
                attackerUnitsFlat,
                defenderUnitsFlat,
                attackerCityInfraOffsets,
                defenderCityInfraOffsets,
                attackerCityInfraFlat,
                defenderCityInfraFlat,
                attackerFreeOffSlots,
                defenderFreeDefSlots,
                defenderAllianceIds,
                attackerActivityWeights,
                defenderActivityWeights,
                attackerResearchBits,
                defenderResearchBits,
                attackerProjectBits,
                defenderProjectBits,
                Map.copyOf(attackerIndexByNationId),
                Map.copyOf(defenderIndexByNationId),
                treatedDefenderIndexesByAttacker,
                Set.copyOf(activePairConflicts),
                defenderIndexesByScoreBucket,
                minDefenderBucket,
                Map.copyOf(defenderIndexesByAllianceId),
                relevantDefenderIndexesByAttacker
        );
    }

    private static int[] compileUnitOffsets(List<DBNationSnapshot> snapshots) {
        int[] offsets = new int[snapshots.size() + 1];
        int width = SimUnits.PURCHASABLE_UNITS.length;
        for (int i = 0; i < snapshots.size(); i++) {
            offsets[i] = i * width;
        }
        offsets[snapshots.size()] = snapshots.size() * width;
        return offsets;
    }

    private static int[] compileUnitsFlat(List<DBNationSnapshot> snapshots, int[] unitOffsets) {
        int[] flat = new int[unitOffsets[snapshots.size()]];
        for (int nationIndex = 0; nationIndex < snapshots.size(); nationIndex++) {
            DBNationSnapshot snapshot = snapshots.get(nationIndex);
            int base = unitOffsets[nationIndex];
            for (int unitIndex = 0; unitIndex < SimUnits.PURCHASABLE_UNITS.length; unitIndex++) {
                MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
                flat[base + unitIndex] = snapshot.unit(unit);
            }
        }
        return flat;
    }

    private static int[] compileCityInfraOffsets(List<DBNationSnapshot> snapshots) {
        int[] offsets = new int[snapshots.size() + 1];
        int next = 0;
        for (int i = 0; i < snapshots.size(); i++) {
            offsets[i] = next;
            next += snapshots.get(i).cityInfra().length;
        }
        offsets[snapshots.size()] = next;
        return offsets;
    }

    private static double[] compileCityInfraFlat(List<DBNationSnapshot> snapshots, int[] infraOffsets) {
        double[] flat = new double[infraOffsets[snapshots.size()]];
        for (int nationIndex = 0; nationIndex < snapshots.size(); nationIndex++) {
            double[] cityInfra = snapshots.get(nationIndex).cityInfra();
            System.arraycopy(cityInfra, 0, flat, infraOffsets[nationIndex], cityInfra.length);
        }
        return flat;
    }

    private static int[][] compileTreatedDefenderIndexesByAttacker(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            double[] attackerScores,
            double[] defenderScores,
            int[][] defenderIndexesByScoreBucket,
            int minDefenderBucket,
            TreatyProvider treatyProvider
    ) {
        int[][] treated = new int[attackers.size()][];
        for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
            DBNationSnapshot attacker = attackers.get(attackerIndex);
            double attackerScore = attackerScores[attackerIndex];
            double minScore = attackerScore * link.locutus.discord.util.PW.WAR_RANGE_MIN_MODIFIER;
            double maxScore = attackerScore * link.locutus.discord.util.PW.WAR_RANGE_MAX_MODIFIER;
            int minBucket = CompiledScenario.scoreBucket(minScore);
            int maxBucket = CompiledScenario.scoreBucket(maxScore);
            int[] indexes = new int[Math.max(0, defenders.size())];
            int count = 0;

            for (int bucket = minBucket; bucket <= maxBucket; bucket++) {
                int bucketIndex = bucket - minDefenderBucket;
                if (bucketIndex < 0 || bucketIndex >= defenderIndexesByScoreBucket.length) {
                    continue;
                }
                int[] bucketDefenders = defenderIndexesByScoreBucket[bucketIndex];
                for (int defenderIndex : bucketDefenders) {
                    double defenderScore = defenderScores[defenderIndex];
                    if (defenderScore < minScore || defenderScore > maxScore) {
                        continue;
                    }
                    DBNationSnapshot defender = defenders.get(defenderIndex);
                    if (treatyProvider.isTreated(attacker.nationId(), defender.nationId())) {
                        indexes[count++] = defenderIndex;
                    }
                }
            }

            if (count == 0) {
                treated[attackerIndex] = new int[0];
                continue;
            }

            int[] trimmed = Arrays.copyOf(indexes, count);
            Arrays.sort(trimmed);
            treated[attackerIndex] = trimmed;
        }
        return treated;
    }

    private static Set<Long> compileActivePairConflicts(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, Integer> attackerIndexByNationId,
            Map<Integer, Integer> defenderIndexByNationId
    ) {
        Set<Long> conflicts = new LinkedHashSet<>();
        for (DBNationSnapshot attacker : attackers) {
            for (int opponentNationId : attacker.activeOpponentNationIds()) {
                if (defenderIndexByNationId.containsKey(opponentNationId)) {
                    conflicts.add(CompiledScenario.pairKey(attacker.nationId(), opponentNationId));
                }
            }
        }
        for (DBNationSnapshot defender : defenders) {
            for (int opponentNationId : defender.activeOpponentNationIds()) {
                if (attackerIndexByNationId.containsKey(opponentNationId)) {
                    conflicts.add(CompiledScenario.pairKey(opponentNationId, defender.nationId()));
                }
            }
        }
        return conflicts;
    }

    private static int minDefenderBucket(double[] defenderScores) {
        if (defenderScores.length == 0) {
            return 0;
        }
        int minBucket = Integer.MAX_VALUE;
        for (double defenderScore : defenderScores) {
            minBucket = Math.min(minBucket, CompiledScenario.scoreBucket(defenderScore));
        }
        return minBucket == Integer.MAX_VALUE ? 0 : minBucket;
    }

    private static int maxDefenderBucket(double[] defenderScores) {
        if (defenderScores.length == 0) {
            return 0;
        }
        int maxBucket = Integer.MIN_VALUE;
        for (double defenderScore : defenderScores) {
            maxBucket = Math.max(maxBucket, CompiledScenario.scoreBucket(defenderScore));
        }
        return maxBucket == Integer.MIN_VALUE ? 0 : maxBucket;
    }

    private static int[][] compileDefenderScoreBuckets(double[] defenderScores, int minDefenderBucket) {
        if (defenderScores.length == 0) {
            return new int[0][];
        }
        int maxDefenderBucket = maxDefenderBucket(defenderScores);
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new List[maxDefenderBucket - minDefenderBucket + 1];
        for (int defenderIndex = 0; defenderIndex < defenderScores.length; defenderIndex++) {
            int bucketIndex = CompiledScenario.scoreBucket(defenderScores[defenderIndex]) - minDefenderBucket;
            List<Integer> bucket = buckets[bucketIndex];
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets[bucketIndex] = bucket;
            }
            bucket.add(defenderIndex);
        }
        int[][] compiledBuckets = new int[buckets.length][];
        for (int i = 0; i < buckets.length; i++) {
            List<Integer> bucket = buckets[i];
            if (bucket == null || bucket.isEmpty()) {
                compiledBuckets[i] = new int[0];
                continue;
            }
            int[] bucketIndexes = new int[bucket.size()];
            for (int j = 0; j < bucket.size(); j++) {
                bucketIndexes[j] = bucket.get(j);
            }
            compiledBuckets[i] = bucketIndexes;
        }
        return compiledBuckets;
    }

    private static Map<Integer, int[]> compileDefendersByAlliance(List<DBNationSnapshot> defenders) {
        Map<Integer, List<Integer>> byAlliance = new HashMap<>();
        for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
            int allianceId = defenders.get(defenderIndex).allianceId();
            if (allianceId == 0) {
                continue;
            }
            byAlliance.computeIfAbsent(allianceId, ignored -> new ArrayList<>()).add(defenderIndex);
        }
        Map<Integer, int[]> compiled = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : byAlliance.entrySet()) {
            int[] indexes = new int[entry.getValue().size()];
            for (int i = 0; i < entry.getValue().size(); i++) {
                indexes[i] = entry.getValue().get(i);
            }
            compiled.put(entry.getKey(), indexes);
        }
        return compiled;
    }

    private static int[][] compileRelevantDefenderIndexes(
            List<DBNationSnapshot> attackers,
            Map<Integer, Integer> defenderIndexByNationId
    ) {
        int[][] relevant = new int[attackers.size()][];
        for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
            int[] indexes = new int[attackers.get(attackerIndex).activeOpponentNationIds().size()];
            int count = 0;
            for (int opponentNationId : attackers.get(attackerIndex).activeOpponentNationIds()) {
                Integer defenderIndex = defenderIndexByNationId.get(opponentNationId);
                if (defenderIndex != null) {
                    indexes[count++] = defenderIndex;
                }
            }
            if (count == indexes.length) {
                relevant[attackerIndex] = indexes;
            } else {
                int[] trimmed = new int[count];
                System.arraycopy(indexes, 0, trimmed, 0, count);
                relevant[attackerIndex] = trimmed;
            }
        }
        return relevant;
    }
}
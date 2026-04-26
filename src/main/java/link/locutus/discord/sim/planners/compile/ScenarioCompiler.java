package link.locutus.discord.sim.planners.compile;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        float[] attackerActivityWeights = new float[attackerList.size()];
        float[] defenderActivityWeights = new float[defenderList.size()];
        int[] attackerResearchBits = new int[attackerList.size()];
        int[] defenderResearchBits = new int[defenderList.size()];
        long[] attackerProjectBits = new long[attackerList.size()];
        long[] defenderProjectBits = new long[defenderList.size()];
        Int2IntOpenHashMap attackerIndexByNationId = newNationIndex(attackerList.size());
        Int2IntOpenHashMap defenderIndexByNationId = newNationIndex(defenderList.size());

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
            long[][] activePairConflictWordsByAttacker = compileActivePairConflicts(
                attackerList,
                defenderList,
                attackerIndexByNationId,
                defenderIndexByNationId
            );
            CompiledAllianceGroups defenderAllianceGroups = compileDefenderAllianceGroups(defenderList);
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
                attackerActivityWeights,
                defenderActivityWeights,
                attackerResearchBits,
                defenderResearchBits,
                attackerProjectBits,
                defenderProjectBits,
                attackerIndexByNationId,
                defenderIndexByNationId,
                treatedDefenderIndexesByAttacker,
                activePairConflictWordsByAttacker,
                defenderIndexesByScoreBucket,
                minDefenderBucket,
                defenderAllianceGroups.startsByDefenderIndex,
                defenderAllianceGroups.lengthsByDefenderIndex,
                defenderAllianceGroups.flatDefenderIndexes,
                relevantDefenderIndexesByAttacker
        );
    }

    private static Int2IntOpenHashMap newNationIndex(int size) {
        Int2IntOpenHashMap index = new Int2IntOpenHashMap(Math.max(16, size * 2));
        index.defaultReturnValue(-1);
        return index;
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
            next += snapshots.get(i).cityInfraCount();
        }
        offsets[snapshots.size()] = next;
        return offsets;
    }

    private static double[] compileCityInfraFlat(List<DBNationSnapshot> snapshots, int[] infraOffsets) {
        double[] flat = new double[infraOffsets[snapshots.size()]];
        for (int nationIndex = 0; nationIndex < snapshots.size(); nationIndex++) {
            snapshots.get(nationIndex).copyCityInfraInto(flat, infraOffsets[nationIndex]);
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

    private static long[][] compileActivePairConflicts(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Int2IntOpenHashMap attackerIndexByNationId,
            Int2IntOpenHashMap defenderIndexByNationId
    ) {
        int wordsPerAttacker = (defenders.size() + Long.SIZE - 1) >>> 6;
        long[][] conflicts = new long[attackers.size()][wordsPerAttacker];
        for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
            DBNationSnapshot attacker = attackers.get(attackerIndex);
            for (int opponentNationId : attacker.activeOpponentNationIds()) {
                int defenderIndex = defenderIndexByNationId.get(opponentNationId);
                if (defenderIndex >= 0) {
                    setConflict(conflicts[attackerIndex], defenderIndex);
                }
            }
        }
        for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
            DBNationSnapshot defender = defenders.get(defenderIndex);
            for (int opponentNationId : defender.activeOpponentNationIds()) {
                int attackerIndex = attackerIndexByNationId.get(opponentNationId);
                if (attackerIndex >= 0) {
                    setConflict(conflicts[attackerIndex], defenderIndex);
                }
            }
        }
        return conflicts;
    }

    private static void setConflict(long[] attackerConflicts, int defenderIndex) {
        attackerConflicts[defenderIndex >>> 6] |= 1L << (defenderIndex & 63);
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

    private static CompiledAllianceGroups compileDefenderAllianceGroups(List<DBNationSnapshot> defenders) {
        int[] starts = new int[defenders.size()];
        int[] lengths = new int[defenders.size()];
        if (defenders.isEmpty()) {
            return new CompiledAllianceGroups(starts, lengths, new int[0]);
        }
        Int2ObjectOpenHashMap<IntArrayList> byAlliance = new Int2ObjectOpenHashMap<>(Math.max(16, defenders.size()));
        for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
            int allianceId = defenders.get(defenderIndex).allianceId();
            if (allianceId == 0) {
                continue;
            }
            IntArrayList allianceDefenders = byAlliance.get(allianceId);
            if (allianceDefenders == null) {
                allianceDefenders = new IntArrayList();
                byAlliance.put(allianceId, allianceDefenders);
            }
            allianceDefenders.add(defenderIndex);
        }
        int totalMembers = 0;
        for (IntArrayList indexes : byAlliance.values()) {
            totalMembers += indexes.size();
        }
        int[] flatIndexes = new int[totalMembers];
        int next = 0;
        for (IntArrayList indexes : byAlliance.values()) {
            int start = next;
            int length = indexes.size();
            for (int i = 0; i < length; i++) {
                int defenderIndex = indexes.getInt(i);
                flatIndexes[next++] = defenderIndex;
                starts[defenderIndex] = start;
                lengths[defenderIndex] = length;
            }
        }
        return new CompiledAllianceGroups(starts, lengths, flatIndexes);
    }

    private static int[][] compileRelevantDefenderIndexes(
            List<DBNationSnapshot> attackers,
            Int2IntOpenHashMap defenderIndexByNationId
    ) {
        int[][] relevant = new int[attackers.size()][];
        for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
            int[] indexes = new int[attackers.get(attackerIndex).activeOpponentNationIds().size()];
            int count = 0;
            for (int opponentNationId : attackers.get(attackerIndex).activeOpponentNationIds()) {
                int defenderIndex = defenderIndexByNationId.get(opponentNationId);
                if (defenderIndex >= 0) {
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

    private static final class CompiledAllianceGroups {
        private final int[] startsByDefenderIndex;
        private final int[] lengthsByDefenderIndex;
        private final int[] flatDefenderIndexes;

        private CompiledAllianceGroups(int[] startsByDefenderIndex, int[] lengthsByDefenderIndex, int[] flatDefenderIndexes) {
            this.startsByDefenderIndex = startsByDefenderIndex;
            this.lengthsByDefenderIndex = lengthsByDefenderIndex;
            this.flatDefenderIndexes = flatDefenderIndexes;
        }
    }
}
package link.locutus.discord.sim.planners.compile;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.util.PW;

import java.util.ArrayList;
import java.util.List;

public final class ProjectedOpeningEvaluationScenario implements OpeningEvaluationScenario {
    private final List<DBNationSnapshot> attackers;
    private final List<DBNationSnapshot> defenders;
    private final double[] attackerScores;
    private final double[] defenderScores;
    private final long[][] activePairConflictWordsByAttacker;
    private final int[][] defenderIndexesByScoreBucket;
    private final int minDefenderBucket;
    private final int[] defenderAllianceGroupStarts;
    private final int[] defenderAllianceGroupLengths;
    private final int[] defenderAllianceFlatIndexes;

    private ProjectedOpeningEvaluationScenario(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            double[] attackerScores,
            double[] defenderScores,
            long[][] activePairConflictWordsByAttacker,
            int[][] defenderIndexesByScoreBucket,
            int minDefenderBucket,
            int[] defenderAllianceGroupStarts,
            int[] defenderAllianceGroupLengths,
            int[] defenderAllianceFlatIndexes
    ) {
        this.attackers = attackers;
        this.defenders = defenders;
        this.attackerScores = attackerScores;
        this.defenderScores = defenderScores;
        this.activePairConflictWordsByAttacker = activePairConflictWordsByAttacker;
        this.defenderIndexesByScoreBucket = defenderIndexesByScoreBucket;
        this.minDefenderBucket = minDefenderBucket;
        this.defenderAllianceGroupStarts = defenderAllianceGroupStarts;
        this.defenderAllianceGroupLengths = defenderAllianceGroupLengths;
        this.defenderAllianceFlatIndexes = defenderAllianceFlatIndexes;
    }

    public static ProjectedOpeningEvaluationScenario create(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
    ) {
        List<DBNationSnapshot> attackerList = List.copyOf(attackers);
        List<DBNationSnapshot> defenderList = List.copyOf(defenders);
        double[] attackerScores = new double[attackerList.size()];
        double[] defenderScores = new double[defenderList.size()];
        Int2IntOpenHashMap attackerIndexByNationId = newNationIndex(attackerList.size());
        Int2IntOpenHashMap defenderIndexByNationId = newNationIndex(defenderList.size());
        for (int attackerIndex = 0; attackerIndex < attackerList.size(); attackerIndex++) {
            DBNationSnapshot attacker = attackerList.get(attackerIndex);
            attackerScores[attackerIndex] = attacker.score();
            attackerIndexByNationId.put(attacker.nationId(), attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderList.size(); defenderIndex++) {
            DBNationSnapshot defender = defenderList.get(defenderIndex);
            defenderScores[defenderIndex] = defender.score();
            defenderIndexByNationId.put(defender.nationId(), defenderIndex);
        }
        int minDefenderBucket = minDefenderBucket(defenderScores);
        CompiledAllianceGroups defenderAllianceGroups = compileDefenderAllianceGroups(defenderList);
        return new ProjectedOpeningEvaluationScenario(
                attackerList,
                defenderList,
                attackerScores,
                defenderScores,
                compileActivePairConflicts(attackerList, defenderList, attackerIndexByNationId, defenderIndexByNationId),
                compileDefenderScoreBuckets(defenderScores, minDefenderBucket),
                minDefenderBucket,
            defenderAllianceGroups.startsByDefenderIndex,
            defenderAllianceGroups.lengthsByDefenderIndex,
            defenderAllianceGroups.flatDefenderIndexes
        );
    }

    @Override
    public int attackerCount() {
        return attackers.size();
    }

    @Override
    public int defenderCount() {
        return defenders.size();
    }

    @Override
    public DBNationSnapshot attacker(int attackerIndex) {
        return attackers.get(attackerIndex);
    }

    @Override
    public DBNationSnapshot defender(int defenderIndex) {
        return defenders.get(defenderIndex);
    }

    @Override
    public void forEachDefenderIndexInRange(int attackerIndex, DefenderIndexVisitor visitor) {
        double attackerScore = attackerScores[attackerIndex];
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        int minBucket = CompiledScenario.scoreBucket(minScore);
        int maxBucket = CompiledScenario.scoreBucket(maxScore);
        for (int bucket = minBucket; bucket <= maxBucket; bucket++) {
            int bucketIndex = bucket - minDefenderBucket;
            if (bucketIndex < 0 || bucketIndex >= defenderIndexesByScoreBucket.length) {
                continue;
            }
            int[] defenderIndexes = defenderIndexesByScoreBucket[bucketIndex];
            for (int defenderIndex : defenderIndexes) {
                double defenderScore = defenderScores[defenderIndex];
                if (defenderScore >= minScore && defenderScore <= maxScore) {
                    visitor.accept(defenderIndex);
                }
            }
        }
    }

    @Override
    public boolean isTreated(int attackerIndex, int defenderIndex) {
        return false;
    }

    @Override
    public boolean hasActivePairConflict(int attackerIndex, int defenderIndex) {
        long[] words = activePairConflictWordsByAttacker[attackerIndex];
        int wordIndex = defenderIndex >>> 6;
        return wordIndex < words.length
                && (words[wordIndex] & (1L << (defenderIndex & 63))) != 0L;
    }

    @Override
    public double estimateAllianceCounterRisk(int attackerIndex, int defenderIndex) {
        int sameAllianceCount = defenderAllianceGroupLengths[defenderIndex];
        if (sameAllianceCount == 0) {
            return 0.0d;
        }
        int sameAllianceOffset = defenderAllianceGroupStarts[defenderIndex];
        double attackerScore = attackerScores[attackerIndex];
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        int inRangeCount = 0;
        for (int index = 0; index < sameAllianceCount; index++) {
            int sameAllianceDefenderIndex = defenderAllianceFlatIndexes[sameAllianceOffset + index];
            double candidateScore = defenderScores[sameAllianceDefenderIndex];
            if (candidateScore >= minScore && candidateScore <= maxScore) {
                inRangeCount++;
            }
        }
        return (double) inRangeCount / sameAllianceCount;
    }

    private static Int2IntOpenHashMap newNationIndex(int size) {
        Int2IntOpenHashMap index = new Int2IntOpenHashMap(Math.max(16, size * 2));
        index.defaultReturnValue(-1);
        return index;
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
        for (int bucketIndex = 0; bucketIndex < buckets.length; bucketIndex++) {
            List<Integer> bucket = buckets[bucketIndex];
            if (bucket == null || bucket.isEmpty()) {
                compiledBuckets[bucketIndex] = new int[0];
                continue;
            }
            int[] bucketIndexes = new int[bucket.size()];
            for (int index = 0; index < bucket.size(); index++) {
                bucketIndexes[index] = bucket.get(index);
            }
            compiledBuckets[bucketIndex] = bucketIndexes;
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
            for (int index = 0; index < length; index++) {
                int defenderIndex = indexes.getInt(index);
                flatIndexes[next++] = defenderIndex;
                starts[defenderIndex] = start;
                lengths[defenderIndex] = length;
            }
        }
        return new CompiledAllianceGroups(starts, lengths, flatIndexes);
    }

    private record CompiledAllianceGroups(
            int[] startsByDefenderIndex,
            int[] lengthsByDefenderIndex,
            int[] flatDefenderIndexes
    ) {
    }
}
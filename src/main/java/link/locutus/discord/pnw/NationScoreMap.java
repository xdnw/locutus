package link.locutus.discord.pnw;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.KeyValue;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class NationScoreMap<T> {
    private final List<T>[] scoreMap;
    private final int minScore;
    private final int maxScore;

    public NationScoreMap(Collection<T> nations, Function<T, Double> scoreFunc, double minFactor, double maxFactor) {
        double minDble = Integer.MAX_VALUE;
        double maxDble = Integer.MIN_VALUE;
        for (T nation : nations) {
            double score = scoreFunc.apply(nation);
            double scoreMin = score * minFactor;
            double scoreMax = score * maxFactor;
            if (scoreMin < minDble) minDble = scoreMin;
            if (scoreMax > maxDble) maxDble = scoreMax;
        }
        this.minScore = (int) Math.round(minDble);
        this.maxScore = (int) Math.round(maxDble);
        scoreMap = new List[maxScore - minScore + 1];
        for (T nation : nations) {
            double score = scoreFunc.apply(nation);
            int scoreMin = (int) Math.round(score * minFactor);
            int scoreMax = (int) Math.round(score * maxFactor);
            for (int i = scoreMin; i <= scoreMax; i++) {
                int j = i - minScore;
                List<T> list = scoreMap[j];
                if (list == null) {
                    scoreMap[j] = list = new LinkedList<>();
                }
                list.add(nation);
            }
        }
    }

    public List<T> get(int score) {
        if (score < minScore) return Collections.emptyList();
        if (score > maxScore) return Collections.emptyList();
        List<T> list = scoreMap[score - minScore];
        if (list == null) Collections.emptyList();
        return list;
    }

    public static Map.Entry<Double, Double> getMinMaxScore(Collection<DBNation> nations, double minFactor, double maxFactor) {
        double minDble = Integer.MAX_VALUE;
        double maxDble = Integer.MIN_VALUE;
        for (DBNation nation : nations) {
            double score = nation.getScore();
            double scoreMin = score * minFactor;
            double scoreMax = score * maxFactor;
            if (scoreMin < minDble) minDble = scoreMin;
            if (scoreMax > maxDble) maxDble = scoreMax;
        }
        return KeyValue.of(minDble, maxDble);
    }

    public int getMaxScore() {
        return maxScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public BiFunction<Integer, Integer, Integer> getSummedFunction(Predicate<T> filter) {
        long[] summedCount = new long[scoreMap.length];
        long sum = 0;
        for (int i = 0; i < scoreMap.length; i++) {
            List<T> list = scoreMap[i];
            int size;
            if (list == null || list.isEmpty()) {
                size = 0;
            } else if (filter == null){
                size = list.size();
            } else {
                size = (int) list.stream().filter(filter).count();
            }
            sum += size;
            summedCount[i] = sum;
        }
        // return sum (inclusive min, inclusive max)
        return (min, max) -> {
            if (min < minScore) min = minScore;
            if (max > maxScore) max = maxScore;
            if (min > max) return 0;
            int minIdx = min - minScore;
            int maxIdx = max - minScore;
            long sumMin = minIdx == 0 ? 0 : summedCount[minIdx - 1];
            long sumMax = summedCount[maxIdx];
            return Math.toIntExact(sumMax - sumMin);
        };
    }
}


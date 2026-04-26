package link.locutus.discord.sim;

import link.locutus.discord.util.PW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * World-owned score index for war-range candidate lookup.
 */
final class DeclareRangeIndex {
    private final NavigableMap<Double, LinkedHashSet<Integer>> nationIdsByScore = new TreeMap<>();
    private final Map<Integer, Double> scoreByNationId = new HashMap<>();
    private final Map<Integer, Integer> insertionOrderByNationId = new HashMap<>();
    private int nextInsertionOrder;

    void onNationAdded(int nationId, double score) {
        if (scoreByNationId.containsKey(nationId)) {
            throw new IllegalArgumentException("Nation already indexed: " + nationId);
        }
        scoreByNationId.put(nationId, score);
        insertionOrderByNationId.put(nationId, nextInsertionOrder++);
        nationIdsByScore.computeIfAbsent(score, ignored -> new LinkedHashSet<>()).add(nationId);
    }

    void onNationScoreChanged(int nationId, double previousScore, double currentScore) {
        Double indexedScore = scoreByNationId.get(nationId);
        if (indexedScore == null) {
            throw new IllegalArgumentException("Nation not indexed: " + nationId);
        }
        if (Double.compare(indexedScore, previousScore) != 0) {
            previousScore = indexedScore;
        }
        if (Double.compare(previousScore, currentScore) == 0) {
            return;
        }
        LinkedHashSet<Integer> previousBucket = nationIdsByScore.get(previousScore);
        if (previousBucket != null) {
            previousBucket.remove(nationId);
            if (previousBucket.isEmpty()) {
                nationIdsByScore.remove(previousScore);
            }
        }
        nationIdsByScore.computeIfAbsent(currentScore, ignored -> new LinkedHashSet<>()).add(nationId);
        scoreByNationId.put(nationId, currentScore);
    }

    List<Integer> nationIdsInWarRange(double attackerScore, int excludeNationId) {
        double min = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double max = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        if (Double.compare(min, max) > 0) {
            return List.of();
        }
        NavigableMap<Double, LinkedHashSet<Integer>> candidatesByScore = nationIdsByScore.subMap(min, true, max, true);
        if (candidatesByScore.isEmpty()) {
            return List.of();
        }
        ArrayList<Integer> candidateNationIds = new ArrayList<>();
        for (LinkedHashSet<Integer> idsAtScore : candidatesByScore.values()) {
            for (int nationId : idsAtScore) {
                if (nationId != excludeNationId) {
                    candidateNationIds.add(nationId);
                }
            }
        }
        candidateNationIds.sort(Comparator.comparingInt(insertionOrderByNationId::get));
        return List.copyOf(candidateNationIds);
    }

    DeclareRangeIndex deepCopy() {
        DeclareRangeIndex copy = new DeclareRangeIndex();
        for (Map.Entry<Double, LinkedHashSet<Integer>> entry : nationIdsByScore.entrySet()) {
            copy.nationIdsByScore.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        copy.scoreByNationId.putAll(scoreByNationId);
        copy.insertionOrderByNationId.putAll(insertionOrderByNationId);
        copy.nextInsertionOrder = nextInsertionOrder;
        return copy;
    }
}
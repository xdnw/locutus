package link.locutus.discord.sim;

import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.doubles.Double2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import link.locutus.discord.util.PW;

import java.util.List;

/**
 * World-owned score index for war-range candidate lookup.
 */
final class DeclareRangeIndex {
    private final Double2ObjectRBTreeMap<IntLinkedOpenHashSet> nationIdsByScore = new Double2ObjectRBTreeMap<>();
    private final Int2DoubleOpenHashMap scoreByNationId = new Int2DoubleOpenHashMap();
    private final Int2IntOpenHashMap insertionOrderByNationId = new Int2IntOpenHashMap();
    private int nextInsertionOrder;

    void onNationAdded(int nationId, double score) {
        if (scoreByNationId.containsKey(nationId)) {
            throw new IllegalArgumentException("Nation already indexed: " + nationId);
        }
        scoreByNationId.put(nationId, score);
        insertionOrderByNationId.put(nationId, nextInsertionOrder++);
        nationIdsByScore.computeIfAbsent(score, ignored -> new IntLinkedOpenHashSet()).add(nationId);
    }

    void onNationScoreChanged(int nationId, double previousScore, double currentScore) {
        if (!scoreByNationId.containsKey(nationId)) {
            throw new IllegalArgumentException("Nation not indexed: " + nationId);
        }
        double indexedScore = scoreByNationId.get(nationId);
        if (Double.compare(indexedScore, previousScore) != 0) {
            previousScore = indexedScore;
        }
        if (Double.compare(previousScore, currentScore) == 0) {
            return;
        }
        IntLinkedOpenHashSet previousBucket = nationIdsByScore.get(previousScore);
        if (previousBucket != null) {
            previousBucket.remove(nationId);
            if (previousBucket.isEmpty()) {
                nationIdsByScore.remove(previousScore);
            }
        }
        nationIdsByScore.computeIfAbsent(currentScore, ignored -> new IntLinkedOpenHashSet()).add(nationId);
        scoreByNationId.put(nationId, currentScore);
    }

    List<Integer> nationIdsInWarRange(double attackerScore, int excludeNationId) {
        double min = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double max = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        if (Double.compare(min, max) > 0) {
            return List.of();
        }
        Double2ObjectSortedMap<IntLinkedOpenHashSet> candidatesByScore = nationIdsByScore.subMap(min, Math.nextUp(max));
        if (candidatesByScore.isEmpty()) {
            return List.of();
        }
        IntArrayList candidateNationIds = new IntArrayList();
        for (IntLinkedOpenHashSet idsAtScore : candidatesByScore.values()) {
            for (int nationId : idsAtScore) {
                if (nationId != excludeNationId) {
                    candidateNationIds.add(nationId);
                }
            }
        }
        candidateNationIds.sort((left, right) -> Integer.compare(insertionOrderByNationId.get(left), insertionOrderByNationId.get(right)));
        IntList orderedNationIds = candidateNationIds;
        return IntLists.unmodifiable(orderedNationIds);
    }

    DeclareRangeIndex deepCopy() {
        DeclareRangeIndex copy = new DeclareRangeIndex();
        for (Double2ObjectMap.Entry<IntLinkedOpenHashSet> entry : nationIdsByScore.double2ObjectEntrySet()) {
            copy.nationIdsByScore.put(entry.getDoubleKey(), new IntLinkedOpenHashSet(entry.getValue()));
        }
        copy.scoreByNationId.putAll(scoreByNationId);
        copy.insertionOrderByNationId.putAll(insertionOrderByNationId);
        copy.nextInsertionOrder = nextInsertionOrder;
        return copy;
    }
}
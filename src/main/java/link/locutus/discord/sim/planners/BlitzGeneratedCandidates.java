package link.locutus.discord.sim.planners;

import java.util.List;
import java.util.Map;

record BlitzGeneratedCandidates(
    CandidateEdgeTable edgeTable,
    Map<Integer, List<Integer>> candidateDefendersByAttacker,
    Map<Long, Float> edgeScoresByPair,
    Map<Long, Integer> initialWarTypeOrdinalsByPair,
    Map<Long, Integer> initialAttackTypeOrdinalsByPair
) {
    boolean containsPair(int attackerNationId, int defenderNationId) {
        return edgeScoresByPair.containsKey(pairKey(attackerNationId, defenderNationId));
    }

    float edgeScore(int attackerNationId, int defenderNationId) {
        return edgeScoresByPair.getOrDefault(pairKey(attackerNationId, defenderNationId), Float.NEGATIVE_INFINITY);
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }
}
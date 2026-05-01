package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LongHorizonFeedbackSearch {
    private static final int MAX_SELECTIVE_RELIEF_VARIANTS = 6;
    private static final int MAX_FEEDBACK_VARIANTS = 4;
    private static final int MAX_FIXED_POINT_ITERATIONS = 4;
    /**
     * Floor multiplier applied to over-countered attackers' outgoing edges so a near-zero projected
     * mid-horizon strength ratio still leaves the edge with a small but non-zero score.
     */
    private static final float OVERCOUNTER_PROJECTED_FLOOR = 0.05f;

    private LongHorizonFeedbackSearch() {
    }

    static List<LongHorizonAssignmentOptimizer.Candidate> selectiveAttackerReliefCandidates(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.Candidate seed,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters
    ) {
        int[] fixedCounts = fixedAttackerCounts(fixedEdges, attackerNationIds);
        List<Integer> reliefOrder = reliefOrder(seed.attackerCounts(), fixedCounts, terminalProjection, realizedCounters);
        if (reliefOrder.isEmpty()) {
            return List.of();
        }

        int[] adjustedCaps = attackerCaps.clone();
        for (int attackerIndex = 0; attackerIndex < adjustedCaps.length; attackerIndex++) {
            adjustedCaps[attackerIndex] = Math.min(
                    adjustedCaps[attackerIndex],
                    Math.max(fixedCounts[attackerIndex], seed.attackerCounts()[attackerIndex])
            );
        }
        List<LongHorizonAssignmentOptimizer.Candidate> candidates =
                new ArrayList<>(Math.min(MAX_SELECTIVE_RELIEF_VARIANTS, reliefOrder.size()));
        int orderIndex = 0;
        while (candidates.size() < MAX_SELECTIVE_RELIEF_VARIANTS && !reliefOrder.isEmpty()) {
            int attackerIndex = reliefOrder.get(orderIndex % reliefOrder.size());
            int lowerBound = Math.max(1, fixedCounts[attackerIndex]);
            if (adjustedCaps[attackerIndex] <= lowerBound) {
                reliefOrder.remove(orderIndex % reliefOrder.size());
                orderIndex = 0;
                continue;
            }
            adjustedCaps[attackerIndex]--;
            candidates.add(LongHorizonAssignmentOptimizer.solveWithAttackerCaps(
                    baseEdges,
                    scenario,
                    adjustedCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    horizonTurns
            ));
            orderIndex++;
        }
        return candidates;
    }

    static LongHorizonAssignmentOptimizer.Candidate recedingFixedPointFeedback(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.Candidate seed,
            int[] seedRealizedCounters,
            LongHorizonControlProjection seedProjection,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext,
            LongHorizonAssignmentOptimizer.EvaluationCache evaluationCache
    ) {
        if (seedRealizedCounters == null || seedRealizedCounters.length == 0) {
            return seed;
        }
        int[] fixedCounts = fixedAttackerCounts(fixedEdges, attackerNationIds);
        int[] adjustedCaps = attackerCaps.clone();
        CandidateEdgeTable currentEdges = CandidateEdgeTable.copyOf(baseEdges);
        LongHorizonAssignmentOptimizer.Candidate best = seed;
        LongHorizonAssignmentOptimizer.Candidate currentSeed = seed;
        int[] currentRealized = seedRealizedCounters.clone();
        int variantsRemaining = MAX_FEEDBACK_VARIANTS;
        for (int iteration = 0; iteration < MAX_FIXED_POINT_ITERATIONS && variantsRemaining > 0; iteration++) {
            List<Integer> overCountered = overCounteredAttackers(
                    currentRealized,
                    currentSeed.attackerCounts(),
                    fixedCounts
            );
            if (overCountered.isEmpty()) {
                break;
            }
            LongHorizonForwardProjection.MidHorizonSnapshot snapshot = seedProjection.snapshotMidHorizonState(
                    currentSeed.edgeAssigned(),
                    currentSeed.attackerCounts(),
                    currentSeed.defenderCounts()
            );
            boolean adjusted = false;
            for (int attackerIndex : overCountered) {
                int lowerBound = fixedCounts[attackerIndex];
                if (adjustedCaps[attackerIndex] > lowerBound) {
                    adjustedCaps[attackerIndex]--;
                    adjusted = true;
                }
                rebuildAttackerEdgesFromMidHorizon(currentEdges, attackerIndex, snapshot);
                adjusted = true;
            }
            if (!adjusted) {
                break;
            }
            variantsRemaining--;

            LongHorizonControlProjection iterationProjection = LongHorizonControlProjection.create(
                    currentEdges,
                    scenario,
                    adjustedCaps,
                    defenderCaps,
                    horizonTurns,
                    LongHorizonAssignmentOptimizer.horizonFactor(horizonTurns)
            );
            LongHorizonMarginalFlowSolver.Result iterationResult = LongHorizonMarginalFlowSolver.solve(
                    currentEdges,
                    iterationProjection,
                    scenario.attackerCount(),
                    scenario.defenderCount(),
                    adjustedCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges
            );
            double iterationScore = seedProjection.assignmentScoreDense(
                    iterationResult.edgeAssigned(),
                    iterationResult.attackerCounts(),
                    iterationResult.defenderCounts()
            );
            LongHorizonAssignmentOptimizer.Candidate iterationCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                    iterationResult.assignment(),
                    iterationResult.edgeAssigned(),
                    iterationResult.attackerCounts(),
                    iterationResult.defenderCounts(),
                    iterationScore
            );
            double iterationObjective = LongHorizonAssignmentOptimizer.scoreCandidate(
                    iterationCandidate,
                    seedProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
            );
            int[] nextRealized = LongHorizonAssignmentOptimizer.realizedCountersFor(
                    iterationCandidate,
                    seedProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
            );
            boolean improvement = iterationObjective > LongHorizonAssignmentOptimizer.scoreCandidate(
                    best,
                    seedProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
            ) + LongHorizonAssignmentOptimizer.EPSILON;
            if (improvement) {
                best = iterationCandidate;
            }
            if (!realizedChanged(currentRealized, nextRealized) && !improvement) {
                break;
            }
            currentRealized = nextRealized;
            currentSeed = iterationCandidate;
        }
        return best;
    }

    private static int[] fixedAttackerCounts(List<BlitzFixedEdge> fixedEdges, int[] attackerNationIds) {
        int[] counts = new int[attackerNationIds.length];
        if (fixedEdges.isEmpty()) {
            return counts;
        }
        Map<Integer, Integer> attackerIndexByNationId = new LinkedHashMap<>(Math.max(16, attackerNationIds.length * 2));
        for (int attackerIndex = 0; attackerIndex < attackerNationIds.length; attackerIndex++) {
            attackerIndexByNationId.put(attackerNationIds[attackerIndex], attackerIndex);
        }
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            Integer attackerIndex = attackerIndexByNationId.get(fixedEdge.attackerNationId());
            if (attackerIndex != null) {
                counts[attackerIndex]++;
            }
        }
        return counts;
    }

    private static List<Integer> reliefOrder(
            int[] attackerCounts,
            int[] fixedCounts,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters
    ) {
        List<Integer> order = new ArrayList<>();
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] <= Math.max(1, fixedCounts[attackerIndex])) {
                continue;
            }
            order.add(attackerIndex);
        }
        order.sort((left, right) -> {
            double leftPriority = reliefPriority(left, attackerCounts, terminalProjection, realizedCounters);
            double rightPriority = reliefPriority(right, attackerCounts, terminalProjection, realizedCounters);
            int priorityOrder = Double.compare(rightPriority, leftPriority);
            if (priorityOrder != 0) {
                return priorityOrder;
            }
            int countOrder = Integer.compare(attackerCounts[right], attackerCounts[left]);
            if (countOrder != 0) {
                return countOrder;
            }
            return Integer.compare(left, right);
        });
        return order;
    }

    private static double reliefPriority(
            int attackerIndex,
            int[] attackerCounts,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters
    ) {
        int assignedBefore = Math.max(0, attackerCounts[attackerIndex] - 1);
        double counterPenalty = -terminalProjection.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore);
        double realized = realizedCounters != null && attackerIndex < realizedCounters.length
                ? Math.max(0, realizedCounters[attackerIndex])
                : 0d;
        return Math.max(0d, counterPenalty) + realized;
    }

    private static List<Integer> overCounteredAttackers(int[] realizedCounters, int[] attackerCounts, int[] fixedCounts) {
        List<Integer> overCountered = new ArrayList<>();
        for (int attackerIndex = 0; attackerIndex < realizedCounters.length; attackerIndex++) {
            if (realizedCounters[attackerIndex] < LongHorizonAssignmentOptimizer.FEEDBACK_OVERCOUNTER_THRESHOLD) {
                continue;
            }
            int currentCount = attackerCounts[attackerIndex];
            if (currentCount <= fixedCounts[attackerIndex]) {
                continue;
            }
            overCountered.add(attackerIndex);
        }
        overCountered.sort((left, right) -> {
            int countOrder = Integer.compare(realizedCounters[right], realizedCounters[left]);
            if (countOrder != 0) {
                return countOrder;
            }
            int seedOrder = Integer.compare(attackerCounts[right], attackerCounts[left]);
            if (seedOrder != 0) {
                return seedOrder;
            }
            return Integer.compare(left, right);
        });
        return overCountered;
    }

    private static void rebuildAttackerEdgesFromMidHorizon(
            CandidateEdgeTable edges,
            int attackerIndex,
            LongHorizonForwardProjection.MidHorizonSnapshot snapshot
    ) {
        double rawFactor = snapshot.attackerEdgeFactor(attackerIndex);
        float factor = (float) Math.max(OVERCOUNTER_PROJECTED_FLOOR, Math.min(1d, rawFactor));
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (edges.attackerIndex(edgeIndex) != attackerIndex) {
                continue;
            }
            edges.rescaleEdgeFromProjectedState(edgeIndex, factor);
        }
    }

    private static boolean realizedChanged(int[] previous, int[] next) {
        if (previous == null || next == null || previous.length != next.length) {
            return true;
        }
        for (int index = 0; index < previous.length; index++) {
            if (previous[index] != next[index]) {
                return true;
            }
        }
        return false;
    }
}
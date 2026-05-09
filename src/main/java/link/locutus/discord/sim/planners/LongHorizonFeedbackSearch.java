package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.ArrayList;
import java.util.List;

final class LongHorizonFeedbackSearch {
    static final int OVERCOUNTER_THRESHOLD = 2;

    private static final int MAX_SELECTIVE_RELIEF_VARIANTS = 12;
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
                int[] realizedCounters,
                SidePlannerSettings attackerPlannerSettings
    ) {
        int[] fixedCounts = fixedAttackerCounts(fixedEdges, attackerNationIds);
        List<Integer> reliefOrder = reliefOrder(seed.attackerCounts(), fixedCounts, terminalProjection, realizedCounters);
        if (reliefOrder.isEmpty()) {
            return List.of();
        }
        int[] reliefBudgets = reliefBudgets(seed.attackerCounts(), fixedCounts, realizedCounters);
        int variantLimit = reliefVariantLimit(reliefBudgets);
        if (variantLimit <= 0) {
            return List.of();
        }

        int[] adjustedCaps = attackerCaps.clone();
        List<LongHorizonAssignmentOptimizer.Candidate> candidates =
                new ArrayList<>(variantLimit);
        for (int attackerIndex : reliefOrder) {
            int lowerBound = fixedCounts[attackerIndex];
            int remainingBudget = reliefBudgets[attackerIndex];
            while (remainingBudget > 0 && candidates.size() < variantLimit) {
                if (adjustedCaps[attackerIndex] <= lowerBound) {
                    break;
                }
                adjustedCaps[attackerIndex]--;
                remainingBudget--;
                reliefBudgets[attackerIndex] = remainingBudget;
                LongHorizonControlProjection reliefProjection = terminalProjection.sameSettingsFullVariant(
                        baseEdges,
                        adjustedCaps,
                        defenderCaps,
                        attackerStrengthRanks
                );
                LongHorizonMarginalFlowSolver.Result reliefResult = LongHorizonMarginalFlowSolver.solve(
                        baseEdges,
                    reliefProjection,
                    scenario.attackerCount(),
                    scenario.defenderCount(),
                        adjustedCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                    fixedEdges
                );
                double reliefScore = reliefProjection.assignmentScoreDense(
                    reliefResult.edgeAssigned(),
                    reliefResult.attackerCounts(),
                    reliefResult.defenderCounts()
                );
                candidates.add(new LongHorizonAssignmentOptimizer.Candidate(
                    reliefResult.assignment(),
                    reliefResult.edgeAssigned(),
                    reliefResult.attackerCounts(),
                    reliefResult.defenderCounts(),
                    reliefScore
                ));
            }
            if (candidates.size() >= variantLimit) {
                continue;
            }
        }
        return candidates;
    }

    private static int[] reliefBudgets(int[] attackerCounts, int[] fixedCounts, int[] realizedCounters) {
        int[] budgets = new int[attackerCounts.length];
        if (realizedCounters == null) {
            return budgets;
        }
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            int lowerBound = fixedCounts[attackerIndex];
            int availableRelief = Math.max(0, attackerCounts[attackerIndex] - lowerBound);
            int overCounterExcess = attackerIndex < realizedCounters.length
                    ? Math.max(0, realizedCounters[attackerIndex] - OVERCOUNTER_THRESHOLD + 1)
                    : 0;
            budgets[attackerIndex] = Math.min(availableRelief, overCounterExcess);
        }
        return budgets;
    }

    private static int reliefVariantLimit(int[] reliefBudgets) {
        int totalBudget = 0;
        for (int budget : reliefBudgets) {
            totalBudget += Math.max(0, budget);
        }
        return Math.min(MAX_SELECTIVE_RELIEF_VARIANTS, totalBudget);
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
            LongHorizonControlProjection seedProjection,
                LongHorizonCandidateEvaluator evaluator,
                SidePlannerSettings attackerPlannerSettings
    ) {
        LongHorizonForwardProjection.ProjectedFeedbackEvaluation currentFeedback = evaluator.feedbackEvaluation(seed, seedProjection);
        int[] currentRealized = currentFeedback.projectedEvaluation().realizedCounterIncidence().clone();
        if (currentRealized.length == 0) {
            return seed;
        }
        int[] fixedCounts = fixedAttackerCounts(fixedEdges, attackerNationIds);
        int[] adjustedCaps = attackerCaps.clone();
        CandidateEdgeTable currentEdges = CandidateEdgeTable.copyOf(baseEdges);
        LongHorizonAssignmentOptimizer.Candidate best = seed;
        LongHorizonAssignmentOptimizer.Candidate currentSeed = seed;
        double bestObjective = evaluator.score(best, currentFeedback.projectedEvaluation());
        int bestOverCountered = overCounteredAttackers(currentRealized, best.attackerCounts(), fixedCounts).size();
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
            LongHorizonForwardProjection.MidHorizonSnapshot snapshot = currentFeedback.midHorizonSnapshot();
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

            LongHorizonControlProjection iterationProjection = seedProjection.sameSettingsFullVariant(
                    currentEdges,
                    adjustedCaps,
                    defenderCaps,
                    attackerStrengthRanks
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
                double iterationScore = iterationProjection.assignmentScoreDense(
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
                LongHorizonForwardProjection.ProjectedFeedbackEvaluation iterationFeedback = evaluator.feedbackEvaluation(
                    iterationCandidate,
                    iterationProjection
                );
                double iterationObjective = evaluator.score(
                    iterationCandidate,
                    iterationFeedback.projectedEvaluation()
                );
                int[] nextRealized = iterationFeedback.projectedEvaluation().realizedCounterIncidence();
            int nextOverCountered = overCounteredAttackers(nextRealized, iterationCandidate.attackerCounts(), fixedCounts).size();
            boolean improvement = iterationObjective > bestObjective + LongHorizonAssignmentOptimizer.EPSILON;
            boolean counterPressureTieBreak = !improvement
                    && iterationObjective >= bestObjective - LongHorizonAssignmentOptimizer.EPSILON
                    && nextOverCountered < bestOverCountered;
            if (improvement) {
                best = iterationCandidate;
                bestObjective = iterationObjective;
                bestOverCountered = nextOverCountered;
            } else if (counterPressureTieBreak) {
                best = iterationCandidate;
                bestObjective = iterationObjective;
                bestOverCountered = nextOverCountered;
                improvement = true;
            }
            if (!realizedChanged(currentRealized, nextRealized) && !improvement) {
                break;
            }
            currentRealized = nextRealized;
            currentSeed = iterationCandidate;
                currentFeedback = iterationFeedback;
        }
        return best;
    }

    private static int[] fixedAttackerCounts(List<BlitzFixedEdge> fixedEdges, int[] attackerNationIds) {
        int[] counts = new int[attackerNationIds.length];
        if (fixedEdges.isEmpty()) {
            return counts;
        }
        Int2IntOpenHashMap attackerIndexByNationId = new Int2IntOpenHashMap(Math.max(16, attackerNationIds.length * 2));
        attackerIndexByNationId.defaultReturnValue(-1);
        for (int attackerIndex = 0; attackerIndex < attackerNationIds.length; attackerIndex++) {
            attackerIndexByNationId.put(attackerNationIds[attackerIndex], attackerIndex);
        }
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            int attackerIndex = attackerIndexByNationId.get(fixedEdge.attackerNationId());
            if (attackerIndex >= 0) {
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
        List<Integer> order = new IntArrayList();
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] <= fixedCounts[attackerIndex]) {
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
        List<Integer> overCountered = new IntArrayList();
        for (int attackerIndex = 0; attackerIndex < realizedCounters.length; attackerIndex++) {
            if (realizedCounters[attackerIndex] < OVERCOUNTER_THRESHOLD) {
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

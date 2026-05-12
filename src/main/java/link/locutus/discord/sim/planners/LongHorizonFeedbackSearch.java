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
            int[] fixedCounts,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.Candidate seed,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters,
            SidePlannerSettings attackerPlannerSettings,
                LongHorizonMarginalFlowSolver.StaticSolveInputs marginalFlowStaticInputs,
                LongHorizonMarginalFlowSolver.GraphBuildBuffers marginalFlowGraphBuffers
    ) {
        IntArrayList reliefOrder = reliefOrder(seed.attackerCounts(), fixedCounts, terminalProjection, realizedCounters);
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
        boolean[] warmStartEdgeAssigned = seed.edgeAssigned();
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
                LongHorizonControlProjection reliefProjection = terminalProjection.sameSettingsScorerOnlyVariant(
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
                        fixedEdges,
                        marginalFlowStaticInputs,
                        marginalFlowGraphBuffers,
                        warmStartEdgeAssigned
                );
                double reliefScore = reliefProjection.assignmentScoreDense(
                    reliefResult.edgeAssigned(),
                    reliefResult.attackerCounts(),
                    reliefResult.defenderCounts()
                );
                candidates.add(new LongHorizonAssignmentOptimizer.Candidate(reliefResult, reliefScore));
                warmStartEdgeAssigned = reliefResult.edgeAssigned();
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
            int[] fixedCounts,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.Candidate seed,
            LongHorizonControlProjection seedProjection,
            LongHorizonCandidateEvaluator evaluator,
            SidePlannerSettings attackerPlannerSettings,
                LongHorizonMarginalFlowSolver.StaticSolveInputs marginalFlowStaticInputs,
                LongHorizonMarginalFlowSolver.GraphBuildBuffers marginalFlowGraphBuffers
    ) {
        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation currentFeedback = null;
        LongHorizonControlProjection currentProjection = seedProjection;
        int[] currentRealized = evaluator.realizedCounters(seed, seedProjection);
        if (currentRealized.length == 0) {
            return seed;
        }
        int[] adjustedCaps = attackerCaps.clone();
        CandidateEdgeTable currentEdges = CandidateEdgeTable.copyOf(baseEdges);
        LongHorizonAssignmentOptimizer.Candidate best = seed;
        LongHorizonAssignmentOptimizer.Candidate currentSeed = seed;
        double bestObjective = evaluator.score(best, seedProjection);
        int bestOverCountered = countOverCounteredAttackers(currentRealized, best.attackerCounts(), fixedCounts);
        int variantsRemaining = MAX_FEEDBACK_VARIANTS;
        boolean[] warmStartEdgeAssigned = seed.edgeAssigned();
        IntArrayList overCounteredScratch = new IntArrayList(Math.max(4, currentRealized.length));
        for (int iteration = 0; iteration < MAX_FIXED_POINT_ITERATIONS && variantsRemaining > 0; iteration++) {
            IntArrayList overCountered = overCounteredAttackers(
                overCounteredScratch,
                currentRealized,
                currentSeed.attackerCounts(),
                fixedCounts
            );
            if (overCountered.isEmpty()) {
                break;
            }
            if (currentFeedback == null) {
                currentFeedback = evaluator.attackerFeedbackEvaluation(currentSeed, currentProjection);
            }
            LongHorizonForwardProjection.AttackerMidHorizonSnapshot snapshot = currentFeedback.attackerMidHorizonSnapshot();
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

                LongHorizonControlProjection iterationSolveProjection = seedProjection.sameSettingsScorerOnlyVariant(
                    currentEdges,
                    adjustedCaps,
                    defenderCaps,
                    attackerStrengthRanks
            );
            LongHorizonMarginalFlowSolver.Result iterationResult = LongHorizonMarginalFlowSolver.solve(
                    currentEdges,
                    iterationSolveProjection,
                    scenario.attackerCount(),
                    scenario.defenderCount(),
                    adjustedCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                        fixedEdges,
                        marginalFlowStaticInputs,
                    marginalFlowGraphBuffers,
                    warmStartEdgeAssigned
            );
                double iterationScore = iterationSolveProjection.assignmentScoreDense(
                    iterationResult.edgeAssigned(),
                    iterationResult.attackerCounts(),
                    iterationResult.defenderCounts()
            );
            LongHorizonAssignmentOptimizer.Candidate iterationCandidate =
                    new LongHorizonAssignmentOptimizer.Candidate(iterationResult, iterationScore);
                warmStartEdgeAssigned = iterationResult.edgeAssigned();
                LongHorizonControlProjection iterationProjection =
                        seedProjection.sameSettingsFullVariantReusingScorer(iterationSolveProjection);
            boolean canContinueFeedback = iteration + 1 < MAX_FIXED_POINT_ITERATIONS && variantsRemaining > 0;
            LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation iterationFeedback = null;
            double iterationObjective;
            int[] nextRealized;
            if (canContinueFeedback) {
                iterationFeedback = evaluator.attackerFeedbackEvaluation(
                    iterationCandidate,
                    iterationProjection
                );
                iterationObjective = evaluator.score(
                    iterationCandidate,
                    iterationFeedback.projectedEvaluation()
                );
                nextRealized = iterationFeedback.projectedEvaluation().realizedCounterIncidence();
            } else {
                iterationObjective = evaluator.score(iterationCandidate, iterationProjection);
                nextRealized = evaluator.realizedCounters(iterationCandidate, iterationProjection);
            }
            int nextOverCountered = countOverCounteredAttackers(nextRealized, iterationCandidate.attackerCounts(), fixedCounts);
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
            if (!canContinueFeedback) {
                break;
            }
            if (!realizedChanged(currentRealized, nextRealized) && !improvement) {
                break;
            }
            currentRealized = nextRealized;
            currentSeed = iterationCandidate;
            currentProjection = iterationProjection;
            currentFeedback = iterationFeedback;
        }
        return best;
    }

    static int[] fixedAttackerCounts(List<BlitzFixedEdge> fixedEdges, int[] attackerNationIds) {
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

    private static IntArrayList reliefOrder(
            int[] attackerCounts,
            int[] fixedCounts,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters
    ) {
        IntArrayList order = new IntArrayList();
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] <= fixedCounts[attackerIndex]) {
                continue;
            }
            order.add(attackerIndex);
        }
        sortReliefOrder(order, attackerCounts, terminalProjection, realizedCounters);
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

    private static IntArrayList overCounteredAttackers(
            IntArrayList overCountered,
            int[] realizedCounters,
            int[] attackerCounts,
            int[] fixedCounts
    ) {
        overCountered.clear();
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
        sortOverCounteredAttackers(overCountered, realizedCounters, attackerCounts);
        return overCountered;
    }

    private static int countOverCounteredAttackers(int[] realizedCounters, int[] attackerCounts, int[] fixedCounts) {
        int count = 0;
        for (int attackerIndex = 0; attackerIndex < realizedCounters.length; attackerIndex++) {
            if (realizedCounters[attackerIndex] < OVERCOUNTER_THRESHOLD) {
                continue;
            }
            if (attackerCounts[attackerIndex] <= fixedCounts[attackerIndex]) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static void sortReliefOrder(
            IntArrayList order,
            int[] attackerCounts,
            LongHorizonControlProjection terminalProjection,
            int[] realizedCounters
    ) {
        int size = order.size();
        if (size < 2) {
            return;
        }
        double[] priorities = new double[size];
        for (int index = 0; index < size; index++) {
            priorities[index] = reliefPriority(order.getInt(index), attackerCounts, terminalProjection, realizedCounters);
        }
        for (int index = 1; index < size; index++) {
            int attackerIndex = order.getInt(index);
            double priority = priorities[index];
            int cursor = index;
            while (cursor > 0 && compareReliefOrder(attackerIndex, priority, order.getInt(cursor - 1), priorities[cursor - 1], attackerCounts) < 0) {
                order.set(cursor, order.getInt(cursor - 1));
                priorities[cursor] = priorities[cursor - 1];
                cursor--;
            }
            order.set(cursor, attackerIndex);
            priorities[cursor] = priority;
        }
    }

    private static int compareReliefOrder(
            int leftAttackerIndex,
            double leftPriority,
            int rightAttackerIndex,
            double rightPriority,
            int[] attackerCounts
    ) {
        int priorityOrder = Double.compare(rightPriority, leftPriority);
        if (priorityOrder != 0) {
            return priorityOrder;
        }
        int countOrder = Integer.compare(attackerCounts[rightAttackerIndex], attackerCounts[leftAttackerIndex]);
        if (countOrder != 0) {
            return countOrder;
        }
        return Integer.compare(leftAttackerIndex, rightAttackerIndex);
    }

    private static void sortOverCounteredAttackers(IntArrayList overCountered, int[] realizedCounters, int[] attackerCounts) {
        int size = overCountered.size();
        if (size < 2) {
            return;
        }
        for (int index = 1; index < size; index++) {
            int attackerIndex = overCountered.getInt(index);
            int cursor = index;
            while (cursor > 0 && compareOverCounteredAttackers(
                    attackerIndex,
                    overCountered.getInt(cursor - 1),
                    realizedCounters,
                    attackerCounts
            ) < 0) {
                overCountered.set(cursor, overCountered.getInt(cursor - 1));
                cursor--;
            }
            overCountered.set(cursor, attackerIndex);
        }
    }

    private static int compareOverCounteredAttackers(
            int leftAttackerIndex,
            int rightAttackerIndex,
            int[] realizedCounters,
            int[] attackerCounts
    ) {
        int countOrder = Integer.compare(realizedCounters[rightAttackerIndex], realizedCounters[leftAttackerIndex]);
        if (countOrder != 0) {
            return countOrder;
        }
        int seedOrder = Integer.compare(attackerCounts[rightAttackerIndex], attackerCounts[leftAttackerIndex]);
        if (seedOrder != 0) {
            return seedOrder;
        }
        return Integer.compare(leftAttackerIndex, rightAttackerIndex);
    }

    private static void rebuildAttackerEdgesFromMidHorizon(
            CandidateEdgeTable edges,
            int attackerIndex,
            LongHorizonForwardProjection.AttackerMidHorizonSnapshot snapshot
    ) {
        double rawFactor = snapshot.attackerEdgeFactor(attackerIndex);
        float factor = (float) Math.max(OVERCOUNTER_PROJECTED_FLOOR, Math.min(1d, rawFactor));
        edges.rescaleAttackerEdgesFromProjectedState(attackerIndex, factor);
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

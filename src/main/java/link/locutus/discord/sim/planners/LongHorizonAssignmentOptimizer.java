package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Budgeted primitive marginal-flow owner for blitz horizons where first-turn exact local search is
 * the wrong optimization target.
 *
 * <p>Operates as a deterministic marginal-flow controller:
 * <ol>
 *   <li>Solve a baseline opening assignment with the initial edge-table scores.</li>
 *   <li>Build the full-horizon projection objective over base edge scores, defender-pressure
 *       completion, and attacker-commitment completion.</li>
 *   <li>Run one expanded-slot min-cost-flow solve that optimizes those marginal objective
 *       components directly.</li>
 * </ol>
 *
 * <p>The map-shaped assignment is still only materialized at the boundary. The bridge can also
 * score a small portfolio of candidate opening shapes through the primitive forward projection,
 * so objective terminal value can override the raw assignment scalar without replay allocation.
 */
final class LongHorizonAssignmentOptimizer {
    private static final int SHORT_HORIZON_LIMIT_TURNS = 12;
    private static final int MAX_HORIZON_TURNS = 720;
    static final int FEEDBACK_OVERCOUNTER_THRESHOLD = 2;
    private static final double REALIZED_COUNTER_OBJECTIVE_PENALTY = 75d;
    static final double PRESSURE_SCORE_WEIGHT = 0.24d;
    static final double EPSILON = 1e-9;

    private LongHorizonAssignmentOptimizer() {
    }

    static boolean shouldOptimize(int horizonTurns) {
        return horizonTurns > SHORT_HORIZON_LIMIT_TURNS;
    }

    static Map<Integer, List<Integer>> solve(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns
    ) {
        return solve(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                null
        );
    }

    static Map<Integer, List<Integer>> solve(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            ProjectionScoringContext projectionScoringContext
    ) {
        return solveDetailed(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                projectionScoringContext
        ).assignment();
    }

    static Result solveDetailed(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            ProjectionScoringContext projectionScoringContext
    ) {
            try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE)) {
                int edgeCount = baseEdges.edgeCount();
                int attackerCount = scenario.attackerCount();
                int defenderCount = scenario.defenderCount();
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "edges", edgeCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "attackers", attackerCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "defenders", defenderCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "horizonTurns", horizonTurns);

                boolean[] initialEdgeAssigned = new boolean[edgeCount];
                int[] initialAttackerCounts = new int[attackerCount];
                int[] initialDefenderCounts = new int[defenderCount];

                Map<Integer, List<Integer>> initialAssignment = PrimitiveAssignmentSolver.solveAssignment(
                    baseEdges,
                    null,
                    attackerCount,
                    defenderCount,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    edgeCount > 0 ? initialEdgeAssigned : null,
                    initialAttackerCounts,
                    initialDefenderCounts
                );
                if (!shouldOptimize(horizonTurns) || edgeCount == 0) {
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "assignmentPairs", assignmentPairCount(initialAssignment));
                return new Result(initialAssignment, null);
                }

                LongHorizonControlProjection terminalProjection = LongHorizonControlProjection.create(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    horizonTurns,
                    horizonFactor(horizonTurns)
                );
                double initialScore = terminalProjection.assignmentScoreDense(
                    initialEdgeAssigned,
                    initialAttackerCounts,
                    initialDefenderCounts
                );
                LongHorizonMarginalFlowSolver.Result marginalResult = LongHorizonMarginalFlowSolver.solve(
                    baseEdges,
                    terminalProjection,
                    attackerCount,
                    defenderCount,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges
                );
                double marginalScore = terminalProjection.assignmentScoreDense(
                    marginalResult.edgeAssigned(),
                    marginalResult.attackerCounts(),
                    marginalResult.defenderCounts()
                );
                Candidate best = new Candidate(
                    initialAssignment,
                    initialEdgeAssigned,
                    initialAttackerCounts,
                    initialDefenderCounts,
                    initialScore
                );
                Candidate marginalCandidate = new Candidate(
                    marginalResult.assignment(),
                    marginalResult.edgeAssigned(),
                    marginalResult.attackerCounts(),
                    marginalResult.defenderCounts(),
                    marginalScore
                );
                EvaluationCache evaluationCache = new EvaluationCache();
                best = betterCandidate(best, marginalCandidate, terminalProjection, scenario, projectionScoringContext, evaluationCache);

                if (projectionScoringContext != null && canScoreProjection(scenario)) {
                int[] realizedCounters = realizedCountersFor(
                    marginalCandidate,
                    terminalProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
                );
                for (Candidate reliefCandidate : LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    horizonTurns,
                    marginalCandidate,
                    terminalProjection,
                    realizedCounters
                )) {
                    best = betterCandidate(best, reliefCandidate, terminalProjection, scenario, projectionScoringContext, evaluationCache);
                }
                int[] feedbackCounters = realizedCountersFor(
                    best,
                    terminalProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
                );
                best = betterCandidate(best, LongHorizonFeedbackSearch.recedingFixedPointFeedback(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    horizonTurns,
                    best,
                    feedbackCounters,
                    terminalProjection,
                    projectionScoringContext,
                    evaluationCache
                ), terminalProjection, scenario, projectionScoringContext, evaluationCache);
                best = betterCandidate(best, solveWithAttackerCapLimit(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    horizonTurns,
                    1
                ), terminalProjection, scenario, projectionScoringContext, evaluationCache);
                best = betterCandidate(best, solveWithAttackerCapLimit(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    horizonTurns,
                    2
                ), terminalProjection, scenario, projectionScoringContext, evaluationCache);
                }
                ScoreSummary projectedObjectiveSummary = cachedObjectiveSummary(
                    best,
                    terminalProjection,
                    scenario,
                    projectionScoringContext,
                    evaluationCache
                );
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "assignmentPairs", assignmentPairCount(best.assignment()));
                return new Result(cloneAssignment(best.assignment()), projectedObjectiveSummary);
            }
    }

            private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
            int pairCount = 0;
            for (List<Integer> defenders : assignment.values()) {
                pairCount += defenders.size();
            }
            return pairCount;
            }

    private static Candidate solveWithAttackerCapLimit(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            int attackerCapLimit
    ) {
        int[] limitedCaps = new int[attackerCaps.length];
        for (int index = 0; index < attackerCaps.length; index++) {
            limitedCaps[index] = Math.min(attackerCaps[index], attackerCapLimit);
        }
        return solveWithAttackerCaps(
                baseEdges,
                scenario,
                limitedCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns
        );
    }

    static Candidate solveWithAttackerCaps(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns
    ) {
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                horizonTurns,
                horizonFactor(horizonTurns)
        );
        LongHorizonMarginalFlowSolver.Result result = LongHorizonMarginalFlowSolver.solve(
                baseEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges
        );
        double projectionScore = projection.assignmentScoreDense(
                result.edgeAssigned(),
                result.attackerCounts(),
                result.defenderCounts()
        );
        return new Candidate(result.assignment(), result.edgeAssigned(), result.attackerCounts(), result.defenderCounts(), projectionScore);
    }

    private static Candidate betterCandidate(
            Candidate current,
            Candidate candidate,
            LongHorizonControlProjection projection,
            CompiledScenario scenario,
            ProjectionScoringContext projectionScoringContext,
            EvaluationCache evaluationCache
    ) {
        if (candidate == null) {
            return current;
        }
        double currentScore = scoreCandidate(current, projection, scenario, projectionScoringContext, evaluationCache);
        double candidateScore = scoreCandidate(candidate, projection, scenario, projectionScoringContext, evaluationCache);
        return candidateScore > currentScore + EPSILON ? candidate : current;
    }

    static double scoreCandidate(
            Candidate candidate,
            LongHorizonControlProjection projection,
            CompiledScenario scenario,
            ProjectionScoringContext projectionScoringContext,
            EvaluationCache evaluationCache
    ) {
        if (projectionScoringContext == null || !canScoreProjection(scenario)) {
            return candidate.projectionScore();
        }
        int attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(
                candidate,
                projection,
                projectionScoringContext,
                attackerTeamId,
                evaluationCache
        );
        return candidate.projectionScore()
            + evaluation.objectiveScore()
            - realizedCounterObjectivePenalty(candidate, evaluation.realizedCounterIncidence());
    }

    private static ScoreSummary cachedObjectiveSummary(
            Candidate candidate,
            LongHorizonControlProjection projection,
            CompiledScenario scenario,
            ProjectionScoringContext projectionScoringContext,
            EvaluationCache evaluationCache
    ) {
        if (candidate.assignment().isEmpty()) {
            return ScoreSummary.identical(0d);
        }
        if (projectionScoringContext == null || !canScoreProjection(scenario)) {
            return null;
        }
        int attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(
                candidate,
                projection,
                projectionScoringContext,
                attackerTeamId,
                evaluationCache
        );
        return ScoreSummary.identical(evaluation.objectiveScore());
    }

    private static LongHorizonForwardProjection.ProjectedEvaluation evaluationFor(
            Candidate candidate,
            LongHorizonControlProjection projection,
            ProjectionScoringContext projectionScoringContext,
            int attackerTeamId,
            EvaluationCache evaluationCache
    ) {
        LongHorizonForwardProjection.ProjectedEvaluation cached = evaluationCache.projectedEvaluations.get(candidate);
        if (cached != null) {
            return cached;
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = projection.projectedEvaluation(
                projectionScoringContext.objective(),
                attackerTeamId,
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts()
        );
        evaluationCache.projectedEvaluations.put(candidate, evaluation);
        evaluationCache.realizedCounters.put(candidate, evaluation.realizedCounterIncidence());
        return evaluation;
    }

    static int[] realizedCountersFor(
            Candidate candidate,
            LongHorizonControlProjection projection,
            CompiledScenario scenario,
            ProjectionScoringContext projectionScoringContext,
            EvaluationCache evaluationCache
    ) {
        int[] cached = evaluationCache.realizedCounters.get(candidate);
        if (cached != null) {
            return cached;
        }
        if (projectionScoringContext != null && canScoreProjection(scenario)) {
            int attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
            return evaluationFor(candidate, projection, projectionScoringContext, attackerTeamId, evaluationCache)
                    .realizedCounterIncidence();
        }
        int[] realizedCounters = projection.realizedCounterIncidence(
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts()
        );
        evaluationCache.realizedCounters.put(candidate, realizedCounters);
        return realizedCounters;
    }

    private static double realizedCounterObjectivePenalty(Candidate candidate, int[] realizedCounters) {
        double penalty = 0d;
        for (int attackerIndex = 0; attackerIndex < realizedCounters.length; attackerIndex++) {
            if (candidate.attackerCounts()[attackerIndex] <= 0) {
                continue;
            }
            int overCounter = realizedCounters[attackerIndex] - FEEDBACK_OVERCOUNTER_THRESHOLD + 1;
            if (overCounter > 0) {
                penalty += overCounter * REALIZED_COUNTER_OBJECTIVE_PENALTY;
            }
        }
        return penalty;
    }

    static ScoreSummary projectedObjectiveSummary(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            Map<Integer, List<Integer>> assignment,
            TeamScoreObjective objective,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        if (assignment.isEmpty()) {
            return ScoreSummary.identical(0d);
        }
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                horizonTurns,
                horizonFactor(horizonTurns)
        );
        DenseAssignment denseAssignment = denseAssignment(
                baseEdges,
                scenario,
                assignment,
                attackerNationIds,
                defenderNationIds
        );
        int attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
        return ScoreSummary.identical(projection.projectedObjectiveScore(
                objective,
                attackerTeamId,
                denseAssignment.edgeAssigned(),
                denseAssignment.attackerCounts(),
                denseAssignment.defenderCounts()
        ));
    }

    private static DenseAssignment denseAssignment(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            Map<Integer, List<Integer>> assignment,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        boolean[] edgeAssigned = new boolean[baseEdges.edgeCount()];
        int[] attackerCounts = new int[scenario.attackerCount()];
        int[] defenderCounts = new int[scenario.defenderCount()];
        Map<Integer, Integer> attackerIndexByNationId = new LinkedHashMap<>(Math.max(16, attackerNationIds.length * 2));
        Map<Integer, Integer> defenderIndexByNationId = new LinkedHashMap<>(Math.max(16, defenderNationIds.length * 2));
        Map<Long, Integer> edgeIndexByPair = new LinkedHashMap<>(Math.max(16, baseEdges.edgeCount() * 2));
        for (int attackerIndex = 0; attackerIndex < attackerNationIds.length; attackerIndex++) {
            attackerIndexByNationId.put(attackerNationIds[attackerIndex], attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderNationIds.length; defenderIndex++) {
            defenderIndexByNationId.put(defenderNationIds[defenderIndex], defenderIndex);
        }
        for (int edgeIndex = 0; edgeIndex < baseEdges.edgeCount(); edgeIndex++) {
            edgeIndexByPair.put(pairKey(
                    attackerNationIds[baseEdges.attackerIndex(edgeIndex)],
                    defenderNationIds[baseEdges.defenderIndex(edgeIndex)]
            ), edgeIndex);
        }
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerIndex = attackerIndexByNationId.get(entry.getKey());
            if (attackerIndex == null) {
                continue;
            }
            for (int defenderNationId : entry.getValue()) {
                Integer defenderIndex = defenderIndexByNationId.get(defenderNationId);
                if (defenderIndex == null) {
                    continue;
                }
                attackerCounts[attackerIndex]++;
                defenderCounts[defenderIndex]++;
                Integer edgeIndex = edgeIndexByPair.get(pairKey(entry.getKey(), defenderNationId));
                if (edgeIndex != null) {
                    edgeAssigned[edgeIndex] = true;
                }
            }
        }
        return new DenseAssignment(edgeAssigned, attackerCounts, defenderCounts);
    }

    private static boolean canScoreProjection(CompiledScenario scenario) {
        Set<Integer> attackerIds = new LinkedHashSet<>();
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            attackerIds.add(scenario.attackerNationId(attackerIndex));
        }
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            if (attackerIds.contains(scenario.defenderNationId(defenderIndex))) {
                return false;
            }
        }
        return true;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) ^ (defenderNationId & 0xffffffffL);
    }

    private static Map<Integer, List<Integer>> cloneAssignment(Map<Integer, List<Integer>> assignment) {
        Map<Integer, List<Integer>> clone = new LinkedHashMap<>(Math.max(16, assignment.size() * 2));
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            clone.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return clone;
    }

    static double horizonFactor(int horizonTurns) {
        int clampedHorizon = Math.max(SHORT_HORIZON_LIMIT_TURNS + 1, Math.min(MAX_HORIZON_TURNS, horizonTurns));
        double numerator = Math.log1p(clampedHorizon - SHORT_HORIZON_LIMIT_TURNS);
        double denominator = Math.log1p(MAX_HORIZON_TURNS - SHORT_HORIZON_LIMIT_TURNS);
        return Math.max(0d, Math.min(1d, numerator / denominator));
    }

        record ProjectionScoringContext(
            TeamScoreObjective objective
    ) {
    }

    record Result(
            Map<Integer, List<Integer>> assignment,
            ScoreSummary projectedObjectiveSummary
    ) {
    }

        record Candidate(
            Map<Integer, List<Integer>> assignment,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            double projectionScore
    ) {
    }

    private record DenseAssignment(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
    }

    static final class EvaluationCache {
        private final IdentityHashMap<Candidate, LongHorizonForwardProjection.ProjectedEvaluation> projectedEvaluations =
                new IdentityHashMap<>();
        private final IdentityHashMap<Candidate, int[]> realizedCounters = new IdentityHashMap<>();
    }
}

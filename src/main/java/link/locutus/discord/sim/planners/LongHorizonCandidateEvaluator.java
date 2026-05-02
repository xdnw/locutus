package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Candidate-level terminal objective evaluator for long-horizon assignment search.
 *
 * <p>The optimizer owns which candidates to try. This owner decides how a dense candidate is
 * scored against the terminal projection and caches the expensive projected evaluation used by
 * both objective scoring and realized-counter feedback.</p>
 */
final class LongHorizonCandidateEvaluator {
    private static final int REALIZED_COUNTER_OBJECTIVE_PENALTY = 75;

    private final LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext;
    private final boolean canScoreProjection;
    private final int attackerTeamId;
    private final IdentityHashMap<LongHorizonAssignmentOptimizer.Candidate, LongHorizonForwardProjection.ProjectedEvaluation>
            projectedEvaluations = new IdentityHashMap<>();
    private final IdentityHashMap<LongHorizonAssignmentOptimizer.Candidate, int[]> realizedCounters =
            new IdentityHashMap<>();

    private LongHorizonCandidateEvaluator(
            CompiledScenario scenario,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext
    ) {
        this.projectionScoringContext = projectionScoringContext;
        this.canScoreProjection = canScoreProjection(scenario);
        this.attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
    }

    static LongHorizonCandidateEvaluator create(
            CompiledScenario scenario,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext
    ) {
        return new LongHorizonCandidateEvaluator(scenario, projectionScoringContext);
    }

    LongHorizonAssignmentOptimizer.Candidate betterCandidate(
            LongHorizonAssignmentOptimizer.Candidate current,
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (candidate == null) {
            return current;
        }
        double currentScore = score(current, projection);
        double candidateScore = score(candidate, projection);
        return candidateScore > currentScore + LongHorizonAssignmentOptimizer.EPSILON ? candidate : current;
    }

    boolean canScoreObjectiveProjection() {
        return projectionScoringContext != null && canScoreProjection;
    }

    double score(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (projectionScoringContext == null || !canScoreProjection) {
            return candidate.projectionScore();
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(candidate, projection);
        return candidate.projectionScore()
                + evaluation.objectiveScore()
                - realizedCounterObjectivePenalty(candidate, evaluation.realizedCounterIncidence());
    }

    ObjectiveValueSummary objectiveSummary(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (candidate.assignment().isEmpty()) {
            return ObjectiveValueSummary.identical(0d);
        }
        if (projectionScoringContext == null || !canScoreProjection) {
            return null;
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(candidate, projection);
        return ObjectiveValueSummary.identical(evaluation.objectiveScore());
    }

    int[] realizedCounters(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        int[] cached = realizedCounters.get(candidate);
        if (cached != null) {
            return cached;
        }
        if (projectionScoringContext != null && canScoreProjection) {
            return evaluationFor(candidate, projection).realizedCounterIncidence();
        }
        int[] realized = projection.realizedCounterIncidence(
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts()
        );
        realizedCounters.put(candidate, realized);
        return realized;
    }

    private LongHorizonForwardProjection.ProjectedEvaluation evaluationFor(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        LongHorizonForwardProjection.ProjectedEvaluation cached = projectedEvaluations.get(candidate);
        if (cached != null) {
            return cached;
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation;
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)) {
            evaluation = projection.projectedEvaluation(
                    projectionScoringContext.objective(),
                    attackerTeamId,
                    candidate.edgeAssigned(),
                    candidate.attackerCounts(),
                    candidate.defenderCounts()
            );
        }
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "projectedEvaluations", 1);
        projectedEvaluations.put(candidate, evaluation);
        realizedCounters.put(candidate, evaluation.realizedCounterIncidence());
        return evaluation;
    }

    private static double realizedCounterObjectivePenalty(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            int[] realizedCounters
    ) {
        double penalty = 0d;
        for (int attackerIndex = 0; attackerIndex < realizedCounters.length; attackerIndex++) {
            if (candidate.attackerCounts()[attackerIndex] <= 0) {
                continue;
            }
            int overCounter = realizedCounters[attackerIndex]
                    - LongHorizonFeedbackSearch.OVERCOUNTER_THRESHOLD
                    + 1;
            if (overCounter > 0) {
                penalty += overCounter * REALIZED_COUNTER_OBJECTIVE_PENALTY;
            }
        }
        return penalty;
    }

    private static boolean canScoreProjection(CompiledScenario scenario) {
        Set<Integer> attackerIds = new IntOpenHashSet(Math.max(16, scenario.attackerCount() * 2));
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
}

package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Candidate-level terminal objective evaluator for long-horizon assignment search.
 *
 * <p>The optimizer owns which candidates to try. This owner decides how a dense candidate is
 * scored against the terminal projection and caches the expensive projected evaluation used by
 * both objective scoring and realized-counter feedback.</p>
 */
final class LongHorizonCandidateEvaluator {
    private final LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext;
    private final boolean canScoreProjection;
    private final int attackerTeamId;
        private final IdentityHashMap<LongHorizonAssignmentOptimizer.Candidate, CandidateStateKey> candidateKeys =
            new IdentityHashMap<>();
        private final Map<CandidateStateKey, LongHorizonForwardProjection.ProjectedEvaluation> projectedEvaluations =
            new HashMap<>();
        private final Map<CandidateStateKey, LongHorizonForwardProjection.ProjectedFeedbackEvaluation> projectedFeedbackEvaluations =
            new HashMap<>();
        private final Map<CandidateStateKey, LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation> projectedAttackerFeedbackEvaluations =
            new HashMap<>();
        private final Map<CandidateStateKey, int[]> realizedCounters = new HashMap<>();

    private LongHorizonCandidateEvaluator(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
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
        return new LongHorizonCandidateEvaluator(scenario, null, projectionScoringContext);
    }

    static LongHorizonCandidateEvaluator create(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext
    ) {
        return new LongHorizonCandidateEvaluator(scenario, edges, projectionScoringContext);
    }

    LongHorizonAssignmentOptimizer.Candidate betterCandidate(
            LongHorizonAssignmentOptimizer.Candidate current,
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (candidate == null) {
            return current;
        }
        if (projectionScoringContext == null || !canScoreProjection) {
            return candidate.projectionScore() > current.projectionScore() + LongHorizonAssignmentOptimizer.EPSILON
                    ? candidate
                    : current;
        }
        if (!usesPrimaryTerminalComparison()) {
            double currentScore = score(current, projection);
            double candidateScore = score(candidate, projection);
            return candidateScore > currentScore + LongHorizonAssignmentOptimizer.EPSILON ? candidate : current;
        }
        double currentObjective = objectiveComparisonScore(current, projection);
        double candidateObjective = objectiveComparisonScore(candidate, projection);
        if (candidateObjective > currentObjective + LongHorizonAssignmentOptimizer.EPSILON) {
            return candidate;
        }
        if (Math.abs(candidateObjective - currentObjective) <= LongHorizonAssignmentOptimizer.EPSILON
                && candidate.projectionScore() > current.projectionScore() + LongHorizonAssignmentOptimizer.EPSILON) {
            return candidate;
        }
        return current;
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
        return score(candidate, evaluationFor(candidate, projection));
    }

    double score(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
    ) {
        if (projectionScoringContext == null || !canScoreProjection) {
            return candidate.projectionScore();
        }
        if (!usesPrimaryTerminalComparison()) {
            return candidate.projectionScore() + objectiveScore(candidate, evaluation);
        }
        return objectiveScore(candidate, evaluation);
    }

    private boolean usesPrimaryTerminalComparison() {
        return projectionScoringContext.objective().usesWarSlotDenial();
    }

    private double objectiveComparisonScore(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(candidate, projection);
        return evaluation.objectiveScore();
    }

    ObjectiveValueSummary objectiveSummary(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
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
        CandidateStateKey key = candidateStateKey(candidate);
        int[] cached = realizedCounters.get(key);
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
        realizedCounters.put(key, realized);
        return realized;
    }

    LongHorizonForwardProjection.ProjectedFeedbackEvaluation feedbackEvaluation(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        CandidateStateKey key = candidateStateKey(candidate);
        LongHorizonForwardProjection.ProjectedFeedbackEvaluation cached = projectedFeedbackEvaluations.get(key);
        if (cached != null) {
            return cached;
        }
        if (projectionScoringContext == null || !canScoreProjection) {
            throw new IllegalStateException("Feedback projection requires objective projection scoring");
        }
        LongHorizonForwardProjection.ProjectedFeedbackEvaluation evaluation;
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)) {
            evaluation = projection.projectedFeedbackEvaluation(
                    projectionScoringContext.objective(),
                    attackerTeamId,
                    candidate.edgeAssigned(),
                    candidate.attackerCounts(),
                    candidate.defenderCounts()
            );
        }
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "projectedEvaluations", 1);
        projectedFeedbackEvaluations.put(key, evaluation);
        cacheProjectedEvaluation(key, evaluation.projectedEvaluation());
        return evaluation;
    }

    LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation attackerFeedbackEvaluation(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        CandidateStateKey key = candidateStateKey(candidate);
        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation cached = projectedAttackerFeedbackEvaluations.get(key);
        if (cached != null) {
            return cached;
        }
        if (projectionScoringContext == null || !canScoreProjection) {
            throw new IllegalStateException("Feedback projection requires objective projection scoring");
        }
        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation evaluation;
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)) {
            evaluation = projection.projectedAttackerFeedbackEvaluation(
                    projectionScoringContext.objective(),
                    attackerTeamId,
                    candidate.edgeAssigned(),
                    candidate.attackerCounts(),
                    candidate.defenderCounts()
            );
        }
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "projectedEvaluations", 1);
        projectedAttackerFeedbackEvaluations.put(key, evaluation);
        cacheProjectedEvaluation(key, evaluation.projectedEvaluation());
        return evaluation;
    }

    private LongHorizonForwardProjection.ProjectedEvaluation evaluationFor(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        CandidateStateKey key = candidateStateKey(candidate);
        LongHorizonForwardProjection.ProjectedEvaluation cached = projectedEvaluations.get(key);
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
        return cacheProjectedEvaluation(key, evaluation);
    }

    private LongHorizonForwardProjection.ProjectedEvaluation cacheProjectedEvaluation(
            CandidateStateKey key,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
    ) {
        projectedEvaluations.put(key, evaluation);
        realizedCounters.put(key, evaluation.realizedCounterIncidence());
        return evaluation;
    }

    private CandidateStateKey candidateStateKey(LongHorizonAssignmentOptimizer.Candidate candidate) {
        CandidateStateKey cached = candidateKeys.get(candidate);
        if (cached != null) {
            return cached;
        }
        CandidateStateKey key = new CandidateStateKey(
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts(),
                CandidateStateKey.hash(candidate.edgeAssigned(), candidate.attackerCounts(), candidate.defenderCounts())
        );
        candidateKeys.put(candidate, key);
        return key;
    }

    private double objectiveScore(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
    ) {
        return evaluation.objectiveScore();
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

    private record CandidateStateKey(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int hash
    ) {
        private static int hash(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
            int result = Arrays.hashCode(edgeAssigned);
            result = 31 * result + Arrays.hashCode(attackerCounts);
            result = 31 * result + Arrays.hashCode(defenderCounts);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CandidateStateKey other)) {
                return false;
            }
            return Arrays.equals(edgeAssigned, other.edgeAssigned)
                    && Arrays.equals(attackerCounts, other.attackerCounts)
                    && Arrays.equals(defenderCounts, other.defenderCounts);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}

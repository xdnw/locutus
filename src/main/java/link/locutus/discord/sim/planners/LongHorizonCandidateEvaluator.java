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
    private static final int REALIZED_COUNTER_OBJECTIVE_PENALTY = 300;
    private static final double OPENING_OVERCOMMITMENT_OPPORTUNITY_WEIGHT = 0.35d;
    private static final double UNCOVERED_DEFENDER_OBJECTIVE_PENALTY_WEIGHT = 28.0d;

    private final LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext;
    private final boolean canScoreProjection;
    private final int attackerTeamId;
    private final double[] uncoveredDefenderPenalties;
    private final int[] openingCommitmentTargets;
    private final double[] openingOvercommitmentUnitPenalties;
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
        this.uncoveredDefenderPenalties = uncoveredDefenderPenalties(scenario, edges);
        this.openingCommitmentTargets = openingCommitmentTargets(scenario, edges);
        this.openingOvercommitmentUnitPenalties = openingOvercommitmentUnitPenalties(scenario, edges);
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
        double realizedCounterPenalty = realizedCounterObjectivePenalty(candidate, evaluation.realizedCounterIncidence());
        double openingOvercommitmentPenalty = openingOvercommitmentObjectivePenalty(candidate);
        double uncoveredDefenderPenalty = uncoveredDefenderObjectivePenalty(candidate);
        return evaluation.objectiveScore()
                - realizedCounterPenalty
                - openingOvercommitmentPenalty
                - uncoveredDefenderPenalty;
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
        double realizedCounterPenalty = realizedCounterObjectivePenalty(candidate, evaluation.realizedCounterIncidence());
        double openingOvercommitmentPenalty = openingOvercommitmentObjectivePenalty(candidate);
        double uncoveredDefenderPenalty = uncoveredDefenderObjectivePenalty(candidate);
        return evaluation.objectiveScore()
                - realizedCounterPenalty
                - openingOvercommitmentPenalty
                - uncoveredDefenderPenalty;
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

    private double uncoveredDefenderObjectivePenalty(LongHorizonAssignmentOptimizer.Candidate candidate) {
        double penalty = 0d;
        int[] defenderCounts = candidate.defenderCounts();
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length && defenderIndex < uncoveredDefenderPenalties.length; defenderIndex++) {
            if (defenderCounts[defenderIndex] == 0) {
                penalty += uncoveredDefenderPenalties[defenderIndex];
            }
        }
        return penalty;
    }

    private static double[] uncoveredDefenderPenalties(CompiledScenario scenario, CandidateEdgeTable edges) {
        double[] penalties = new double[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < penalties.length; defenderIndex++) {
            DBNationSnapshot defender = scenario.defender(defenderIndex);
            double tierWeight = Math.max(0d, Math.min(1d, (defender.cities() - 35d) / 10d));
            if (!(tierWeight > 0d)) {
                continue;
            }
            penalties[defenderIndex] = UNCOVERED_DEFENDER_OBJECTIVE_PENALTY_WEIGHT
                    * tierWeight
                    * (OpeningMetricSummary.defenderControlPressure(defender) + (35d * defender.cities()));
        }
        if (edges == null || edges.edgeCount() == 0) {
            return penalties;
        }
        int[] defenderCaps = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < defenderCaps.length; defenderIndex++) {
            defenderCaps[defenderIndex] = Math.max(0, scenario.defenderFreeDefSlots(defenderIndex));
        }
        int[] defenderNeeds = LongHorizonOpeningCommitmentModel.defenderPressureNeeds(scenario, edges, defenderCaps);
        double[] maxIncomingEdgeScores = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            double edgeScore = Math.max(0d, edges.scalarScore(edgeIndex));
            if (!(edgeScore > 0d)) {
                continue;
            }
            int defenderIndex = edges.defenderIndex(edgeIndex);
            maxIncomingEdgeScores[defenderIndex] = Math.max(maxIncomingEdgeScores[defenderIndex], edgeScore);
        }
        for (int defenderIndex = 0; defenderIndex < penalties.length; defenderIndex++) {
            penalties[defenderIndex] = Math.max(
                    penalties[defenderIndex],
                    maxIncomingEdgeScores[defenderIndex] * Math.max(1, defenderNeeds[defenderIndex])
            );
        }
        return penalties;
    }

    private double openingOvercommitmentObjectivePenalty(LongHorizonAssignmentOptimizer.Candidate candidate) {
        double penalty = 0d;
        int[] attackerCounts = candidate.attackerCounts();
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            int target = attackerIndex < openingCommitmentTargets.length ? openingCommitmentTargets[attackerIndex] : 0;
            int surplus = attackerCounts[attackerIndex] - Math.max(0, target);
            if (surplus <= 0) {
                continue;
            }
            double unitPenalty = attackerIndex < openingOvercommitmentUnitPenalties.length
                    ? openingOvercommitmentUnitPenalties[attackerIndex]
                    : 0d;
            double targetScale = 1d / Math.max(1d, target);
            penalty += unitPenalty * targetScale * surplus * surplus;
        }
        return penalty;
    }

    private static double[] openingOvercommitmentUnitPenalties(CompiledScenario scenario, CandidateEdgeTable edges) {
        double[] penalties = new double[scenario.attackerCount()];
        if (edges == null || edges.edgeCount() == 0) {
            return penalties;
        }
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            double edgeScore = Math.max(0d, edges.scalarScore(edgeIndex));
            if (!(edgeScore > 0d)) {
                continue;
            }
            int attackerIndex = edges.attackerIndex(edgeIndex);
            penalties[attackerIndex] = Math.max(
                    penalties[attackerIndex],
                    OPENING_OVERCOMMITMENT_OPPORTUNITY_WEIGHT * edgeScore
            );
        }
        return penalties;
    }

    private static int[] openingCommitmentTargets(CompiledScenario scenario, CandidateEdgeTable edges) {
        if (edges == null || edges.edgeCount() == 0) {
            return new int[scenario.attackerCount()];
        }
        int[] attackerCaps = new int[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < attackerCaps.length; attackerIndex++) {
            attackerCaps[attackerIndex] = Math.max(0, scenario.attackerFreeOffSlots(attackerIndex));
        }
        int[] defenderCaps = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < defenderCaps.length; defenderIndex++) {
            defenderCaps[defenderIndex] = Math.max(0, scenario.defenderFreeDefSlots(defenderIndex));
        }
        double[] edgeScores = new double[edges.edgeCount()];
        for (int edgeIndex = 0; edgeIndex < edgeScores.length; edgeIndex++) {
            edgeScores[edgeIndex] = edges.scalarScore(edgeIndex);
        }
        return LongHorizonOpeningCommitmentModel.attackerCommitmentNeeds(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                edgeScores
        );
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

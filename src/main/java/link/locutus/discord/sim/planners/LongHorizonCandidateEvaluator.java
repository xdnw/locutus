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
    private static final double PROJECTED_OVERLOADED_ATTACKER_REGRET_WEIGHT = 1.25d;
    private static final double PROJECTED_COUNTER_STORM_EXCESS_REGRET_WEIGHT = 0.75d;
    private static final double PROJECTED_NO_POSITIVE_ATTACK_RATE_REGRET_WEIGHT = 1.5d;
    private static final double PROJECTED_NO_ATTACK_RATE_REGRET_WEIGHT = 0.75d;
    private static final double PROJECTED_UNDER_STRENGTH_LATER_DECLARATION_RATE_REGRET_WEIGHT = 0.75d;

    private final LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionScoringContext;
    private final boolean canScoreProjection;
    private final int attackerTeamId;
    private final int defenderTeamId;
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
        this.defenderTeamId = scenario.defenderCount() == 0 ? attackerTeamId : scenario.defender(0).teamId();
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
        LongHorizonForwardProjection.ProjectedEvaluation currentEvaluation = evaluationFor(current, projection);
        LongHorizonForwardProjection.ProjectedEvaluation candidateEvaluation = evaluationFor(candidate, projection);
        double currentObjective = objectiveScore(current, currentEvaluation);
        double candidateObjective = objectiveScore(candidate, candidateEvaluation);
        if (candidateObjective > currentObjective + LongHorizonAssignmentOptimizer.EPSILON) {
            return candidate;
        }
        if (Math.abs(candidateObjective - currentObjective) <= LongHorizonAssignmentOptimizer.EPSILON) {
            int familyComparison = compareProjectedFamilyConsequences(
                    candidate,
                    candidateEvaluation,
                    current,
                    currentEvaluation
            );
            if (familyComparison < 0) {
                return candidate;
            }
            if (familyComparison == 0
                    && candidate.projectionScore() > current.projectionScore() + LongHorizonAssignmentOptimizer.EPSILON) {
                return candidate;
            }
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

    ObjectiveValueSummary objectiveSummary(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (projectionScoringContext == null || !canScoreProjection) {
            return null;
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluationFor(candidate, projection);
        return ObjectiveValueSummary.identical(objectiveScore(candidate, evaluation));
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

    LongHorizonForwardProjection.ProjectedEvaluation projectedEvaluation(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonControlProjection projection
    ) {
        if (projectionScoringContext == null || !canScoreProjection) {
            throw new IllegalStateException("Projected evaluation requires objective projection scoring");
        }
        return evaluationFor(candidate, projection);
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
                    defenderTeamId,
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
        return projectedPrimaryObjectiveScore(candidate, evaluation);
        }

        static double projectedPrimaryObjectiveScore(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
        ) {
        return evaluation.comparisonScore()
                + candidate.projectionScore()
            - evaluation.openingSideDelayedDeclarationRegret()
            - projectedStrategyRegretPenalty(candidate, evaluation);
        }

        static double projectedStrategyRegretPenalty(
            LongHorizonAssignmentOptimizer.Candidate candidate,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
        ) {
        LongHorizonForwardProjection.ProjectedFamilyConsequences consequences = evaluation.familyConsequences();
        int[] attackerCounts = candidate.attackerCounts();
        int[] realizedCounterIncidence = evaluation.realizedCounterIncidence();
        double overloadedAttackerRate = ratio(
            projectedOverloadedAttackers(attackerCounts, realizedCounterIncidence),
            committedAttackers(attackerCounts)
        );
        double counterStormExcessRate = ratio(
            projectedCounterStormExcess(attackerCounts, realizedCounterIncidence),
            committedOpenings(attackerCounts)
        );
        double noPositiveAttackRate = ratio(
            consequences.noPositiveAttackChoices(),
            consequences.attackChoiceCalls()
        );
        double noAttackRate = ratio(
            consequences.noAttackChoices(),
            consequences.attackChoiceCalls()
        );
        double underStrengthLaterDeclarationRate = ratio(
            consequences.underStrengthSelectedLaterDeclarations(),
            consequences.selectedLaterDeclarations()
        );
        return PROJECTED_OVERLOADED_ATTACKER_REGRET_WEIGHT * overloadedAttackerRate
            + PROJECTED_COUNTER_STORM_EXCESS_REGRET_WEIGHT * counterStormExcessRate
            + PROJECTED_NO_POSITIVE_ATTACK_RATE_REGRET_WEIGHT * noPositiveAttackRate
            + PROJECTED_NO_ATTACK_RATE_REGRET_WEIGHT * noAttackRate
            + PROJECTED_UNDER_STRENGTH_LATER_DECLARATION_RATE_REGRET_WEIGHT * underStrengthLaterDeclarationRate;
    }

    static boolean preferProjectedFamilyConsequences(
            LongHorizonAssignmentOptimizer.Candidate preferredCandidate,
            LongHorizonForwardProjection.ProjectedEvaluation preferredEvaluation,
            LongHorizonAssignmentOptimizer.Candidate otherCandidate,
            LongHorizonForwardProjection.ProjectedEvaluation otherEvaluation
    ) {
        return compareProjectedFamilyConsequences(
            preferredCandidate,
            preferredEvaluation,
            otherCandidate,
            otherEvaluation
        ) < 0;
        }

        private static int compareProjectedFamilyConsequences(
            LongHorizonAssignmentOptimizer.Candidate leftCandidate,
            LongHorizonForwardProjection.ProjectedEvaluation leftEvaluation,
            LongHorizonAssignmentOptimizer.Candidate rightCandidate,
            LongHorizonForwardProjection.ProjectedEvaluation rightEvaluation
        ) {
        int preferredOverloadedAttackers = projectedOverloadedAttackers(
            leftCandidate.attackerCounts(),
            leftEvaluation.realizedCounterIncidence()
        );
        int otherOverloadedAttackers = projectedOverloadedAttackers(
            rightCandidate.attackerCounts(),
            rightEvaluation.realizedCounterIncidence()
        );
        if (preferredOverloadedAttackers != otherOverloadedAttackers) {
            return Integer.compare(preferredOverloadedAttackers, otherOverloadedAttackers);
        }
        int preferredCounterStormExcess = projectedCounterStormExcess(
            leftCandidate.attackerCounts(),
            leftEvaluation.realizedCounterIncidence()
        );
        int otherCounterStormExcess = projectedCounterStormExcess(
            rightCandidate.attackerCounts(),
            rightEvaluation.realizedCounterIncidence()
        );
        if (preferredCounterStormExcess != otherCounterStormExcess) {
            return Integer.compare(preferredCounterStormExcess, otherCounterStormExcess);
        }
        LongHorizonForwardProjection.ProjectedFamilyConsequences preferredConsequences = leftEvaluation.familyConsequences();
        LongHorizonForwardProjection.ProjectedFamilyConsequences otherConsequences = rightEvaluation.familyConsequences();
        int noPositiveAttackRateComparison = comparePerCallRate(
            preferredConsequences.noPositiveAttackChoices(),
            preferredConsequences.attackChoiceCalls(),
            otherConsequences.noPositiveAttackChoices(),
            otherConsequences.attackChoiceCalls()
        );
        if (noPositiveAttackRateComparison != 0) {
            return noPositiveAttackRateComparison;
        }
        int noAttackRateComparison = comparePerCallRate(
            preferredConsequences.noAttackChoices(),
            preferredConsequences.attackChoiceCalls(),
            otherConsequences.noAttackChoices(),
            otherConsequences.attackChoiceCalls()
        );
        if (noAttackRateComparison != 0) {
            return noAttackRateComparison;
        }
        if (preferredConsequences.underStrengthSelectedLaterDeclarations()
                != otherConsequences.underStrengthSelectedLaterDeclarations()) {
            return Integer.compare(
                preferredConsequences.underStrengthSelectedLaterDeclarations(),
                otherConsequences.underStrengthSelectedLaterDeclarations()
            );
        }
        if (preferredConsequences.selectedLaterDeclarations() != otherConsequences.selectedLaterDeclarations()) {
            return Integer.compare(
                preferredConsequences.selectedLaterDeclarations(),
                otherConsequences.selectedLaterDeclarations()
            );
        }
        return 0;
    }

    private static int comparePerCallRate(int leftNumerator, int leftCalls, int rightNumerator, int rightCalls) {
        if (leftCalls <= 0 || rightCalls <= 0) {
            return 0;
        }
        long leftScaled = (long) leftNumerator * (long) rightCalls;
        long rightScaled = (long) rightNumerator * (long) leftCalls;
        return Long.compare(leftScaled, rightScaled);
    }

    private static int projectedOverloadedAttackers(int[] attackerCounts, int[] realizedCounterIncidence) {
        int count = 0;
        int length = Math.min(attackerCounts.length, realizedCounterIncidence.length);
        for (int attackerIndex = 0; attackerIndex < length; attackerIndex++) {
            if (realizedCounterIncidence[attackerIndex] > Math.max(1, attackerCounts[attackerIndex])) {
                count++;
            }
        }
        return count;
    }

    private static int projectedCounterStormExcess(int[] attackerCounts, int[] realizedCounterIncidence) {
        int excess = 0;
        int length = Math.min(attackerCounts.length, realizedCounterIncidence.length);
        for (int attackerIndex = 0; attackerIndex < length; attackerIndex++) {
            excess += Math.max(0, realizedCounterIncidence[attackerIndex] - Math.max(1, attackerCounts[attackerIndex]));
        }
        return excess;
    }

    private static int committedAttackers(int[] attackerCounts) {
        int committed = 0;
        for (int count : attackerCounts) {
            if (count > 0) {
                committed++;
            }
        }
        return committed;
    }

    private static int committedOpenings(int[] attackerCounts) {
        int openings = 0;
        for (int count : attackerCounts) {
            openings += Math.max(0, count);
        }
        return openings;
    }

    private static double ratio(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) {
            return 0d;
        }
        return (double) numerator / (double) denominator;
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

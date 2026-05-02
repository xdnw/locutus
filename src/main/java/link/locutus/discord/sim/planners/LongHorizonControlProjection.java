package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.planners.compile.CompiledScenario;

/**
 * Primitive long-horizon control view over a candidate edge table.
 *
 * <p>Edge-indexed assignment scoring is dense: per-edge
 * arrays are indexed by edge ordinal, attacker/defender counts are indexed by
 * scenario slot, and there is no per-pair {@code Map} lookup in the hot loop.
 * Callers feed dense per-attacker/per-defender assigned counts and a per-edge
 * "edge carries flow" boolean buffer; this owner returns objective scalars and
 * exact slot marginal scores without rebuilding any collection.
 */
final class LongHorizonControlProjection implements LongHorizonMarginalScorer {
    private final LongHorizonAssignmentScoringModel assignmentScoringModel;
    private final LongHorizonForwardProjection forwardProjection;

    private LongHorizonControlProjection(
            LongHorizonAssignmentScoringModel assignmentScoringModel,
            LongHorizonForwardProjection forwardProjection
    ) {
        this.assignmentScoringModel = assignmentScoringModel;
        this.forwardProjection = forwardProjection;
    }

    static LongHorizonControlProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        return new LongHorizonControlProjection(
                LongHorizonAssignmentScoringModel.create(edges, scenario, attackerCaps, defenderCaps, horizonTurns, horizonFactor),
                LongHorizonForwardProjection.create(edges, scenario, attackerCaps, horizonTurns, horizonFactor)
        );
    }

    /**
     * Computes the long-horizon objective scalar from a dense edge-assignment buffer
     * and dense per-side counts. Allocates nothing.
     */
    double assignmentScoreDense(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return assignmentScoringModel.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, forwardProjection);
    }

    @Override
    public double edgeScore(int edgeIndex) {
        return assignmentScoringModel.edgeScore(edgeIndex);
    }

    @Override
    public double attackerCommitmentMarginalScore(int attackerIndex, int assignedBefore) {
        return assignmentScoringModel.attackerCommitmentMarginalScore(attackerIndex, assignedBefore);
    }

    @Override
    public double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore) {
        return forwardProjection.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore);
    }

    double projectedObjectiveScore(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return forwardProjection.projectedObjectiveScore(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.ProjectedEvaluation projectedEvaluation(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return forwardProjection.projectedEvaluation(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

    int[] realizedCounterIncidence(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        return forwardProjection.realizedCounterIncidence(edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.MidHorizonSnapshot snapshotMidHorizonState(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return forwardProjection.snapshotMidHorizonState(
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                forwardProjection.defaultMidHorizonTurns()
        );
    }

    @Override
    public double defenderPressureMarginalScore(int defenderIndex, int assignedBefore) {
        return assignmentScoringModel.defenderPressureMarginalScore(defenderIndex, assignedBefore);
    }
}

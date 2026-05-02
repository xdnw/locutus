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
    private final LongHorizonCounterOpportunityModel counterOpportunityModel;
    private final LongHorizonForwardProjection forwardProjection;
    private final int[] attackerCaps;

    private LongHorizonControlProjection(
            LongHorizonAssignmentScoringModel assignmentScoringModel,
            LongHorizonCounterOpportunityModel counterOpportunityModel,
            LongHorizonForwardProjection forwardProjection,
            int[] attackerCaps
    ) {
        this.assignmentScoringModel = assignmentScoringModel;
        this.counterOpportunityModel = counterOpportunityModel;
        this.forwardProjection = forwardProjection;
        this.attackerCaps = java.util.Arrays.copyOf(attackerCaps, attackerCaps.length);
    }

    static LongHorizonControlProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        LongHorizonForwardProjection forwardProjection = LongHorizonForwardProjection.create(edges, scenario, attackerCaps, horizonTurns, horizonFactor);
        return new LongHorizonControlProjection(
                LongHorizonAssignmentScoringModel.create(edges, scenario, attackerCaps, defenderCaps, horizonTurns, horizonFactor),
                forwardProjection.counterOpportunityModel(),
                forwardProjection,
                attackerCaps
        );
    }

    static LongHorizonControlProjection createScorerOnly(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        return new LongHorizonControlProjection(
                LongHorizonAssignmentScoringModel.create(edges, scenario, attackerCaps, defenderCaps, horizonTurns, horizonFactor),
                LongHorizonForwardProjection.counterOpportunityModel(scenario, horizonTurns, horizonFactor),
                null,
                attackerCaps
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
        return assignmentScoringModel.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps);
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
        return counterOpportunityModel.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore, attackerCaps);
    }

    double projectedObjectiveScore(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.projectedObjectiveScore(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.ProjectedEvaluation projectedEvaluation(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.projectedEvaluation(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

    int[] realizedCounterIncidence(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        requireForwardProjection();
        return forwardProjection.realizedCounterIncidence(edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.MidHorizonSnapshot snapshotMidHorizonState(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
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

    private void requireForwardProjection() {
        if (forwardProjection == null) {
            throw new IllegalStateException("Terminal projection is unavailable on scorer-only long-horizon projection");
        }
    }
}

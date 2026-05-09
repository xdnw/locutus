package link.locutus.discord.sim.planners;

interface LongHorizonMarginalScorer {
    double edgeScore(int edgeIndex);

    double attackerCommitmentMarginalScore(int attackerIndex, int assignedBefore);

    double attackerIdlePressureMarginalScore(int attackerIndex);

    double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore);

    double defenderPressureMarginalScore(int defenderIndex, int assignedBefore);
}
package link.locutus.discord.sim.planners;

public record AdHocTargetRecommendation(
        int attackerId,
        int defenderId,
        double objectiveScore,
        double counterRisk,
        int horizonTurns,
        ObjectiveValueSummary objectiveSummary
) {
    public AdHocTargetRecommendation(
            int attackerId,
            int defenderId,
            double objectiveScore,
            double counterRisk,
            int horizonTurns
    ) {
        this(attackerId, defenderId, objectiveScore, counterRisk, horizonTurns, ObjectiveValueSummary.identical(objectiveScore));
    }
}

package link.locutus.discord.sim.planners;

public record AdHocTargetRecommendation(
        int attackerId,
        int defenderId,
        double objectiveScore,
        double counterRisk,
        int horizonTurns,
        ScoreSummary scoreSummary
) {
    public AdHocTargetRecommendation(
            int attackerId,
            int defenderId,
            double objectiveScore,
            double counterRisk,
            int horizonTurns
    ) {
        this(attackerId, defenderId, objectiveScore, counterRisk, horizonTurns, ScoreSummary.identical(objectiveScore));
    }
}
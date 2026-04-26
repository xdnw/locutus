package link.locutus.discord.sim.planners;

import java.util.List;

public record AdHocPlan(
        int attackerId,
        int horizonTurns,
        List<AdHocTargetRecommendation> recommendations,
        List<PlannerDiagnostic> diagnostics,
        AdHocPlanMetadata metadata
) {
    public AdHocPlan {
        recommendations = List.copyOf(recommendations);
        diagnostics = List.copyOf(diagnostics);
        metadata = metadata == null ? AdHocPlanMetadata.DEFAULT : metadata;
    }

    /**
     * Returns the highest objective score in the recommendation list, or negative infinity when empty.
     */
    public double bestObjectiveScore() {
        if (recommendations.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        return recommendations.get(0).objectiveScore();
    }
}
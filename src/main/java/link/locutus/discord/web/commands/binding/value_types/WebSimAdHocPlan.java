package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.sim.planners.AdHocPlanMetadata;
import link.locutus.discord.sim.planners.PlannerDiagnostic;

import java.util.List;

public record WebSimAdHocPlan(
        int attackerId,
        int horizonTurns,
        boolean worthWaiting,
        int suggestedWaitTurns,
        double currentActivityChance,
        double futureActivityChance,
        double currentObjectiveScore,
        double futureObjectiveScore,
        List<WebSimAdHocTarget> targets,
        List<PlannerDiagnostic> diagnostics,
        AdHocPlanMetadata metadata
) {
    public WebSimAdHocPlan {
        targets = List.copyOf(targets);
        diagnostics = List.copyOf(diagnostics);
        metadata = metadata == null ? AdHocPlanMetadata.DEFAULT : metadata;
        currentObjectiveScore = Double.isFinite(currentObjectiveScore) ? currentObjectiveScore : 0.0;
        futureObjectiveScore = Double.isFinite(futureObjectiveScore) ? futureObjectiveScore : 0.0;
    }
}

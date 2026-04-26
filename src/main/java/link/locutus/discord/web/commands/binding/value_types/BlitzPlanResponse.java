package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.sim.planners.PlannerDiagnostic;
import link.locutus.discord.util.battle.BlitzWarning;

public record BlitzPlanResponse(
        int currentTurn,
        int horizonTurns,
        int[] attackerNationIds,
        int[] defenderNationIds,
        BlitzNationRow[] nations,
        BlitzExistingWar[] existingWars,
        BlitzLegalEdge[] legalEdges,
        BlitzAssignedWar[] assignments,
        BlitzWarning[] warnings,
        PlannerDiagnostic[] diagnostics,
        BlitzObjectiveSummary objective,
        BlitzReplayTrace trace
) {
}

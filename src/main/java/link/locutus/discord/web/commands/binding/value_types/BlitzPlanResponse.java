package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.sim.planners.PlannerDiagnostic;

public record BlitzPlanResponse(
        int currentTurn,
        int horizonTurns,
        int[] attackerNationIds,
        int[] defenderNationIds,
        BlitzNationRow[] nations,
        BlitzOutsiderNation[] outsiderNations,
        BlitzExistingWar[] existingWars,
        BlitzPairLockout[] pairLockouts,
        BlitzLegalEdge[] legalEdges,
        BlitzAssignedWar[] assignments,
        BlitzPlanWarning[] warnings,
        PlannerDiagnostic[] diagnostics,
        BlitzObjectiveSummary objective,
        BlitzReplayTrace trace,
        BlitzMilitaryRules rules
) {
}

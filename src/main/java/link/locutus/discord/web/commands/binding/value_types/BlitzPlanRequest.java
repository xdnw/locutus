package link.locutus.discord.web.commands.binding.value_types;

public record BlitzPlanRequest(
        String attackers,
        String defenders,
        BlitzDraftEdit[] edits,
        BlitzPlannedWar[] plannedWars,
        int sideModeOrdinal,
        int rebuyModeOrdinal,
        int horizonTurns,
        boolean includeExistingWars,
        boolean assume5553Buildings,
        long stochasticSeed,
        Integer currentTurnOverride,
        int[] excludedWarIds,
        boolean runAssignment,
        boolean captureTrace
) {
    public BlitzPlanRequest {
        attackers = attackers == null || attackers.isBlank() ? "*" : attackers;
        defenders = defenders == null || defenders.isBlank() ? "*" : defenders;
        edits = edits == null ? new BlitzDraftEdit[0] : edits;
        plannedWars = plannedWars == null ? new BlitzPlannedWar[0] : plannedWars;
        excludedWarIds = excludedWarIds == null ? new int[0] : excludedWarIds;
        horizonTurns = horizonTurns == 0 ? 6 : horizonTurns;
        stochasticSeed = stochasticSeed == 0L ? 1L : stochasticSeed;
    }
}

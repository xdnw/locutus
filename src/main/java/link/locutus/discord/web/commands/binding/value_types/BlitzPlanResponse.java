package link.locutus.discord.web.commands.binding.value_types;

public record BlitzPlanResponse(
        int currentTurn,
        int horizonTurns,
        int plannerNationCount,
        int[] allianceIds,
        String[] allianceNames,
        int[] participantIds,
        String[] participantNames,
        int[] participantAllianceIndexes,
        int[] plannerScalarLanes,
        int[] plannerBitLanes,
        int[] plannerUnitLanes,
        int[] existingWarPairs,
        int[] existingWarLanes,
        int[] pairLockoutPairs,
        int[] pairLockoutLanes,
        int[] assignmentPairs,
        int[] assignmentLanes,
        int[] warningLanes,
        int[] diagnosticLanes,
        BlitzObjectiveSummary objective,
        BlitzReplayTrace trace
) {
}

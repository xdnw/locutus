package link.locutus.discord.web.commands.binding.value_types;

public record BlitzReplayTrace(
	int startTurn,
	int[] turnMetaLanes,
	int[] changedNationIndexes,
	int[] changedNationMasks,
	int[] changedNationLanes,
	int[] changedWarIndexes,
	int[] changedWarMasks,
	int[] changedWarLanes,
	int[] declaredWarPairs,
	int[] declaredWarLanes,
	int[] concludedWarLanes,
	int[] summaryScalarLanes,
	int[] summaryWarTypeCounts,
	int[] summaryAttackOutcomeCounts,
	int[] summaryUnitLossCounts,
	int[] summaryInfraLossCents
) {
}

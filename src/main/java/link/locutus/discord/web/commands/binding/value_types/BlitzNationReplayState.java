package link.locutus.discord.web.commands.binding.value_types;

public record BlitzNationReplayState(
        int nationId,
        int[] unitsByMilitaryUnitOrdinal,
        double[] cityInfra,
        double score,
        int beigeTurns,
        double[] resources
) {
}
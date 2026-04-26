package link.locutus.discord.web.commands.binding.value_types;

public record BlitzObjectiveSummary(
        double scoreMean,
        double scoreP10,
        double scoreP50,
        double scoreP90,
        int sampleCount
) {
}

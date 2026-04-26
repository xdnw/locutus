package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.sim.planners.ScoreSummary;

public record WebSimAdHocTarget(
        WebTarget target,
        double simScore,
        double counterRisk,
        double lootEstimate,
        ScoreSummary scoreSummary
) {
    public WebSimAdHocTarget(
            WebTarget target,
            double simScore,
            double counterRisk,
            double lootEstimate
    ) {
        this(target, simScore, counterRisk, lootEstimate, ScoreSummary.identical(simScore));
    }
}
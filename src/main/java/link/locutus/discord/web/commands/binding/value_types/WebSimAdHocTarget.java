package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.sim.planners.ObjectiveValueSummary;

public record WebSimAdHocTarget(
        WebTarget target,
        double simScore,
        double counterRisk,
        double lootEstimate,
        ObjectiveValueSummary scoreSummary
) {
    public WebSimAdHocTarget(
            WebTarget target,
            double simScore,
            double counterRisk,
            double lootEstimate
    ) {
        this(target, simScore, counterRisk, lootEstimate, ObjectiveValueSummary.identical(simScore));
    }
}

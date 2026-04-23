package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.ScenarioActionPolicy;

import java.util.Objects;

/**
 * Optional ad-hoc planner controls for exact validation, named coordination overrides,
 * and scenario legality.
 */
public record AdHocSimulationOptions(
        PlannerExactValidatorScripts validatorScripts,
        PlannerCoordinationPolicy coordinationPolicy,
        ScenarioActionPolicy scenarioActionPolicy
) {
    public static final AdHocSimulationOptions DEFAULT = new AdHocSimulationOptions(
            PlannerExactValidatorScripts.DEFAULT,
            PlannerCoordinationPolicy.NONE,
            ScenarioActionPolicy.ALLOW_ALL
    );

    public AdHocSimulationOptions(
            PlannerExactValidatorScripts validatorScripts,
            ScenarioActionPolicy scenarioActionPolicy
    ) {
        this(validatorScripts, PlannerCoordinationPolicy.NONE, scenarioActionPolicy);
    }

    public AdHocSimulationOptions(
            PlannerCoordinationPolicy coordinationPolicy,
            ScenarioActionPolicy scenarioActionPolicy
    ) {
        this(PlannerExactValidatorScripts.DEFAULT, coordinationPolicy, scenarioActionPolicy);
    }

    public AdHocSimulationOptions {
        validatorScripts = Objects.requireNonNull(validatorScripts, "validatorScripts");
        coordinationPolicy = Objects.requireNonNull(coordinationPolicy, "coordinationPolicy");
        scenarioActionPolicy = Objects.requireNonNull(scenarioActionPolicy, "scenarioActionPolicy");
    }

    PlannerExactValidatorScripts effectiveValidatorScripts() {
        return coordinationPolicy.applyTo(validatorScripts);
    }

    boolean usesDefaultExactValidation() {
        return PlannerExactValidatorScripts.DEFAULT.equals(validatorScripts) && coordinationPolicy.isDefault();
    }
}

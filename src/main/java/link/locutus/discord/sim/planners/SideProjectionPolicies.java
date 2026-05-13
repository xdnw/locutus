package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

public record SideProjectionPolicies(AttackChoicePolicy attackChoicePolicy) {
    public static final SideProjectionPolicies HEURISTIC = new SideProjectionPolicies(
            HeuristicAttackChoicePolicy.INSTANCE
    );

    public static final SideProjectionPolicies NO_DECLARATIONS = new SideProjectionPolicies(
            HeuristicAttackChoicePolicy.INSTANCE
    );

    public SideProjectionPolicies {
        if (attackChoicePolicy == null) {
            throw new IllegalArgumentException("attackChoicePolicy must not be null");
        }
    }

    public static SideProjectionPolicies heuristic() {
        return HEURISTIC;
    }

    public static SideProjectionPolicies noDeclarations() {
        return NO_DECLARATIONS;
    }

    public static SideProjectionPolicies objectiveDriven(StrategicObjective objective, SideOpeningSettings openingSettings) {
        return new SideProjectionPolicies(new ObjectiveDrivenAttackChoicePolicy(objective, openingSettings));
    }
}

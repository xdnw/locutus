package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicObjective;

public record SideProjectionPolicies(
        AttackChoicePolicy attackChoicePolicy,
        LaterDeclarationScoringPolicy laterDeclarationScoringPolicy
) {
    public static final SideProjectionPolicies HEURISTIC = new SideProjectionPolicies(
            HeuristicAttackChoicePolicy.INSTANCE,
            HeuristicLaterDeclarationScoringPolicy.INSTANCE
    );

    public static final SideProjectionPolicies NO_DECLARATIONS = new SideProjectionPolicies(
            HeuristicAttackChoicePolicy.INSTANCE,
            HeuristicLaterDeclarationScoringPolicy.INSTANCE
    );

    public SideProjectionPolicies {
        if (attackChoicePolicy == null) {
            throw new IllegalArgumentException("attackChoicePolicy must not be null");
        }
        if (laterDeclarationScoringPolicy == null) {
            throw new IllegalArgumentException("laterDeclarationScoringPolicy must not be null");
        }
    }

    public static SideProjectionPolicies heuristic() {
        return HEURISTIC;
    }

    public static SideProjectionPolicies noDeclarations() {
        return NO_DECLARATIONS;
    }

    public static SideProjectionPolicies objectiveDriven(StrategicObjective objective, SideOpeningSettings openingSettings) {
        return new SideProjectionPolicies(
                new ObjectiveDrivenAttackChoicePolicy(objective, openingSettings),
                new ObjectiveDrivenLaterDeclarationScoringPolicy(objective)
        );
    }

    public static SideProjectionPolicies objectiveAttackChoice(
            StrategicObjective objective,
            SideOpeningSettings openingSettings
    ) {
        return new SideProjectionPolicies(
                new ObjectiveDrivenAttackChoicePolicy(objective, openingSettings),
                HeuristicLaterDeclarationScoringPolicy.INSTANCE
        );
    }
}

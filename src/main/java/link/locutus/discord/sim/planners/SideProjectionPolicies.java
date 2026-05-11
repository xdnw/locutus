package link.locutus.discord.sim.planners;

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
}
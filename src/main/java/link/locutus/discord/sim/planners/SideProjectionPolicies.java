package link.locutus.discord.sim.planners;

public record SideProjectionPolicies(
        CounterDeclarationPolicy counterDeclarationPolicy,
        RedeclarationPolicy redeclarationPolicy,
        AttackChoicePolicy attackChoicePolicy
) {
    public static final SideProjectionPolicies HEURISTIC = new SideProjectionPolicies(
            HeuristicCounterDeclarationPolicy.INSTANCE,
            HeuristicRedeclarationPolicy.INSTANCE,
            HeuristicAttackChoicePolicy.INSTANCE
    );

        public static final SideProjectionPolicies NO_DECLARATIONS = new SideProjectionPolicies(
            NoDeclarationCounterPolicy.INSTANCE,
            NoDeclarationRedeclarationPolicy.INSTANCE,
            HeuristicAttackChoicePolicy.INSTANCE
        );

    public SideProjectionPolicies {
        if (counterDeclarationPolicy == null) {
            throw new IllegalArgumentException("counterDeclarationPolicy must not be null");
        }
        if (redeclarationPolicy == null) {
            throw new IllegalArgumentException("redeclarationPolicy must not be null");
        }
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
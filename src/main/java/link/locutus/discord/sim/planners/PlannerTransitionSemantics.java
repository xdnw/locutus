package link.locutus.discord.sim.planners;

/**
 * Optional planner-local transition semantics enabled for exact/scheduled state advancement.
 */
public record PlannerTransitionSemantics(
        boolean policyCooldown,
        boolean pendingBuys,
        boolean peaceOffers
) {
    public static final PlannerTransitionSemantics NONE = new PlannerTransitionSemantics(false, false, false);
    public static final PlannerTransitionSemantics ALL_OPTIONAL = new PlannerTransitionSemantics(true, true, true);
}

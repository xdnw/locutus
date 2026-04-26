package link.locutus.discord.sim;

/**
 * Minimal score surface for planner-local evaluation.
 *
 * <p>Objectives that only need per-team score totals can implement this view without requiring a
 * full {@link SimWorld} instance.</p>
 */
public interface TeamScoreView {
    void forEachNation(NationScoreConsumer consumer);

    @FunctionalInterface
    interface NationScoreConsumer {
        void accept(int nationId, int teamId, double score);
    }

    static TeamScoreView of(SimWorld world) {
        return consumer -> {
            for (SimNation nation : world.nations()) {
                consumer.accept(nation.nationId(), nation.teamId(), nation.score());
            }
        };
    }
}
package link.locutus.discord.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal score and value surface for planner-local evaluation.
 *
 * <p>Game score remains available for mechanics that need it, but objective terminal value is
 * supplied separately so score does not become a proxy for expected value.</p>
 */
public interface TeamScoreView {
    void forEachNation(NationScoreConsumer consumer);

    void forEachNationStrategicValue(NationValueConsumer consumer);

    @FunctionalInterface
    interface NationScoreConsumer {
        void accept(int nationId, int teamId, double score);
    }

    @FunctionalInterface
    interface NationValueConsumer {
        void accept(int nationId, int teamId, double value);
    }

    static TeamScoreView of(SimWorld world) {
        return new TeamScoreView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                for (SimNation nation : world.nations()) {
                    consumer.accept(nation.nationId(), nation.teamId(), nation.score());
                }
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                List<SimNation> nations = new ArrayList<>();
                for (SimNation nation : world.nations()) {
                    nations.add(nation);
                }
                for (SimNation nation : nations) {
                    double[] opponentScores = opposingScores(nations, nation.teamId());
                    StrategicAssetValue.StrategicRelevance relevance = StrategicAssetValue.relevanceForWarRange(
                            nation.cities(),
                            nation.score(),
                            nation.offSlotsUsed() + nation.defSlotsUsed(),
                            opponentScores.length,
                            index -> opponentScores[index]
                    );
                    consumer.accept(
                            nation.nationId(),
                            nation.teamId(),
                            StrategicAssetValue.contextualMilitaryValue(
                                    nation::units,
                                    nation::pendingBuys,
                                    nation::unitsBoughtToday,
                                    nation::dailyBuyCap,
                                    nation.researchBits(),
                                    StrategicAssetValue.ActiveWarContext.fromSlots(
                                            nation.offSlotsUsed(),
                                            nation.maxOffSlots(),
                                            nation.defSlotsUsed(),
                                            nation.offSlotsUsed() + nation.defSlotsUsed()
                                    ),
                                    relevance
                            ).totalValue()
                    );
                }
            }

            private double[] opposingScores(List<SimNation> nations, int teamId) {
                double[] scores = new double[(int) nations.stream().filter(nation -> nation.teamId() != teamId).count()];
                int index = 0;
                for (SimNation nation : nations) {
                    if (nation.teamId() == teamId) {
                        continue;
                    }
                    scores[index++] = nation.score();
                }
                return scores;
            }
        };
    }
}

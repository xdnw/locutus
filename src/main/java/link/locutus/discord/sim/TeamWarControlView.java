package link.locutus.discord.sim;

public interface TeamWarControlView extends StrategicValueView {
    record ControlComponentWeights(
            double controlOwnershipWeight,
            double activeWarTargetPressureWeight,
            double activeWarTacticalMomentumWeight,
            double activeWarForceWindowWeight,
            double slotDenialWeight,
            double controlRegimeWeight
    ) {
    }

    void forEachWarControl(WarControlConsumer consumer);

    default void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
    }

    default void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
    }

    default void forEachExternalTeamStrategicValue(ExternalTeamValueConsumer consumer) {
    }

    default double controlScoreForTeam(int teamId) {
        return TeamWarControlScorer.controlScoreForTeam(this, teamId);
    }

    default double activeWarStrategicScoreForTeam(int teamId, double targetPressureWeight, double tacticalMomentumWeight, double forceWindowWeight) {
        return TeamWarControlScorer.activeWarStrategicScoreForTeam(this, teamId, targetPressureWeight, tacticalMomentumWeight, forceWindowWeight);
    }

    default double activeWarSlotDenialScoreForTeam(int teamId) {
        return TeamWarControlScorer.activeWarSlotDenialScoreForTeam(this, teamId);
    }

    default double controlRegimeScoreForTeam(int teamId) {
        return TeamWarControlScorer.controlRegimeScoreForTeam(this, teamId);
    }

    default double controlCompositeScoreForTeam(int teamId, ControlComponentWeights weights) {
        return TeamWarControlScorer.controlCompositeScoreForTeam(this, teamId, weights);
    }

    @FunctionalInterface
    interface WarControlConsumer {
        void accept(
                int attackerTeamId,
                int defenderTeamId,
                int groundSuperiorityTeamId,
                int airSuperiorityTeamId,
                int blockadeTeamId,
                int attackerResistance,
                int defenderResistance
        );
    }

    @FunctionalInterface
    interface ActiveWarMetricConsumer {
        void accept(
                int attackerTeamId,
                int defenderTeamId,
                double targetPressure,
                double tacticalMomentum,
                double forceWindowAdvantage
        );
    }

    @FunctionalInterface
    interface ActiveWarSlotMetricConsumer {
        void accept(
                int attackerTeamId,
                int defenderTeamId,
                double attackerOffensiveSlotCost,
                double defenderDefensiveSlotDenial
        );
    }

    @FunctionalInterface
    interface ExternalTeamValueConsumer {
        void accept(int teamId, double value);
    }
}

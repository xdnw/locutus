package link.locutus.discord.sim;

public interface TeamProjectionView extends StrategicValueView {
    void forEachWarControl(WarControlConsumer consumer);

    default void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
    }

    default void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
    }

    default void forEachExternalTeamStrategicValue(ExternalTeamValueConsumer consumer) {
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
                double actionSpaceQuality
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
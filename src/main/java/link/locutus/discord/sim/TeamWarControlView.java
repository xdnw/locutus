package link.locutus.discord.sim;

public interface TeamWarControlView extends TeamScoreView {
    void forEachWarControl(WarControlConsumer consumer);

    default void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
    }

    default double controlScoreForTeam(int teamId) {
        double[] score = new double[1];
        forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
            int enemyTeamId = attackerTeamId == teamId ? defenderTeamId : attackerTeamId;
            score[0] += controlOwnerScore(groundControlTeamId, teamId, enemyTeamId, 4.0d);
            score[0] += controlOwnerScore(airSuperiorityTeamId, teamId, enemyTeamId, 5.0d);
            score[0] += controlOwnerScore(blockadeTeamId, teamId, enemyTeamId, 3.0d);
            if (attackerTeamId == teamId) {
                score[0] += Math.max(0, 100 - defenderResistance) * 0.05d;
            } else if (defenderTeamId == teamId) {
                score[0] += Math.max(0, 100 - attackerResistance) * 0.05d;
            }
        });
        return score[0];
    }

    default double activeWarStrategicScoreForTeam(int teamId, double targetPressureWeight, double futureWarLeverageWeight) {
        double[] score = new double[1];
        forEachActiveWarMetric((attackerTeamId, defenderTeamId, targetPressure, futureWarLeverage) -> {
            double value = (targetPressureWeight * targetPressure) + (futureWarLeverageWeight * futureWarLeverage);
            if (attackerTeamId == teamId) {
                score[0] += value;
            } else if (defenderTeamId == teamId) {
                score[0] -= value;
            }
        });
        return score[0];
    }

    default double controlRegimeScoreForTeam(int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(this, teamId);
        double totalValue = Math.max(1.0d, totals.ownValue() + totals.enemyValue());
        double strategicValueEdge = (totals.ownValue() - totals.enemyValue()) / totalValue;
        double[] score = new double[1];
        forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
            if (attackerTeamId != teamId && defenderTeamId != teamId) {
                return;
            }
            int enemyTeamId = attackerTeamId == teamId ? defenderTeamId : attackerTeamId;
            int ownControls = 0;
            int enemyControls = 0;
            if (groundControlTeamId == teamId) {
                ownControls++;
            } else if (groundControlTeamId == enemyTeamId) {
                enemyControls++;
            }
            if (airSuperiorityTeamId == teamId) {
                ownControls++;
            } else if (airSuperiorityTeamId == enemyTeamId) {
                enemyControls++;
            }
            if (blockadeTeamId == teamId) {
                ownControls++;
            } else if (blockadeTeamId == enemyTeamId) {
                enemyControls++;
            }

            int ownResistance = attackerTeamId == teamId ? attackerResistance : defenderResistance;
            int enemyResistance = attackerTeamId == teamId ? defenderResistance : attackerResistance;
            score[0] += StrategicAssetValue.controlRegimeScore(
                    ownResistance,
                    enemyResistance,
                    ownControls,
                    enemyControls,
                    strategicValueEdge
            );
        });
        return score[0];
    }

    private static double controlOwnerScore(int ownerTeamId, int teamId, int enemyTeamId, double value) {
        if (ownerTeamId == teamId) {
            return value;
        }
        if (ownerTeamId == enemyTeamId) {
            return -value;
        }
        return 0.0d;
    }

    @FunctionalInterface
    interface WarControlConsumer {
        void accept(
                int attackerTeamId,
                int defenderTeamId,
                int groundControlTeamId,
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
                double futureWarLeverage
        );
    }
}

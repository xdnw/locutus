package link.locutus.discord.sim;

/**
 * Canonical terminal control reducer for projecting one team's control-relevant war state.
 */
public final class StrategicControlReducer {
    private StrategicControlReducer() {
    }

    public record ControlWeights(
            double controlOwnershipWeight,
            double targetPressureWeight,
            double tacticalMomentumWeight,
            double followOnLeverageWeight,
            double slotDenialWeight,
            double durableControlWeight
    ) {
    }

    public record ControlComponents(
            double controlOwnership,
            double targetPressure,
            double tacticalMomentum,
            double followOnLeverage,
            double slotDenial,
            double durableControl
    ) {
        public double activeWarStrategicScore(
                double targetPressureWeight,
                double tacticalMomentumWeight,
                double followOnLeverageWeight
        ) {
            return (targetPressureWeight * targetPressure)
                    + (tacticalMomentumWeight * tacticalMomentum)
                    + (followOnLeverageWeight * followOnLeverage);
        }

        public double score(ControlWeights weights) {
            if (weights == null) {
                return 0d;
            }
            return (weights.controlOwnershipWeight() * controlOwnership)
                    + (weights.targetPressureWeight() * targetPressure)
                    + (weights.tacticalMomentumWeight() * tacticalMomentum)
                    + (weights.followOnLeverageWeight() * followOnLeverage)
                    + (weights.slotDenialWeight() * slotDenial)
                    + (weights.durableControlWeight() * durableControl);
        }
    }

    public static ControlComponents reduce(TeamWarControlView view, int teamId) {
        double[] components = new double[6];
        view.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
            int enemyTeamId = attackerTeamId == teamId ? defenderTeamId : attackerTeamId;
            components[0] += controlOwnerScore(groundSuperiorityTeamId, teamId, enemyTeamId, 4.0d);
            components[0] += controlOwnerScore(airSuperiorityTeamId, teamId, enemyTeamId, 5.0d);
            components[0] += controlOwnerScore(blockadeTeamId, teamId, enemyTeamId, 3.0d);

            if (attackerTeamId != teamId && defenderTeamId != teamId) {
                return;
            }
            int ownControls = 0;
            int enemyControls = 0;
            if (groundSuperiorityTeamId == teamId) {
                ownControls++;
            } else if (groundSuperiorityTeamId == enemyTeamId) {
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
            components[5] += StrategicAssetValue.controlRegimeScore(
                    ownResistance,
                    enemyResistance,
                    ownControls,
                    enemyControls
            );
        });

        view.forEachActiveWarMetric((attackerTeamId, defenderTeamId, targetPressure, tacticalMomentum, forceWindowAdvantage) -> {
            if (attackerTeamId == teamId) {
                components[1] += targetPressure;
                components[2] += tacticalMomentum;
                components[3] += forceWindowAdvantage;
            } else if (defenderTeamId == teamId) {
                components[1] -= targetPressure;
                components[2] -= tacticalMomentum;
                components[3] -= forceWindowAdvantage;
            }
        });

        view.forEachActiveWarSlotMetric((attackerTeamId, defenderTeamId, attackerOffensiveSlotCost, defenderDefensiveSlotDenial) -> {
            if (attackerTeamId == teamId) {
                components[4] += defenderDefensiveSlotDenial - attackerOffensiveSlotCost;
            } else if (defenderTeamId == teamId) {
                components[4] += attackerOffensiveSlotCost - defenderDefensiveSlotDenial;
            }
        });

        return new ControlComponents(
                components[0],
                components[1],
                components[2],
                components[3],
                components[4],
                components[5]
        );
    }

    public static double score(TeamWarControlView view, int teamId, ControlWeights weights) {
        return reduce(view, teamId).score(weights);
    }

    private static double controlOwnerScore(int ownerTeamId, int teamId, int enemyTeamId, double value) {
        if (ownerTeamId == teamId) {
            return value;
        }
        if (ownerTeamId == enemyTeamId) {
            return -value;
        }
        return 0d;
    }
}
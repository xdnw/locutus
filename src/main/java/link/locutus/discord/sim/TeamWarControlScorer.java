package link.locutus.discord.sim;

final class TeamWarControlScorer {
    private TeamWarControlScorer() {
    }

    static double controlScoreForTeam(TeamWarControlView view, int teamId) {
        double[] score = new double[1];
        view.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
            int enemyTeamId = attackerTeamId == teamId ? defenderTeamId : attackerTeamId;
            score[0] += controlOwnerScore(groundSuperiorityTeamId, teamId, enemyTeamId, 4.0d);
            score[0] += controlOwnerScore(airSuperiorityTeamId, teamId, enemyTeamId, 5.0d);
            score[0] += controlOwnerScore(blockadeTeamId, teamId, enemyTeamId, 3.0d);
        });
        return score[0];
    }

    static double activeWarStrategicScoreForTeam(
            TeamWarControlView view,
            int teamId,
            double targetPressureWeight,
            double tacticalMomentumWeight,
            double forceWindowWeight
    ) {
        double[] score = new double[1];
        view.forEachActiveWarMetric((attackerTeamId, defenderTeamId, targetPressure, tacticalMomentum, forceWindowAdvantage) -> {
            double value = (targetPressureWeight * targetPressure)
                    + (tacticalMomentumWeight * tacticalMomentum)
                    + (forceWindowWeight * forceWindowAdvantage);
            if (attackerTeamId == teamId) {
                score[0] += value;
            } else if (defenderTeamId == teamId) {
                score[0] -= value;
            }
        });
        return score[0];
    }

    static double activeWarSlotDenialScoreForTeam(TeamWarControlView view, int teamId) {
        double[] score = new double[1];
        view.forEachActiveWarSlotMetric((attackerTeamId, defenderTeamId, attackerOffensiveSlotCost, defenderDefensiveSlotDenial) -> {
            if (attackerTeamId == teamId) {
                score[0] += defenderDefensiveSlotDenial - attackerOffensiveSlotCost;
            } else if (defenderTeamId == teamId) {
                score[0] += attackerOffensiveSlotCost - defenderDefensiveSlotDenial;
            }
        });
        return score[0];
    }

    static double controlRegimeScoreForTeam(TeamWarControlView view, int teamId) {
        double[] score = new double[1];
        view.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
            if (attackerTeamId != teamId && defenderTeamId != teamId) {
                return;
            }
            int enemyTeamId = attackerTeamId == teamId ? defenderTeamId : attackerTeamId;
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
            score[0] += StrategicAssetValue.controlRegimeScore(
                    ownResistance,
                    enemyResistance,
                    ownControls,
                    enemyControls
            );
        });
        return score[0];
    }

    static double controlCompositeScoreForTeam(
            TeamWarControlView view,
            int teamId,
            TeamWarControlView.ControlComponentWeights weights
    ) {
        if (weights == null) {
            return 0d;
        }
        double score = 0d;
        score += weights.controlOwnershipWeight() * controlScoreForTeam(view, teamId);
        score += activeWarStrategicScoreForTeam(
                view,
                teamId,
                weights.activeWarTargetPressureWeight(),
                weights.activeWarTacticalMomentumWeight(),
                weights.activeWarForceWindowWeight()
        );
        score += weights.slotDenialWeight() * activeWarSlotDenialScoreForTeam(view, teamId);
        score += weights.controlRegimeWeight() * controlRegimeScoreForTeam(view, teamId);
        return score;
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
}

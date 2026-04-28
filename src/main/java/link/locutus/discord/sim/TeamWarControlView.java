package link.locutus.discord.sim;

public interface TeamWarControlView extends TeamScoreView {
    void forEachWarControl(WarControlConsumer consumer);

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
}
package link.locutus.discord.sim;

record StrategicValueTotals(double ownValue, double enemyValue) {
    static StrategicValueTotals of(StrategicValueView view, int teamId) {
        double[] totals = new double[2];
        view.forEachNationStrategicValue((nationId, nationTeamId, value) -> {
            if (nationTeamId == teamId) {
                totals[0] += value;
            } else {
                totals[1] += value;
            }
        });
        return new StrategicValueTotals(totals[0], totals[1]);
    }

    static double slotBalanceOf(StrategicValueView view, int teamId) {
        if (!(view instanceof TeamProjectionView projectionView)) {
            return 0d;
        }
        double[] total = new double[1];
        projectionView.forEachActiveWarSlotMetric((attackerTeamId, defenderTeamId, attackerOffensiveSlotCost, defenderDefensiveSlotDenial) -> {
            if (attackerTeamId == teamId) {
                total[0] += defenderDefensiveSlotDenial - attackerOffensiveSlotCost;
            } else if (defenderTeamId == teamId) {
                total[0] += attackerOffensiveSlotCost - defenderDefensiveSlotDenial;
            }
        });
        return total[0];
    }
}
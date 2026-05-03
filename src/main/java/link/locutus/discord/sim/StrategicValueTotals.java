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
}
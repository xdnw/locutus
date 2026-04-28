package link.locutus.discord.sim;

record ScoreTotals(double ownScore, double enemyScore) {
    static ScoreTotals of(TeamScoreView view, int teamId) {
        double[] totals = new double[2];
        view.forEachNation((nationId, nationTeamId, score) -> {
            if (nationTeamId == teamId) {
                totals[0] += score;
            } else {
                totals[1] += score;
            }
        });
        return new ScoreTotals(totals[0], totals[1]);
    }
}
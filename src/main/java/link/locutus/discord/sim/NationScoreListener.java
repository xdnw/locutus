package link.locutus.discord.sim;

@FunctionalInterface
interface NationScoreListener {
    void onScoreChanged(int nationId, double previousScore, double currentScore);
}
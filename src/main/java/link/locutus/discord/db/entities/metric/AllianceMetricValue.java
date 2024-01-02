package link.locutus.discord.db.entities.metric;

public class AllianceMetricValue {
    public final int alliance;
    public final AllianceMetric metric;
    public final long turn;
    public final double value;

    public AllianceMetricValue(int alliance, AllianceMetric metric, long turn, double value) {
        this.alliance = alliance;
        this.metric = metric;
        this.turn = turn;
        this.value = value;
    }
}

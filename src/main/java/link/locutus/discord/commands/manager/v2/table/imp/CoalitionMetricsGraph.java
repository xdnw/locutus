package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class CoalitionMetricsGraph extends SimpleTable<Void> {
    public static CoalitionMetricsGraph create(Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, coalition);
        return new CoalitionMetricsGraph(metricMap, metrics, startTurn, endTurn, coalitionName, coalition);
    }

    private final double[][] valuesByTurnByMetric;
    private final AllianceMetric[] metricsArr;
    private long minTurn;
    private long maxTurn;
    private final double[] buffer;

    public CoalitionMetricsGraph(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        this.valuesByTurnByMetric = AllianceMetric.toValueByTurnByMetric(metricMap, metrics, startTurn, endTurn, coalition);
        this.metricsArr = metrics.toArray(new AllianceMetric[0]);
        this.minTurn = Long.MAX_VALUE;
        this.maxTurn = 0;

        for (double[] valuesByTurn : valuesByTurnByMetric) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        this.buffer = new double[metrics.size()];

        String[] labels = new String[metrics.size()];
        {
            int i = 0;
            for (AllianceMetric metric : metrics) {
                labels[i++] = metric.name();
            }
        }

        String title = coalitionName + " metrics";
        setTitle(title);
        setLabelX("turn");
        setLabelY("value");
        setLabels(labels);

        writeData();
    }

    @Override
    public long getOrigin() {
        return minTurn;
    }

    @Override
    public void add(long turn, Void ignore) {
        int turnRelative = (int) (turn - minTurn);
        for (int i = 0; i < metricsArr.length; i++) {
            double value = valuesByTurnByMetric[i][turnRelative];
            if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                buffer[i] = value;
            }
        }
        add(turnRelative, buffer);
    }

    @Override
    public SimpleTable<Void> writeData() {
        for (long turn = minTurn; turn <= maxTurn; turn++) {
            add(turn, (Void) null);
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.SI_UNIT;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}

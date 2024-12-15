package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MultiCoalitionMetricGraph extends SimpleTable<Void> {
    private final double[][] valuesByTurnByCoalition;
    private final AllianceMetric metric;
    private long minTurn;
    private long maxTurn;
    private final double[] buffer;

    public static MultiCoalitionMetricGraph create(AllianceMetric metric, long cutoffTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        return new MultiCoalitionMetricGraph(metric, cutoffTurn, TimeUtil.getTurn(), coalitionNames, coalitions);
    }

    public MultiCoalitionMetricGraph(AllianceMetric metric, long startTurn, long endTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");
        this.metric = metric;

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(Collections.singleton(metric), startTurn, endTurn, coalitions);
        this.valuesByTurnByCoalition = AllianceMetric.toValueByTurnByCoalition(metricMap, metric, startTurn, endTurn, coalitions);

        this.minTurn = Long.MAX_VALUE;
        this.maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByCoalition) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        this.buffer = new double[coalitions.length];

        String[] labels;
        if (coalitionNames == null || coalitionNames.size() != coalitions.length) {
            labels = new String[coalitions.length];
            for (int i = 0; i < labels.length; i++) labels[i] = "coalition " + (i + 1);
        } else {
            labels = coalitionNames.toArray(new String[0]);
        }
        String title = metric + " by turn";
        setTitle(title);
        setLabelX("turn");
        setLabelY(metric.name());
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
        for (int i = 0; i < valuesByTurnByCoalition.length; i++) {
            double value = valuesByTurnByCoalition[i][turnRelative];
            if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                buffer[i] = value;
            }
        }
        add(turnRelative, buffer);
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (long turn = minTurn; turn <= maxTurn; turn++) {
            add(turn, (Void) null);
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.TURN_TO_DATE;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return metric.getFormat();
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}

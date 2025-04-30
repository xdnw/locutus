package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;

public class OrbisMetricGraph extends SimpleTable<Void> {
    private final long minTurn;
    private final long maxTurn;
    private final Map<Long, Map<OrbisMetric, Double>> dataByTurn;
    private final List<OrbisMetric> metricList;
    private final double[] buffer;
    private final boolean hasTurn;

    public OrbisMetricGraph(Set<OrbisMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end) {
        NationDB db = Locutus.imp().getNationDB();
        OrbisMetric.update(db);

        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;

        this.hasTurn = metrics.stream().anyMatch(OrbisMetric::isTurn);
        boolean hasDay = metrics.stream().anyMatch(f -> !f.isTurn());
        if (hasTurn && hasDay) throw new IllegalArgumentException("Cannot mix turn and day metrics");

        Map<OrbisMetric, Map<Long, Double>> metricData = db.getMetrics(metrics, start, end);
        this.dataByTurn = new HashMap<>();

        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (Map.Entry<OrbisMetric, Map<Long, Double>> entry : metricData.entrySet()) {
            OrbisMetric metric = entry.getKey();
            Map<Long, Double> turnValue = entry.getValue();
            for (Map.Entry<Long, Double> turnValueEntry : turnValue.entrySet()) {
                long turn = turnValueEntry.getKey();
                double value = turnValueEntry.getValue();
                dataByTurn.computeIfAbsent(turn, f -> new EnumMap<>(OrbisMetric.class)).put(metric, value);
                minTurn = Math.min(minTurn, turn);
                maxTurn = Math.max(maxTurn, turn);
            }
        }
        if (maxTurn == 0) throw new IllegalArgumentException("No data found");

        this.minTurn = minTurn;
        this.maxTurn = maxTurn;

        this.metricList = new ArrayList<>(metrics);
        metricList.sort(Comparator.comparing(OrbisMetric::ordinal));

        String turnOrDayStr = hasTurn ? "turn" : "day";
        String title = (metrics.size() == 1 ? metrics.iterator().next().toString() : "Game Stats") + " by " + turnOrDayStr;
        String[] labels = metricList.stream().map(f -> f.name().toLowerCase().replace("_", " ")).toArray(String[]::new);
        this.buffer = new double[labels.length];

        setTitle(title);
        setLabelX(turnOrDayStr);
        setLabelY("Resource");
        setLabels(labels);

        writeData();
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (long turn = minTurn; turn <= maxTurn; turn++) {
            add(turn, (Void) null);
        }
        return this;
    }

    @Override
    public void add(long turn, Void ignore) {
        int turnRelative = (int) (turn - minTurn);
        Map<OrbisMetric, Double> turnData = dataByTurn.get(turn);
        if (turnData != null) {
            for (int i = 0; i < metricList.size(); i++) {
                double value = turnData.getOrDefault(metricList.get(i), 0d);
                buffer[i] = value;
            }
        }
        add(turnRelative, buffer);
    }

    @Override
    public long getOrigin() {
        return minTurn;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return hasTurn ? TimeFormat.TURN_TO_DATE : TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}
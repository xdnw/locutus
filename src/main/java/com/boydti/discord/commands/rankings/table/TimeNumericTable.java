package com.boydti.discord.commands.rankings.table;

import com.boydti.discord.commands.manager.v2.impl.pw.binding.NationMetric;
import com.boydti.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationList;
import com.boydti.discord.pnw.SimpleNationList;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.math.CIEDE2000;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.erichseifert.gral.data.Column;
import de.erichseifert.gral.data.DataSeries;
import de.erichseifert.gral.data.DataSource;
import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.data.Row;
import de.erichseifert.gral.data.statistics.Statistics;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Orientation;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.XYPlot;
import de.erichseifert.gral.plots.axes.AxisRenderer;
import de.erichseifert.gral.plots.lines.DefaultLineRenderer2D;
import de.erichseifert.gral.plots.lines.LineRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class TimeNumericTable<T> {

    public static TimeNumericTable create(String title, Set<NationMetricDouble> metrics, Collection<Alliance> coalition, NationMetricDouble groupBy, boolean total, boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = new HashSet<>();
        for (Alliance alliance : coalition) {
            nations.addAll(alliance.getNations(removeVM, removeInactiveM, removeApps));
        }
        return create(title, metrics, nations, groupBy, total);
    }

    public static TimeNumericTable create(String titlePrefix, NationMetricDouble metrics, List<Set<Alliance>> coalitions, NationMetricDouble groupBy, boolean total, boolean removeVM, int removeInactiveM, boolean removeApps) {
        List<NationList> coalitionNations = new ArrayList<>();
        List<String> coalitionNames = new ArrayList<>();

        for (Set<Alliance> coalition : coalitions) {
            String coalitionName = coalition.stream().map(f -> f.getName()).collect(Collectors.joining(","));
            Set<DBNation> nations = coalition.stream().flatMap(f -> f.getNations(removeVM, removeInactiveM, removeApps).stream()).collect(Collectors.toSet());

            coalitionNations.add(new SimpleNationList(nations));
            coalitionNames.add(coalitionName);

        }

        return create(titlePrefix, metrics, coalitionNations, coalitionNames, groupBy, total);
    }

    public static TimeNumericTable create(String titlePrefix, NationMetricDouble metric, List<NationList> coalitions, List<String> coalitionNames, NationMetricDouble groupBy, boolean total) {
        String[] labels = coalitionNames.toArray(new String[0]);

        Function<DBNation, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        List<Map<Integer, NationList>> byTierList = new ArrayList<>();
        Set<DBNation> allNations = new HashSet<>();
        for (NationList coalition : coalitions) {
            allNations.addAll(coalition.getNations());
            byTierList.add(coalition.groupBy(groupByInt));
        }
        SimpleNationList allNationsList = new SimpleNationList(allNations);


        int min = allNationsList.stream(groupByInt).min(Integer::compare).get();
        int max = allNationsList.stream(groupByInt).max(Integer::compare).get();

        double[] buffer = new double[coalitions.size()];

        String labelY = labels.length == 1 ? labels[0] : "metric";
        titlePrefix += (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        TimeNumericTable<Void> table = new TimeNumericTable<>(titlePrefix, groupBy.getName(), labelY, labels) {
            @Override
            public void add(long key, Void ignore) {
                for (int i = 0; i < byTierList.size(); i++) {
                    double valueTotal = 0;
                    int count = 0;

                    Map<Integer, NationList> byTier = byTierList.get(i);
                    NationList nations = byTier.get((int) key);
                    if (nations == null) {
                        buffer[i] = 0;
                        continue;
                    }
                    for (DBNation nation : nations.getNations()) {
                        if (nation.hasUnsetMil()) continue;
                        count++;
                        valueTotal += metric.apply(nation);
                    }
                    if (count > 1 && !total) {
                        valueTotal /= count;
                    }
                    buffer[i] = valueTotal;
                }
                add(key, buffer);
            }
        };

        for (int key = min; key <= max; key++) {
            table.add(key, (Void) null);
        }
        return table;
    }

    public static TimeNumericTable create(String title, Set<NationMetricDouble> metrics, Set<DBNation> coalition, NationMetricDouble groupBy, boolean total) {
        NationMetricDouble[] metricsArr = metrics.toArray(new NationMetricDouble[0]);
        String[] labels = metrics.stream().map(NationMetric::getName).toArray(String[]::new);

        NationList coalitionList = new SimpleNationList(coalition);

        Function<DBNation, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        Map<Integer, NationList> byTier = coalitionList.groupBy(groupByInt);
        int min = coalition.isEmpty() ? 0 : coalitionList.stream(groupByInt).min(Integer::compare).get();
        int max = coalition.isEmpty() ? 0 : coalitionList.stream(groupByInt).max(Integer::compare).get();

        double[] buffer = new double[metricsArr.length];
        String labelY = labels.length == 1 ? labels[0] : "metric";
        title += (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        TimeNumericTable<NationList> table = new TimeNumericTable<>(title, groupBy.getName(), labelY, labels) {
            @Override
            public void add(long key, NationList nations) {
                if (nations == null) {
                    Arrays.fill(buffer, 0);
                } else {
                    for (int i = 0; i < metricsArr.length; i++) {
                        NationMetricDouble metric = metricsArr[i];
                        double valueTotal = 0;
                        int count = 0;

                        for (DBNation nation : nations.getNations()) {
                            if (nation.hasUnsetMil()) continue;
                            count++;
                            valueTotal += metric.apply(nation);
                        }
                        if (count > 1 && !total) {
                            valueTotal /= count;
                        }

                        buffer[i] = valueTotal;
                    }
                }
                add(key, buffer);
            }
        };

        for (int key = min; key <= max; key++) {
            NationList nations = byTier.get(key);
            table.add(key, nations);
        }
        return table;
    }

    private final String name;
    protected final DataTable data;
    private final int amt;
    private final String[] labels;
    private final String labelX, labelY;

    public String getName() {
        return name;
    }

    public TimeNumericTable(String title, String labelX, String labelY, String... seriesLabels) {
        this.name = title;
        this.amt = seriesLabels.length;
        this.labels = seriesLabels;
        this.labelX = labelX;
        this.labelY = labelY;
        if (this.labelY == null && title != null) labelY = title;
        List<Class<? extends Comparable<?>>> types = new ArrayList<>();
        types.add(Long.class);
        for (int i = 0; i < amt; i++) types.add(Double.class);
        this.data = new DataTable(types.toArray(new Class[0]));
    }

    public abstract void add(long day, T cost);

    public void add(long day, double... values) {
        Comparable<?>[] arr = new Comparable<?>[values.length + 1];
        arr[0] = day;
        for (int i = 0; i < values.length; i++) arr[i + 1] = values[i];
        data.add(arr);
    }

    public DataTable getData() {
        return data;
    }

    public JsonObject toHtmlJson() {
        JsonObject obj = new JsonObject();
        JsonArray labelsArr = new JsonArray();

        for (String label : this.labels) labelsArr.add(label);

        Column col1 = data.getColumn(0);
        double minX = col1.getStatistics(Statistics.MIN);
        double maxX = col1.getStatistics(Statistics.MAX);

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < amt; i++) {
            Column col = data.getColumn(i + 1);
            minY = Math.min(minY, col.getStatistics(Statistics.MIN));
            maxY = Math.max(maxY, col.getStatistics(Statistics.MAX));
        }

        obj.addProperty("title", name);
        obj.addProperty("x", labelX);
        obj.addProperty("y", labelY);
        obj.add("labels", labelsArr);

        JsonArray[] arrays = new JsonArray[amt + 1];
        for (int i = 0; i < arrays.length; i++) arrays[i] = new JsonArray();

        for (int i = 0; i < data.getRowCount(); i++) {
            Row row = data.getRow(i);
            for (int j = 0; j < row.size(); j++) {
                Number val = (Number) row.get(j);
                arrays[j].add(val);
            }
        }
        JsonArray dataJson = new JsonArray();
        for (JsonArray arr : arrays) dataJson.add(arr);

        obj.add("data", dataJson);
        return obj;
    }

    public TimeNumericTable<T> convertTurnsToEpochSeconds(long turnStart) {
        for (int i = 0; i < data.getRowCount(); i++) {
            Row row = data.getRow(i);
            long turn = turnStart + ((Number) row.get(0)).longValue();
            long time = TimeUtil.getTimeFromTurn(turn) / 1000L;
            data.set(0, i, time);
        }
        return this;
    }

    public XYPlot getTable() {
        DataSource[] series = new DataSource[amt];
        for (int i = 0; i < amt; i++) {
            DataSource source = new DataSeries(this.labels[i], data, 0, i + 1);
            series[i] = source;
        }

        // Create new xy-plot
        XYPlot plot = new XYPlot(series);

        Column col1 = data.getColumn(0);
        plot.getAxis(XYPlot.AXIS_X).setRange(
                col1.getStatistics(Statistics.MIN),
                col1.getStatistics(Statistics.MAX)
        );

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < amt; i++) {
            Column col = data.getColumn(i + 1);
            min = Math.min(min, col.getStatistics(Statistics.MIN));
            max = Math.max(max, col.getStatistics(Statistics.MAX));
        }
        plot.getAxis(XYPlot.AXIS_Y).setRange(min,max);


        // Format  plot
        plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
        plot.getTitle().setText(name);
        plot.setLegendVisible(true);
        plot.setBackground(Color.WHITE);

        // Format legend
        plot.getLegend().setOrientation(Orientation.HORIZONTAL);

        // Format plot area
        ((XYPlot.XYPlotArea2D) plot.getPlotArea()).setMajorGridX(false);
        ((XYPlot.XYPlotArea2D) plot.getPlotArea()).setMinorGridY(true);

        // Format axes (set scale and spacings)
        AxisRenderer axisRendererX = plot.getAxisRenderer(XYPlot.AXIS_X);
        axisRendererX.setTicksAutoSpaced(true);
        axisRendererX.setMinorTicksCount(4);
        AxisRenderer axisRendererY = plot.getAxisRenderer(XYPlot.AXIS_Y);
        axisRendererY.setTicksAutoSpaced(true);
        axisRendererY.setMinorTicksCount(4);

        Set<Color> colors = new HashSet<>();
        switch (amt) {
            case 6:
                colors.add(Color.ORANGE);
            case 5:
                colors.add(Color.CYAN);
            case 4:
                colors.add(Color.MAGENTA);
            case 3:
                colors.add(Color.GREEN);
            case 2:
                colors.add(Color.BLUE);
            case 1:
                colors.add(Color.RED);
                break;
            default:
                for (int i = 0; i < amt; i++) {
                    Color color = CIEDE2000.randomColor(13 + amt, Color.WHITE, colors);
                    colors.add(color);
                }
                break;
        }

        int i = 0;
        for (Color color : colors) {
            DataSource dataA = series[i++];
            plot.setPointRenderers(dataA, null);
            LineRenderer lineA = new DefaultLineRenderer2D();
            lineA.setColor(color);
            plot.setLineRenderers(dataA, lineA);
        }

        return plot;
    }

    public byte[] write() throws IOException {
        XYPlot plot = getTable();

        DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(plot, baos, 1400, 600);
        return baos.toByteArray();
    }

    public void write(MessageChannel channel) throws IOException {
        RateLimitUtil.queue(channel.sendFile(write(), ("img.png")));
    }
}

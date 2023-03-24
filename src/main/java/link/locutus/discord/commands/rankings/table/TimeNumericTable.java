package link.locutus.discord.commands.rankings.table;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.erichseifert.gral.data.*;
import de.erichseifert.gral.data.statistics.Statistics;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Orientation;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.XYPlot;
import de.erichseifert.gral.plots.axes.AxisRenderer;
import de.erichseifert.gral.plots.lines.DefaultLineRenderer2D;
import de.erichseifert.gral.plots.lines.LineRenderer;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.Attribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.CIEDE2000;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class TimeNumericTable<T> {

    protected final DataTable data;
    private final String name;
    private final int amt;
    private final String[] labels;
    private final String labelX, labelY;

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

    public static TimeNumericTable<Void> createForContinents(Set<Continent> continents, long start, long end) {
        String title = "Radiation by turn";
        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = (end >= TimeUtil.getTimeFromTurn(TimeUtil.getTurn())) ? TimeUtil.getTurn() : TimeUtil.getTimeFromTurn(end);
        Map<Long, Map<Continent, Double>> radsbyTurn = Locutus.imp().getNationDB().getRadiationByTurns();
        List<Continent> continentsList = new ArrayList<>(continents);
        String[] labels = new String[continents.size() + 1];
        for (int i = 0; i < continentsList.size(); i++) {
            labels[i] = continentsList.get(i).name();
        }
        labels[labels.length - 1] = "Global";

        double[] buffer = new double[labels.length];

        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", "Radiation", labels) {

            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - turnStart);
                Map<Continent, Double> radsByCont = radsbyTurn.get(turn);
                if (radsByCont != null) {
                    for (int i = 0; i < continentsList.size(); i++) {
                        double rads = radsByCont.getOrDefault(continentsList.get(i), 0d);
                        buffer[i] = rads;
                    }
                    double total = 0;
                    for (double val : radsByCont.values()) total += val;
                    buffer[buffer.length - 1] = total / 5d;
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = turnStart; turn <= turnEnd; turn++) {
            table.add(turn, (Void) null);
        }
        return table;
    }

    /*

            Set<DBNation> nations = coalition.stream().flatMap(f -> f.getNations(removeVM, removeInactiveM, removeApps).stream()).collect(Collectors.toSet());
     */
    public static Set<DBNation> toNations(DBAlliance alliance, boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = alliance.getNations(removeVM, removeInactiveM, removeApps);
        nations.removeIf(DBNation::hasUnsetMil);
        return nations;
    }

    public static List<String> toCoalitionNames(List<Set<DBAlliance>> coalitions) {
        List<String> result = new ArrayList<>();
        for (Set<DBAlliance> coalition : coalitions) {
            String coalitionName = coalition.stream().map(DBAlliance::getName).collect(Collectors.joining(","));
            result.add(coalitionName);
        }
        return result;
    }

    public static List<List<DBNation>> toNations(List<Set<DBAlliance>> toNationCoalitions, boolean removeVM, int removeInactiveM, boolean removeApps) {
        List<List<DBNation>> result = new ArrayList<>();
        for (Set<DBAlliance> alliances : toNationCoalitions) {
            List<DBNation> nations = alliances.stream().flatMap(f -> f.getNations(removeVM, removeInactiveM, removeApps).stream()).collect(Collectors.toList());
            result.add(nations);
        }
        return result;
    }

    public static TimeNumericTable create(String title, Set<NationAttributeDouble> metrics, Collection<DBAlliance> alliances, NationAttributeDouble groupBy, boolean total, boolean removeVM, int removeActiveM, boolean removeApps) {
//        List<String> coalitionNames = alliances.stream().map(f -> f.getName()).collect(Collectors.toList());
        List<Set<DBAlliance>> aaSingleton = Collections.singletonList(new HashSet<>(alliances));
        List<DBNation> nations = toNations(aaSingleton, removeVM, removeActiveM, removeApps).get(0);
        return create(title, (Set) metrics, nations, groupBy, total);
    }

    public static <T> TimeNumericTable create(String title, Set<Attribute<T, Double>> metrics, Collection<T> coalition, Attribute<T, Double> groupBy, boolean total) {
        Set<T> nations = new HashSet<>(coalition);
        return create(title, metrics, nations, groupBy, total);
    }

    public static <T> TimeNumericTable create(String titlePrefix, Attribute<T, Double> metric, List<List<T>> coalitions, List<String> coalitionNames, Attribute<T, Double> groupBy, boolean total) {
        String[] labels = coalitionNames.toArray(new String[0]);

        Function<T, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        List<Map<Integer, List<T>>> byTierList = new ArrayList<>();
        Set<T> allNations = new HashSet<>();
        for (List<T> coalition : coalitions) {
            allNations.addAll(coalition);
            Map<Integer, List<T>> byTierListCoalition = new HashMap<>();
            for (T nation : coalition) {
                Integer group = groupByInt.apply(nation);
                byTierListCoalition.computeIfAbsent(group, f -> new ArrayList<>()).add(nation);
            }
            byTierList.add(byTierListCoalition);
        }
//        SimpleNationList allNationsList = new SimpleNationList(allNations);


        int min = allNations.stream().map(groupByInt).min(Integer::compare).get();
        int max = allNations.stream().map(groupByInt).max(Integer::compare).get();

        double[] buffer = new double[coalitions.size()];

        String labelY = labels.length == 1 ? labels[0] : "metric";
        titlePrefix += (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        TimeNumericTable<Void> table = new TimeNumericTable<>(titlePrefix, groupBy.getName(), labelY, labels) {
            @Override
            public void add(long key, Void ignore) {
                for (int i = 0; i < byTierList.size(); i++) {
                    double valueTotal = 0;
                    int count = 0;

                    Map<Integer, List<T>> byTier = byTierList.get(i);
                    List<T> nations = byTier.get((int) key);
                    if (nations == null) {
                        buffer[i] = 0;
                        continue;
                    }
                    for (T nation : nations) {
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

    public static <T> TimeNumericTable create(String title, Set<Attribute<T, Double>> metrics, Set<T> coalition, Attribute<T, Double> groupBy, boolean total) {
        List<Attribute<T, Double>> metricsList = new ArrayList<>(metrics);
        String[] labels = metrics.stream().map(Attribute::getName).toArray(String[]::new);

        Function<T, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        Map<Integer, List<T>> byTier = new HashMap<>();
        for (T t : coalition) {
            int tier = groupByInt.apply(t);
            byTier.computeIfAbsent(tier, f -> new ArrayList<>()).add(t);
        }
        int min = coalition.isEmpty() ? 0 : coalition.stream().map(groupByInt).min(Integer::compare).get();
        int max = coalition.isEmpty() ? 0 : coalition.stream().map(groupByInt).max(Integer::compare).get();

        double[] buffer = new double[metricsList.size()];
        String labelY = labels.length == 1 ? labels[0] : "metric";
        title += (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        TimeNumericTable<List<T>> table = new TimeNumericTable<>(title, groupBy.getName(), labelY, labels) {
            @Override
            public void add(long key, List<T> nations) {
                if (nations == null) {
                    Arrays.fill(buffer, 0);
                } else {
                    for (int i = 0; i < metricsList.size(); i++) {
                        Attribute<T, Double> metric = metricsList.get(i);
                        double valueTotal = 0;
                        int count = 0;

                        for (T nation : nations) {
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
            List<T> nations = byTier.get(key);
            table.add(key, nations);
        }
        return table;
    }

    public String getName() {
        return name;
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
        plot.getAxis(XYPlot.AXIS_Y).setRange(min, max);


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

    public void write(IMessageIO channel) throws IOException {
        channel.create().file("img.png", write()).send();
    }
}

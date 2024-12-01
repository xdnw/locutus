package link.locutus.discord.commands.manager.v2.table;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.erichseifert.gral.data.*;
import de.erichseifert.gral.data.statistics.Statistics;
import de.erichseifert.gral.graphics.Drawable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.graphics.Orientation;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.XYPlot;
import de.erichseifert.gral.plots.axes.AxisRenderer;
import de.erichseifert.gral.plots.colors.ColorMapper;
import de.erichseifert.gral.plots.legends.AbstractLegend;
import de.erichseifert.gral.plots.lines.DefaultLineRenderer2D;
import de.erichseifert.gral.plots.lines.LineRenderer;
import gg.jte.generated.precompiled.data.JtebarchartdatasrcGenerated;
import gg.jte.generated.precompiled.data.JtetimechartdatasrcGenerated;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.Attribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class TimeNumericTable<T> {

    protected DataTable data;
    private String name;
    private int amt;
    private String[] labels;
    private String labelX;
    private String labelY;
    private boolean isBar;

    public TimeNumericTable(String title, String labelX, String labelY, String... seriesLabels) {
        setTitle(title);
        setLabelX(labelX);
        setLabelY(labelY);
        setLabels(seriesLabels);
    }

    public void setTitle(String name) {
        this.name = name;
    }

    public void setLabelX(String labelX) {
        this.labelX = labelX;
    }

    public void setLabelY(String labelY) {
        this.labelY = labelY;
        if (this.labelY == null && this.name != null) labelY = name;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
        this.amt = labels.length;
        List<Class<? extends Comparable<?>>> types = new ArrayList<>();
        types.add(Long.class);
        for (int i = 0; i < amt; i++) types.add(Double.class);
        this.data = new DataTable(types.toArray(new Class[0]));
    }

    public TimeNumericTable<T> setBar(boolean bar) {
        isBar = bar;
        return this;
    }

    public boolean isBar() {
        return isBar;
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

    public WebGraph toHtmlJson(TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) {
        return toHtmlJson(labels, data, amt, name, labelX, labelY, timeFormat, numberFormat, origin);
    }

    public static WebGraph toHtmlJson(String[] labels, DataTable data, int amt, String name, String labelX, String labelY, TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) {
        WebGraph graph = new WebGraph();

        graph.time_format = timeFormat;
        graph.number_format = numberFormat;

        Column col1 = data.getColumn(0);
//        double minX = col1.getStatistics(Statistics.MIN);
//        double maxX = col1.getStatistics(Statistics.MAX);

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < amt; i++) {
            Column col = data.getColumn(i + 1);
            minY = Math.min(minY, col.getStatistics(Statistics.MIN));
            maxY = Math.max(maxY, col.getStatistics(Statistics.MAX));
        }

        graph.title = name;
        graph.x = labelX;
        graph.y = labelY;
        graph.labels = labels;


        graph.data = new ObjectArrayList<>();
        for (int i = 0; i < amt + 1; i++) graph.data.add(new ObjectArrayList<>());

        for (int i = 0; i < data.getRowCount(); i++) {
            Row row = data.getRow(i);
            for (int j = 0; j < row.size(); j++) {
                Number val = (Number) row.get(j);
                graph.data.get(j).add(val);
            }
        }
        return graph;
    }

    public TimeNumericTable(JsonObject json) {
        this.name = json.get("title").getAsString();
        this.labelX = json.get("x").getAsString();
        this.labelY = json.get("y").getAsString();

        JsonArray labelsArr = json.getAsJsonArray("labels");
        this.labels = new String[labelsArr.size()];
        for (int i = 0; i < labelsArr.size(); i++) {
            this.labels[i] = labelsArr.get(i).getAsString();
        }
        this.amt = labels.length;
        List<Class<? extends Comparable<?>>> types = new ArrayList<>();
        types.add(Long.class);
        for (int i = 0; i < amt; i++) types.add(Double.class);
        this.data = new DataTable(types.toArray(new Class[0]));

        JsonArray dataJson = json.getAsJsonArray("data");
        for (int i = 0; i < dataJson.size(); i++) {
            JsonArray rowArr = dataJson.get(i).getAsJsonArray();
            Comparable<?>[] row = new Comparable<?>[rowArr.size()];
            for (int j = 0; j < rowArr.size(); j++) {
                row[j] = (Comparable<?>) rowArr.get(j).getAsNumber();
            }
            this.data.add(row);
        }
    }

    public List<List<String>> toSheetRows() {
        return toSheetRows(labels, data, name, labelX, labelY);
    }
    public static List<List<String>> toSheetRows(String[] labels, DataTable data, String name, String labelX, String labelY) {
        List<List<String>> rows = new ArrayList<>();
        List<String> header = new ArrayList<>();
        header.add(labelX);
        for (String label : labels) {
            header.add(labelY + "(" + label + ")");
        }
        rows.add(header);

        for (int i = 0; i < data.getRowCount(); i++) {
            Row row = data.getRow(i);
            List<String> sheetRow = new ArrayList<>();
            for (int j = 0; j < row.size(); j++) {
                Number val = (Number) row.get(j);
                sheetRow.add(val == null ? "" : val.toString());
            }
            rows.add(sheetRow);
        }
        return rows;
    }

    public String toCsv() {
        List<List<String>> rows = toSheetRows();
        return StringMan.toCsv(rows);
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

    public TimeNumericTable<T> convertDaysToEpochSeconds(long dayStart) {
        for (int i = 0; i < data.getRowCount(); i++) {
            Row row = data.getRow(i);
            long day = dayStart + ((Number) row.get(0)).longValue();
            long time = TimeUtil.getTimeFromDay(day) / 1000L;
            data.set(0, i, time);
        }
        return this;
    }

    public XYPlot getTable(TimeFormat timeFormat, TableNumberFormat numberFormat) {
        DataSource[] series = new DataSource[amt];
        for (int i = 0; i < amt; i++) {
            DataSource source = new DataSeries(this.labels[i], data, 0, i + 1);
            series[i] = source;
        }

        DataTable barData = null;

        // Create new xy-plot
        XYPlot plot;
        if (isBar) {
            barData = new DataTable(Double.class, Double.class, String.class);

            int numYTypes = data.getColumnCount() - 1;
            double widthPerRow = 1d / (numYTypes);
            for (int i = 0; i < data.getRowCount(); i++) {
                Row row = data.getRow(i);
                long timeData = ((Number) row.get(0)).longValue();
                String timeStr = timeFormat.toString(timeData);

                for (int j = 0; j < numYTypes; j++) {
                    double val = ((Number) row.get(j + 1)).doubleValue();

                    double x = i + (j * widthPerRow);
                    Comparable[] newRow = new Comparable[3];
                    newRow[0] = x;
                    newRow[1] = val;
                    newRow[2] = timeStr;

                    barData.add(newRow);

                    // Dont repeat time
                    timeStr = "";
                }

            }

            plot = new BarPlot(barData);
        } else {
            plot = new XYPlot(series);
        }

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
        if (numberFormat == TableNumberFormat.PERCENTAGE_ONE) {
            min = 0;
            max = Math.min(max, 1);
        } else if (numberFormat == TableNumberFormat.PERCENTAGE_100) {
            min = 0;
            max = Math.min(max, 100);
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
        axisRendererX.setTickLabelFormat(MathMan.toFormat(timeFormat::toString));

        AxisRenderer axisRendererY = plot.getAxisRenderer(XYPlot.AXIS_Y);
        axisRendererY.setTicksAutoSpaced(true);
        axisRendererY.setMinorTicksCount(4);
        axisRendererY.setTickLabelFormat(MathMan.toFormat(numberFormat::toString));

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
                    if (color != null) {
                        colors.add(color);
                    } else {
                        break;
                    }
                }
                break;
        }

        if (isBar) {
            List<Color> colorList = new ArrayList<>(colors);
            BarPlot barPlot = (BarPlot) plot;
            barPlot.setBarWidth(1d / amt);
            BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(barData).get(0);
            ColorMapper mapper = new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    int column = number.intValue();
                    return colorList.get(column % colorList.size());
                }

                @Override
                public Mode getMode() {
                    return Mode.CIRCULAR;
                }
            };
            pointRenderer.setColor(mapper);
            pointRenderer.setBorderStroke(new BasicStroke(1f));
            pointRenderer.setBorderColor(Color.LIGHT_GRAY);
            pointRenderer.setValueVisible(true);
            pointRenderer.setValueColumn(2);
            pointRenderer.setValueLocation(Location.NORTH);
            pointRenderer.setValueRotation(90);
            pointRenderer.setValueColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return Color.BLACK;
                }

                @Override
                public ColorMapper.Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setValueFont(Font.decode(null).deriveFont(10.0f));

            // set legend, use color mapper
            plot.getLegend().clear();
            // add the labels to the legend, using color format, index -> colormapper apply color
//            The labels: this.labels
            DataTable data2 = new DataTable(String.class);
            for (int i = 0; i < this.labels.length; i++) {
                data2.add(this.labels[i]);
            }
//            ((BarPlot.BarPlotLegend) plot.getLegend()).setLabelColumn(0);
            plot.getLegend().add(data2);
            List<Drawable> drawables = plot.getLegend().getDrawables();
            for (int i = 0; i < drawables.size(); i++) {
                AbstractLegend.Item item = (AbstractLegend.Item) drawables.get(i);
                item.getLabel().setColor(colorList.get(i % colorList.size()));
            }
        } else {
            int i = 0;
            for (Color color : colors) {
                DataSource dataA = series[i++];
                plot.setPointRenderers(dataA, null);
                LineRenderer lineA = new DefaultLineRenderer2D();
                lineA.setColor(color);
                plot.setLineRenderers(dataA, lineA);
            }
        }


        return plot;
    }

    public String toHtml(TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) {
        String html;
        if (origin > 0) {
            if (timeFormat == TimeFormat.TURN_TO_DATE) {
                convertTurnsToEpochSeconds(origin);
            } else if (timeFormat == TimeFormat.DAYS_TO_DATE) {
                convertDaysToEpochSeconds(origin);
            }
        }
        if (isBar()) {
            JsonElement json = WebUtil.GSON.toJsonTree(toHtmlJson(timeFormat, numberFormat, origin));
            html = WebStore.render(f -> JtebarchartdatasrcGenerated.render(f, null, null, getName(), json, false));
        } else {
            boolean isTime = timeFormat == TimeFormat.TURN_TO_DATE || timeFormat == TimeFormat.DAYS_TO_DATE || timeFormat == TimeFormat.MILLIS_TO_DATE;
//            toHtmlJson(timeFormat, numberFormat, origin)
            JsonElement json = WebUtil.GSON.toJsonTree(toHtmlJson(timeFormat, numberFormat, origin));
            html = WebStore.render(f -> JtetimechartdatasrcGenerated.render(f, null, null, getName(), json, isTime));
        }
        return html;
    }

    public byte[] write(TimeFormat timeFormat, TableNumberFormat numberFormat) throws IOException {
        XYPlot plot = getTable(timeFormat, numberFormat);
        DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(plot, baos, 1400, 600);
        return baos.toByteArray();
    }
    public void write(IMessageIO channel, TimeFormat timeFormat, TableNumberFormat numberFormat, long originDate, boolean attachJson, boolean attachCsv) throws IOException {
        IMessageBuilder msg = writeMsg(channel.create(), timeFormat, numberFormat, originDate, attachJson, attachCsv);
        msg.send();
    }

    public IMessageBuilder writeMsg(IMessageBuilder msg, TimeFormat timeFormat, TableNumberFormat numberFormat, long originDate, boolean attachJson, boolean attachCsv) throws IOException {
        try {
            msg = msg.graph(this, timeFormat, numberFormat, originDate);
            String name = this.name == null || this.name.isEmpty() ? "data" : this.name;
            if (attachJson) {
                msg = msg.file(name + ".json", toHtmlJson(timeFormat, numberFormat, originDate).toString());
            }
            if (attachCsv) {
                msg = msg.file(name + ".csv", toCsv());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return msg;
    }
}

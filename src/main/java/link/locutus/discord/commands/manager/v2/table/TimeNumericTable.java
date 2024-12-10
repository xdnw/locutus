package link.locutus.discord.commands.manager.v2.table;

import com.google.gson.JsonArray;
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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.Attribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
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

    public void setLabels(String... labels) {
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

    public WebGraph toHtmlJson(TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long origin) {
        return toHtmlJson(labels, data, amt, name, labelX, labelY, timeFormat, numberFormat, type, origin);
    }

    public static WebGraph toHtmlJson(String[] labels, DataTable data, int amt, String name, String labelX, String labelY, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long origin) {
        WebGraph graph = new WebGraph();

        graph.time_format = timeFormat;
        graph.number_format = numberFormat;
        graph.origin = origin;

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
        graph.type = type;


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

    public XYPlot getTable(TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) {
        DataSource[] series = new DataSource[amt];
        for (int i = 0; i < amt; i++) {
            DataSource source = new DataSeries(this.labels[i], data, 0, i + 1);
            series[i] = source;
        }

        DataTable barData = null;

        Function<Number, String> timeFormatFunction;
        if (!timeFormat.isTime() && origin > 0) {
            timeFormatFunction = time -> timeFormat.toString(time.longValue() + origin);
        } else {
            timeFormatFunction = time -> timeFormat.toString(time);
        }
        // Create new xy-plot
        XYPlot plot;
        if (isBar) {
            barData = new DataTable(Double.class, Double.class, String.class);

            int numYTypes = data.getColumnCount() - 1;
            double widthPerRow = 1d / (numYTypes);
            for (int i = 0; i < data.getRowCount(); i++) {
                Row row = data.getRow(i);
                long timeData = ((Number) row.get(0)).longValue();
                String timeStr = timeFormatFunction.apply((double) timeData);

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
        axisRendererX.setTickLabelFormat(MathMan.toFormat(timeFormatFunction::apply));

        AxisRenderer axisRendererY = plot.getAxisRenderer(XYPlot.AXIS_Y);
        axisRendererY.setTicksAutoSpaced(true);
        axisRendererY.setMinorTicksCount(4);
        axisRendererY.setTickLabelFormat(MathMan.toFormat(numberFormat::toString));

        Set<Color> colors = new HashSet<>();
        switch (amt) {
            case 11:
                colors.add(Color.BLACK);
            case 10:
                colors.add(Color.PINK);
            case 9:
                colors.add(Color.YELLOW);
            case 8:
                colors.add(Color.LIGHT_GRAY);
            case 7:
                colors.add(Color.DARK_GRAY);
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

    public byte[] write(TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long origin) throws IOException {
        XYPlot plot = getTable(timeFormat, numberFormat, origin);
        DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(plot, baos, 1400, 600);
        return baos.toByteArray();
    }
    public void write(IMessageIO channel, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long originDate, boolean attachJson, boolean attachCsv) throws IOException {
        IMessageBuilder msg = writeMsg(channel.create(), timeFormat, numberFormat, type, originDate, attachJson, attachCsv);
        msg.send();
    }

    public IMessageBuilder writeMsg(IMessageBuilder msg, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long originDate, boolean attachJson, boolean attachCsv) throws IOException {
        try {
            msg = msg.graph(this, timeFormat, numberFormat, type, originDate);
            String name = this.name == null || this.name.isEmpty() ? "data" : this.name;
            if (attachJson) {
                msg = msg.file(name + ".json", toHtmlJson(timeFormat, numberFormat, type, originDate).toString());
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

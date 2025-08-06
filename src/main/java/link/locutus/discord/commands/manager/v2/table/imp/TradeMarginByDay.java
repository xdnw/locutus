package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TradeMarginByDay extends SimpleTable<Void> {

    private Map<Long, double[]> marginsByDay = new Long2ObjectOpenHashMap<>();
    private final List<ResourceType> resourceTypes;
    private final double[] empty;
    private long minDay;
    private long maxDay;

//    public static List<DBTrade> getTradesByResources(Set<ResourceType> resources, long start, Long end) {
//        if (end == null) end = Long.MAX_VALUE;
//        if (resources.size() == ResourceType.values.length) {
//            return Locutus.imp().getTradeManager().getTradeDb().getTrades(start, end);
//        }
//        return Locutus.imp().getTradeManager().getTradeDb().getTradesByResources(
//                resources.stream().filter(f -> f != ResourceType.MONEY && f != ResourceType.CREDITS).collect(Collectors.toSet()), start, end);
//    }

    public TradeMarginByDay(Set<ResourceType> resources, @Timestamp long start, @Default @Timestamp Long end,
                            @Arg("Use the margin percent instead of absolute difference")
                            @Default("true") boolean percent) {
        if (end == null) end = Long.MAX_VALUE;
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);

        this.resourceTypes = new ObjectArrayList<>(resources);
        this.empty = new double[resourceTypes.size()];
        String[] labels = resourceTypes.stream().map(ResourceType::getName).toArray(String[]::new);

        this.minDay = Long.MAX_VALUE;
        this.maxDay = 0;

        long startDay = TimeUtil.getDay(start);
        long endDay = TimeUtil.getDay(end);
        this.marginsByDay = Locutus.imp().getTradeDB().getMarginsByDay(resourceTypes, startDay, endDay, percent);
        for (Long day : marginsByDay.keySet()) {
            if (day < minDay) minDay = day;
            if (day > maxDay) maxDay = day;
        }


        if (minDay == Long.MAX_VALUE) minDay = 0; // No data case
        setTitle("Resource margin " + (percent ? " % " : "") + "by day");
        setLabelX("day");
        setLabelY("ppu");
        setLabels(labels);

        writeData();
    }

    @Override
    protected SimpleTable writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            add(day, (Void) null);
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public long getOrigin() {
        return minDay;
    }

    @Override
    public void add(long day, Void ignore) {
        double[] margins = marginsByDay.getOrDefault(day, empty);
        add(day - minDay, margins);
    }
}
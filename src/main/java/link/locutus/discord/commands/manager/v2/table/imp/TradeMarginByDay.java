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
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.stream.Collectors;

public class TradeMarginByDay extends SimpleTable<Void> {

    private final Map<Long, double[]> marginsByDay = new Long2ObjectOpenHashMap<>();
    private final List<ResourceType> resourceTypes;
    private final double[] empty;
    private long minDay;
    private long maxDay;

    public static List<DBTrade> getTradesByResources(Set<ResourceType> resources, long start, Long end) {
        if (end == null) end = Long.MAX_VALUE;
        if (resources.size() == ResourceType.values.length) {
            return Locutus.imp().getTradeManager().getTradeDb().getTrades(start, end);
        }
        return Locutus.imp().getTradeManager().getTradeDb().getTradesByResources(
                resources.stream().filter(f -> f != ResourceType.MONEY && f != ResourceType.CREDITS).collect(Collectors.toSet()), start, end);
    }

    public TradeMarginByDay(Set<ResourceType> resources, @Timestamp long start, @Default @Timestamp Long end,
                            @Arg("Use the margin percent instead of absolute difference")
                            @Default("true") boolean percent) {
        this(getTradesByResources(resources, start, end), resources, percent);
    }

    public TradeMarginByDay(List<DBTrade> trades, Set<ResourceType> resources,
                            @Arg("Use the margin percent instead of absolute difference")
                            @Default("true") boolean percent) {
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);

        List<DBTrade> allOffers = trades;
        Map<Long, List<DBTrade>> offersByDay = new Long2ObjectOpenHashMap<>();

        this.minDay = Long.MAX_VALUE;
        this.maxDay = 0;
        for (DBTrade offer : allOffers) {
            long day = TimeUtil.getDay(offer.getDate());
            minDay = Math.min(minDay, day);
            maxDay = Math.max(maxDay, day);
            offersByDay.computeIfAbsent(day, f -> new ObjectArrayList<>()).add(offer);
        }
        offersByDay.remove(minDay);
        minDay++;

        this.resourceTypes = new ObjectArrayList<>(resources);

        for (Map.Entry<Long, List<DBTrade>> entry : offersByDay.entrySet()) {
            long day = entry.getKey();
            List<DBTrade> offers = entry.getValue();
            double[] dayMargins = new double[resourceTypes.size()];

            Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avg = Locutus.imp().getTradeManager().getAverage(offers);
            Map<ResourceType, Double> lows = avg.getKey();
            Map<ResourceType, Double> highs = avg.getValue();
            for (int i = 0; i < resourceTypes.size(); i++) {
                ResourceType type = resourceTypes.get(i);
                Double low = lows.get(type);
                Double high = highs.get(type);
                if (low != null && high != null) {
                    double margin = high - low;
                    if (percent) margin = 100 * margin / high;
                    dayMargins[i] = margin;
                }
            }

            marginsByDay.put(day, dayMargins);
        }

        this.empty = new double[resourceTypes.size()];
        String[] labels = resourceTypes.stream().map(f -> f.getName()).toArray(String[]::new);

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
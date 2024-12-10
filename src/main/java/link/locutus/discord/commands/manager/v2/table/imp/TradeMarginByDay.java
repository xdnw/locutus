package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.stream.Collectors;

public class TradeMarginByDay extends SimpleTable<Map<ResourceType, Double>> {

    private final long start;
    private final long end;
    private final boolean percent;
    private final Map<Long, Map<ResourceType, Double>> marginsByDay = new HashMap<>();
    private final List<ResourceType> resourceTypes;
    private final double[] buffer;
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
        this(getTradesByResources(resources, start, end), resources, start, end, percent);
    }

    public TradeMarginByDay(List<DBTrade> trades, Set<ResourceType> resources, @Timestamp long start, @Default @Timestamp Long end,
                            @Arg("Use the margin percent instead of absolute difference")
                            @Default("true") boolean percent) {
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);

        this.start = start;
        this.end = (end == null) ? Long.MAX_VALUE : end;
        this.percent = percent;
        List<DBTrade> allOffers = trades;
        Map<Long, List<DBTrade>> offersByDay = new LinkedHashMap<>();

        this.minDay = Long.MAX_VALUE;
        this.maxDay = 0;
        for (DBTrade offer : allOffers) {
            long turn = TimeUtil.getTurn(offer.getDate());
            long day = turn / 12;
            minDay = Math.min(minDay, day);
            offersByDay.computeIfAbsent(day, f -> new ArrayList<>()).add(offer);
        }
        offersByDay.remove(minDay);
        minDay++;

        Map<Long, Map<ResourceType, Double>> marginsByDay = new HashMap<>();

        for (Map.Entry<Long, List<DBTrade>> entry : offersByDay.entrySet()) {
            long day = entry.getKey();
            List<DBTrade> offers = entry.getValue();
            Map<ResourceType, Double> dayMargins = new HashMap<>();

            Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avg = Locutus.imp().getTradeManager().getAverage(offers);
            Map<ResourceType, Double> lows = avg.getKey();
            Map<ResourceType, Double> highs = avg.getValue();
            for (ResourceType type : ResourceType.values) {
                Double low = lows.get(type);
                Double high = highs.get(type);
                if (low != null && high != null) {
                    double margin = high - low;
                    if (percent) margin = 100 * margin / high;
                    dayMargins.put(type, margin);
                }
            }

            marginsByDay.put(day, dayMargins);
        }

        this.resourceTypes = new ObjectArrayList<>(resources);
        this.buffer = new double[resourceTypes.size()];
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
            Map<ResourceType, Double> margins = marginsByDay.getOrDefault(day, Collections.emptyMap());
            add(dayOffset, margins);
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
    public void add(long day, Map<ResourceType, Double> cost) {
        for (int i = 0; i < resourceTypes.size(); i++) {
            buffer[i] = cost.getOrDefault(resourceTypes.get(i), buffer[i]);
        }
        add(day - minDay, buffer);
    }
}
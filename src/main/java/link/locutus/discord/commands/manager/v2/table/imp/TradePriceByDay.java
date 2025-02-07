package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class TradePriceByDay extends SimpleTable<Void> {
    private final long minDay;
    private final long maxDay;
    private final Map<ResourceType, Map<Long, Double>> avgByRss;
    private final double[] buffer;
    private final List<ResourceType> rssList;

    public TradePriceByDay(Set<ResourceType> resources, int numDays) {
        if (numDays <= 1) throw new IllegalArgumentException("Invalid number of days");

        this.rssList = new ArrayList<>(resources);
        rssList.remove(ResourceType.MONEY);
        if (rssList.isEmpty()) throw new IllegalArgumentException("Invalid resources");

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays);

        TradeManager manager = Locutus.imp().getTradeManager();
        TradeDB tradeDB = manager.getTradeDb();

        this.avgByRss = new HashMap<>();
        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;

        for (ResourceType type : rssList) {
            double curAvg = manager.getHighAvg(type);
            int min = (int) (curAvg * 0.2);
            int max = (int) (curAvg * 5);

            Map<Long, Double> averages = tradeDB.getAverage(start, type, 15, min, max);
            avgByRss.put(type, averages);

            minDay = Collections.min(averages.keySet());
            maxDay = Collections.max(averages.keySet());
        }

        this.minDay = minDay;
        this.maxDay = maxDay;
        this.buffer = new double[rssList.size()];

        String title = "Trade average by day";
        String[] labels = rssList.stream().map(ResourceType::getName).toArray(String[]::new);

        setTitle(title);
        setLabelX("day");
        setLabelY("ppu");
        setLabels(labels);

        writeData();
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            add(day, (Void) null);
        }
        return this;
    }

    @Override
    public void add(long day, Void ignore) {
        for (int i = 0; i < rssList.size(); i++) {
            ResourceType type = rssList.get(i);
            Double value = avgByRss.getOrDefault(type, Collections.emptyMap()).get(day);
            if (value != null) buffer[i] = value;
        }
        add(day - minDay, buffer);
    }

    @Override
    public long getOrigin() {
        return minDay;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}
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

public class StockpileValueByDay extends SimpleTable<Void> {
    private final Map<Long, Double> valueByDay1;
    private final Map<Long, Double> valueByDay2;
    private final long minDay;
    private final long maxDay;

    public StockpileValueByDay(Map<ResourceType, Double> stockpile1,
                               Map<ResourceType, Double> stockpile2,
                               int numDays) {
        TradeManager manager = Locutus.imp().getTradeManager();
        TradeDB tradeDB = manager.getTradeDb();
        Map<ResourceType, Map<Long, Double>> avgByRss = new HashMap<>();
        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;
        List<ResourceType> resources = new ArrayList<>(Arrays.asList(ResourceType.values()));
        resources.remove(ResourceType.CREDITS);
        resources.remove(ResourceType.MONEY);

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays);

        for (ResourceType type : resources) {
            double curAvg = manager.getHighAvg(type);
            int min = (int) (curAvg * 0.2);
            int max = (int) (curAvg * 5);

            Map<Long, Double> averages = tradeDB.getAverage(start, type, 15, min, max);

            avgByRss.put(type, averages);

            minDay = Math.min(minDay, Collections.min(averages.keySet()));
            maxDay = Collections.max(averages.keySet());
        }

        valueByDay1 = new HashMap<>();
        valueByDay2 = new HashMap<>();

        for (long day = minDay; day <= maxDay; day++) {
            double val1 = 0;
            double val2 = 0;

            for (ResourceType resource : resources) {
                Double rssPrice = avgByRss.getOrDefault(resource, Collections.emptyMap()).get(day);
                if (rssPrice == null) {
                    continue;
                }

                val1 += rssPrice * stockpile1.getOrDefault(resource, 0d);
                val2 += rssPrice * stockpile2.getOrDefault(resource, 0d);
            }
            val1 += 1 * stockpile1.getOrDefault(ResourceType.MONEY, 0d);
            val2 += 1 * stockpile2.getOrDefault(ResourceType.MONEY, 0d);

            valueByDay1.put(day, val1);
            valueByDay2.put(day, val2);
        }

        this.minDay = minDay;
        this.maxDay = maxDay;

        setTitle("Stockpile value by day");
        setLabelX("day");
        setLabelY("value");
        setLabels(new String[]{"stockpile 1", "stockpile 2"});

        writeData();
    }

    @Override
    protected StockpileValueByDay writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            add(day, (Void) null);
        }
        return this;
    }

    @Override
    public void add(long day, Void ignore) {
        long offset = day - minDay;
        add(offset, valueByDay1.getOrDefault(day, 0d), valueByDay2.getOrDefault(day, 0d));
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

package link.locutus.discord.web.commands.api;

import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class TradeEndpoints {
    @Command
    public Map<String, Object> tradePriceByDayJson(link.locutus.discord.db.TradeDB tradeDB, TradeManager manager, Set<ResourceType> resources, @Range(min = 1) int days) {
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("No valid resources");
        }

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

        Map<ResourceType, Map<Long, Double>> avgByRss = new HashMap<>();
        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;

        JsonArray labels = new JsonArray();

        for (ResourceType type : resources) {
            labels.add(type.name());
            // long minDate, ResourceType type, int minQuantity, int min, int max
            double curAvg = manager.getHighAvg(type);
            int min = (int) (curAvg * 0.2);
            int max = (int) (curAvg * 5);

            Map<Long, Double> averages = tradeDB.getAverage(start, type, 15, min, max);

            avgByRss.put(type, averages);

            minDay = Math.min(minDay, Collections.min(averages.keySet()));
            maxDay = Collections.max(averages.keySet());
        }

        Map<String, Object> obj = new LinkedHashMap<>();
        List data = new ObjectArrayList();

        LongArrayList timestampsJson = new LongArrayList();
        for (long day = minDay; day <= maxDay; day++) {
            long time = TimeUtil.getTimeFromDay(day);
            timestampsJson.add(time / 1000L);
        }
        data.add(timestampsJson);

        for (ResourceType type : resources) {
            Map<Long, Double> avgByDay = avgByRss.get(type);
            DoubleArrayList rssData = new DoubleArrayList();
            for (long day = minDay; day <= maxDay; day++) {
                Double price = avgByDay.getOrDefault(day, 0d);
                rssData.add(price);
            }
            data.add(rssData);
        }

        obj.put("x", "Time");
        obj.put("y", "Price Per Unit ($)");
        obj.put("labels", labels);
        obj.put("data", data);
        return obj;
    }
}

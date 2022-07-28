package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeDB;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TradePages {
    @Command
    public Object tradePrice(TradeDB manager) {
        List<String> header = new ArrayList<>(Arrays.asList("Resource", "Low", "High"));
        List<List<Object>> rows = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            List<Object> row = new ArrayList<>();
            row.add(type.name());
            row.add(MarkupUtil.htmlUrl(MathMan.format(manager.getLow(type)), PnwUtil.getTradeUrl(type, true)));
            row.add(MarkupUtil.htmlUrl(MathMan.format(manager.getHigh(type)), PnwUtil.getTradeUrl(type, false)));
            rows.add(row);
        }

        return views.basictable.template("Trade Price", header, rows).render().toString();
    }

    @Command
    public Object tradePriceByDayJson(link.locutus.discord.db.TradeDB tradeDB, TradeDB manager, List<ResourceType> resources, int days) {
        if (days <= 1) return "Invalid number of days";
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);
        if (resources.isEmpty()) return "Invalid resources";

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

        JsonObject obj = new JsonObject();

        JsonArray data = new JsonArray();

        JsonArray timestampsJson = new JsonArray();
        for (long day = minDay; day <= maxDay; day++) {
            long time = TimeUtil.getTimeFromDay(day);
            timestampsJson.add(time / 1000L);
        }

        data.add(timestampsJson);

        for (ResourceType type : resources) {
            Map<Long, Double> avgByDay = avgByRss.get(type);
            JsonArray rssData = new JsonArray();
            for (long day = minDay; day <= maxDay; day++) {
                Double price = avgByDay.getOrDefault(day, 0d);
                rssData.add(price);
            }
            data.add(rssData);
        }

        obj.addProperty("x", "Time");
        obj.addProperty("y", "Price Per Unit ($)");
        obj.add("labels", labels);
        obj.add("data", data);
        return obj.toString();
    }

    @Command
    public Object tradePriceByDay(TradeDB manager, List<ResourceType> resources, int days) {
        String query = StringMan.join(resources, ",") + "/" + days;
        String endpoint = "/tradepricebydayjson/" + query;

        String title = "Trade Price By Day";
        return views.trade.tradeprice.template(title, endpoint).render().toString();
    }
}
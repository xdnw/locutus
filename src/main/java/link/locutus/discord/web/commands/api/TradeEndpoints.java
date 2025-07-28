package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TradeEndpoints {
    @Command(viewable = true)
    @ReturnType(WebGraph.class)
    public WebGraph tradePriceByDayJson(link.locutus.discord.db.TradeDB tradeDB, TradeManager manager, Set<ResourceType> resources, @Range(min = 1) int days) {
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("No valid resources");
        }

        long end = System.currentTimeMillis();
        long start = end - TimeUnit.DAYS.toMillis(days);

        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;

        List<String> labels = new ObjectArrayList<>();
        List<ResourceType> resourceList = new ObjectArrayList<>(resources);
        for (ResourceType type : resourceList) {
            labels.add(type.name());
        }


        Map<Long, double[]> averagesByDay = tradeDB.getAverageByDay(resourceList, TimeUtil.getDay(start), TimeUtil.getDay(end));
        for (Map.Entry<Long, double[]> entry : averagesByDay.entrySet()) {
            minDay = Math.min(minDay, entry.getKey());
            maxDay = Math.max(maxDay, entry.getKey());
        }

        WebGraph result = new WebGraph();
        List<List<Object>> data = new ObjectArrayList<>();

        LongArrayList timestampsJson = new LongArrayList();
        for (long day = minDay; day <= maxDay; day++) {
            long time = TimeUtil.getTimeFromDay(day);
            timestampsJson.add(time / 1000L);
        }

        data.add((List) timestampsJson);
        for (int i = 0; i < resourceList.size(); i++) {
            ResourceType type = resourceList.get(i);
            LongArrayList rssData = new LongArrayList();
            for (long day = minDay; day <= maxDay; day++) {
                double[] avgByDay = averagesByDay.get(day);
                long price = avgByDay == null ? 0 : Math.round(avgByDay[i]);
                rssData.add(price);
            }
            data.add((List) rssData);
        }


        result.origin = minDay;
        result.time_format = TimeFormat.SECONDS_TO_DATE;
        result.number_format = TableNumberFormat.DECIMAL_ROUNDED;
        result.x = "Time";
        result.y = "Price Per Unit ($)";
        result.labels = labels.toArray(new String[0]);
        result.data = data;
        result.title = "Trade Prices by Day";
        result.type = GraphType.LINE;
        return result;
    }
}

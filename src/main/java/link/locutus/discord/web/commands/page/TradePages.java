package link.locutus.discord.web.commands.page;

import gg.jte.generated.precompiled.JtebasictableGenerated;
import gg.jte.generated.precompiled.trade.JtetradepriceGenerated;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class TradePages {
    @Command
    public Object tradePrice(WebStore ws, TradeManager manager) {
        List<String> header = new ArrayList<>(Arrays.asList("Resource", "Low", "High"));
        List<List<String>> rows = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            List<String> row = new ArrayList<>();
            row.add(type.name());
            row.add(MarkupUtil.htmlUrl(MathMan.format(manager.getLow(type)), PW.getTradeUrl(type, true)));
            row.add(MarkupUtil.htmlUrl(MathMan.format(manager.getHigh(type)), PW.getTradeUrl(type, false)));
            rows.add(row);
        }

        return WebStore.render(f -> JtebasictableGenerated.render(f, null, ws, "Trade Price", header, ws.table(rows)));
        return WebStore.render(f -> JtebasictableGenerated.render(f, null, ws, "Trade Price", header, ws.tableUnsafe(rows)));
    }

    @Command
    public Object tradePriceByDay(WebStore ws, TradeManager manager, Set<ResourceType> resources, int days) {
        String query = StringMan.join(resources, ",") + "/" + days;
        String endpoint = "/api/tradepricebydayjson/" + query;

        String title = "Trade Price By Day";
        return WebStore.render(f -> JtetradepriceGenerated.render(f, null, ws, title, endpoint));
    }
}
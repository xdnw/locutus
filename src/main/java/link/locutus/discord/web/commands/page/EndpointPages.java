package link.locutus.discord.web.commands.page;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoForm;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EndpointPages extends PageHelper {
    private static final Gson gson = new Gson();

    @Command
    public Object tradePriceByDayJson(link.locutus.discord.db.TradeDB tradeDB, TradeManager manager, Set<ResourceType> resources, int days) {
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
    @NoForm
    public String login(WebStore ws, Context context, @Default Integer nation, @Default Long user, @Default String token) throws IOException {
        System.out.println("method " + context.method());
        System.out.println("headers " + context.res().getHeaderNames());
        Iterator<String> iter = context.req().getHeaderNames().asIterator();
        while (iter.hasNext()) {
            String header = iter.next();
            System.out.println("- " + header + " " + context.req().getHeader(header));
        }
        System.out.println("login " + context.req().getRemoteUser());
        System.out.println("attr " + context.attributeMap());
        System.out.println("host " + context.host());
        System.out.println("ip " + context.ip());
        System.out.println("session " + context.sessionAttributeMap());


        Map<String, String> queryMap = PageHandler.parseQueryMap(context.queryParamMap());
        boolean requireNation = queryMap.containsKey("nation");
        boolean requireUser = queryMap.containsKey("user");

        try {

            DBAuthRecord auth = AuthBindings.getAuth(ws, context, requireNation || requireUser, requireNation, requireUser);
            if (auth != null) {
                Map<String, Object> data = auth.toMap();
//                Guild guild = AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
                Guild guild = AuthBindings.guild(context, null, null, false);
                if (guild != null) {
                    data.put("guild", guild.getIdLong());
                }
                return gson.toJson(data);
            }
            return "{}";
        } catch (RedirectResponse response) {
            Map<String, String> data = Map.of("action", "redirect", "value", response.getMessage());
            return gson.toJson(data);
        }
    }

    @Command
    @NoForm
    public String logout(WebStore ws, Context context) throws IOException {
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, false, false, false);
        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
        AuthBindings.logout(context, auth, false);
        if (auth != null) {
            if (guild == null) guild = auth.getDefaultGuild();
            Map<String, Object> data = auth.toMap();
            if (guild != null) {
                data.put("guild", guild.getIdLong());
            }
            data.put("success", true);
            data.put("message", "Logged out");
            return gson.toJson(data);
        } else {
            // no account to logout
            return gson.toJson(Map.of("success", false));
        }
    }

    // Session
    // Returns info about current session
    @Command
    @NoForm
    public String session(WebStore ws, Context context) throws IOException {
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, false, false, false);
        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
        if (auth != null) {
            Map<String, Object> data = auth.toMap();
            if (guild != null) {
                data.put("guild", guild.getIdLong());
            }
            return gson.toJson(data);
        } else {
            return "{}";
        }
    }

    private static final Map<UUID, Boolean> mailTokens = new ConcurrentHashMap<>();

    @Command
    public static String login_mail(WebStore ws, Context context, DBNation nation) throws IOException {
        WebUtil.mailLogin(nation, false,true);
        return "{}";
    }

    // Command Options
    // Types

    // Query placeholders
    // Type
    // Selection
    // Fields
}

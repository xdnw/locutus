package link.locutus.discord.web.commands.page;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
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

import static link.locutus.discord.web.jooby.PageHandler.CookieType.URL_AUTH;
import static link.locutus.discord.web.jooby.PageHandler.CookieType.URL_AUTH_SET;

public class EndpointPages extends PageHelper {

    @Command
    public Map<String, Object> set_token(Context context, UUID token) {
        DBAuthRecord record = WebRoot.db().get(token);
        if (record == null) return Map.of("success", false, "message", "Invalid token");
        int keepAlive = (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
        WebUtil.setCookieViaHeader(context, URL_AUTH.getCookieId(), token.toString(), keepAlive, true, null);
        return Map.of("success", true);
    }

    @Command
    public Map<String, Object> input_options(String type) {
        System.out.println(":||remove Get option " + type);
        // InputOptions {
        //    icon?: string[];
        //    key: string[];
        //    text?: string[];
        //    subtext?: string[];
        //    color?: string[];
        //    success?: string;
        //    message?: string;
        //}
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> exampleForTesting = Arrays.asList(
                "banana",
                "apple",
                "orange",
                "grape",
                "kiwi",
                "mango",
                "strawberry",
                "blueberry",
                "raspberry",
                "blackberry",
                "watermelon",
                "cantaloupe",
                "honeydew",
                "papaya",
                "pineapple"
        );
        result.put("key", exampleForTesting);
        return result;
    }

    @Command
    public Map<String, Object> set_oauth_code(Context context, @Me @Default DBNation me, String code) throws IOException {
        String access_token = AuthBindings.getAccessToken(code, Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#/oauth2");
        if (access_token == null) {
            return Map.of("success", false, "message", "Cannot fetch access_token from OAuth2 code");
        }
        JsonObject user = AuthBindings.getUser(access_token);
        if (user == null) {
            return Map.of("success", false, "message", "Fetched access_token successfully, but failed to fetch user using it");
        }
        JsonElement idStr = user.get("id");
        if (idStr == null) {
            return Map.of("success", false, "message", "Fetched access_token and user, but failed to fetch ID from user");
        }
        DBAuthRecord record = AuthBindings.generateAuthRecord(context, Long.parseLong(idStr.getAsString()), me == null ? null : me.getNation_id());
        int keepAlive = (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
        WebUtil.setCookieViaHeader(context, URL_AUTH.getCookieId(), record.getUUID().toString(), keepAlive, true, null);
        return Map.of("success", true);
    }

    @Command
    public Map<String, Object> logout(WebStore ws, Context context, @Me @Default DBAuthRecord auth) {
        if (auth == null) {
            return Map.of("success", false, "message", "No auth record found");
        }
        AuthBindings.logout(ws, context, auth, true);
        List<String> cookiesToRemove = Arrays.asList(
                URL_AUTH.getCookieId(),
                URL_AUTH_SET.getCookieId()
        );
        List<String> removeCookieStrings = new ArrayList<>();
        for (String cookie : cookiesToRemove) {
            removeCookieStrings.add(cookie + "=; Max-Age=0; Path=/; HttpOnly");
        }
        context.header("Set-Cookie", StringMan.join(removeCookieStrings, ", "));
        return Map.of("success", true);
    }

    @Command
    public Map<String, Object> query(Context context, WebStore ws, Map<String, Object> queries) throws IOException {
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, false, false, false);
        if (auth != null) {
            System.out.println(":||remove " + auth.getUserIdRaw() + " | " + auth.getNationIdRaw());
        } else {
            System.out.println(":||remove NOOO AUTHH");
        }
        System.out.println(":||remove Call queries " + queries);
        System.out.println(":||remove " + context.header("Authorization"));

        PageHandler handler = WebRoot.getInstance().getPageHandler();
        CommandGroup commands = handler.getCommands();
        CommandCallable apiCommands = commands.get("api");

        Map<String, Object> result = new ConcurrentHashMap<>();

        for (Map.Entry<String, Object> entry : queries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            List<String> path = key.contains("/") ? StringMan.split(key, "/") : List.of(key);
            CommandCallable callable = apiCommands.getCallable(path);
            if (callable instanceof ParametricCallable cmd) {
                Object componentData =  handler.call(cmd, ws, context, value);
                result.put(key, componentData);
                System.out.println(":||Remove Component data " + componentData);
            } else {
                result.computeIfAbsent("errors", k -> new ArrayList<>());
                ((List<String>) result.get("errors")).add("Invalid command: " + key);
            }
        }
        return result;
    }

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

    @Command
    @NoForm
    public Map<String, Object> session(WebStore ws, Context context) throws IOException {
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, false, false, false);
        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
        if (auth != null) {
            Map<String, Object> data = auth.toMap();
            if (guild != null) {
                data.put("guild", guild.getIdLong());
            }
            return data;
        } else {
            return Map.of("success", false, "message", "No session record found");
        }
    }

//    @Command
//    @NoForm
//    public Map<String, Object> login(WebStore ws, Context context, @Default Integer nation, @Default Long user, @Default String token) throws IOException {
//        Map<String, String> queryMap = PageHandler.parseQueryMap(context.queryParamMap());
//        boolean requireNation = queryMap.containsKey("nation");
//        boolean requireUser = queryMap.containsKey("user");
//        try {
//            DBAuthRecord auth = AuthBindings.getAuth(ws, context, requireNation || requireUser, requireNation, requireUser);
//            if (auth != null) {
//                Map<String, Object> data = auth.toMap();
//                Guild guild = AuthBindings.guild(context, null, null, false);
//                if (guild != null) {
//                    data.put("guild", guild.getIdLong());
//                }
//                return data;
//            }
//            return Collections.emptyMap();
//        } catch (RedirectResponse response) {
//            Map<String, Object> data = Map.of("action", "redirect", "value", response.getMessage());
//            return data;
//        }
//    }
//
//    @Command
//    @NoForm
//    public Map<String, Object> logout(WebStore ws, Context context) throws IOException {
//        DBAuthRecord auth = AuthBindings.getAuth(ws, context, false, false, false);
//        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
//        AuthBindings.logout(ws, context, auth, false);
//        if (auth != null) {
//            if (guild == null) guild = auth.getDefaultGuild();
//            Map<String, Object> data = auth.toMap();
//            if (guild != null) {
//                data.put("guild", guild.getIdLong());
//            }
//            data.put("success", true);
//            data.put("message", "Logged out");
//            return data;
//        } else {
//            return Collections.emptyMap();
//        }
//    }

//    @Command
//    public static String login_mail(WebStore ws, Context context, DBNation nation) throws IOException {
//        String mailUrl = WebUtil.mailLogin(nation, false,true);
//        throw new RedirectResponse(HttpStatus.SEE_OTHER, mailUrl);
//    }

    // Command Options
    // Types

    // Query placeholders
    // Type
    // Selection
    // Fields
}

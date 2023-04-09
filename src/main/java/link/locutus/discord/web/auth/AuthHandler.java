package link.locutus.discord.web.auth;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.web.test.WebDB;
import net.dv8tion.jda.api.entities.User;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import javax.security.auth.message.AuthException;
import java.io.IOException;
import java.net.UnknownServiceException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthHandler implements IAuthHandler {
    private final WebDB db;

    public AuthHandler(WebDB db) {
        this.db = db;
        for (Map.Entry<Long, Map.Entry<String, JsonObject>> entry : db.loadTokens().entrySet()) {
            addAccessToken(entry.getValue().getKey(), entry.getValue().getValue(), false);
        }
    }

    public static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String API_URL = "https://discord.com/api/users/@me";
    public static String COOKIE_ID = "LCTS";

    private final Map<Long, Map.Entry<String, JsonObject>> tokenToUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenHashes = new ConcurrentHashMap<>();

    private Map<String, String> ORIGINAL_PAGE = new ConcurrentHashMap<>();

    @Override
    public void logout(Context context) {
        for (CookieType type : CookieType.values()) {
            context.removeCookie(cookieId(type));
        }
        context.redirect(WebRoot.REDIRECT);
    }

    @Override
    public void login(Context context) {
        String url = context.fullUrl();
        if (!url.toLowerCase().contains("logout")) {
            ORIGINAL_PAGE.put(context.ip(), url);
        }

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", Settings.INSTANCE.APPLICATION_ID + ""));
        params.add(new BasicNameValuePair("redirect_uri", WebRoot.REDIRECT));
        params.add(new BasicNameValuePair("response_type", "code"));
        params.add(new BasicNameValuePair("scope", "identify guilds"));
        String query = URLEncodedUtils.format(params, "UTF-8");
        String redirect = AUTHORIZE_URL + "?" + query;
        context.header("Location", redirect);
        context.header("cache-control", "no-store");

        context.redirect(redirect);
    }

    private enum CookieType {
        DISCORD,
        COMMAND,
        MAIL,
    }

    private String cookieId(CookieType type) {
        StringBuilder key = new StringBuilder(COOKIE_ID);
        key.append(Settings.INSTANCE.BOT_TOKEN.hashCode());
        if (type != CookieType.DISCORD) {
            key.append(type.name());
        }

        return Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
    }

    public JsonObject getUser(String accessToken) throws IOException {
        CloseableHttpClient client = HttpClients.custom().build();

        HttpUriRequest request = RequestBuilder.get()
                .setUri(API_URL)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        CloseableHttpResponse response = client.execute(request);
        String json = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.ISO_8859_1);

        JsonObject user  = JsonParser.parseString(json).getAsJsonObject();
        if (user != null && user.get("id") != null) {
            JsonElement idStr = user.get("id");
            addAccessToken(accessToken, user, true);
            return user;
        }
        return null;
    }

    public String getAccessToken(String code) throws IOException {
        CloseableHttpClient client = HttpClients.custom().build();

        HttpUriRequest request = RequestBuilder.post()
                .setUri(TOKEN_URL)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .addParameter("grant_type", "authorization_code")
                .addParameter("client_id", Settings.INSTANCE.APPLICATION_ID + "")
                .addParameter("client_secret", Settings.INSTANCE.CLIENT_SECRET)
                .addParameter("redirect_uri", WebRoot.REDIRECT)
                .addParameter("code", code)
                .addParameter("scope", "identify guilds")

                .build();

        CloseableHttpResponse response = client.execute(request);

        String json = new String(response.getEntity().getContent().readAllBytes());

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        JsonElement accessToken = obj.get("access_token");

        return accessToken == null ? null : accessToken.getAsString();
    }



    private void addAccessToken(String accessToken, JsonObject userInfo, boolean updateDB) {
        JsonElement userIdStr = userInfo.get("id");
        if (userIdStr != null) {
            long userId = Long.parseLong(userIdStr.getAsString());
            tokenToUserMap.put(userId, new AbstractMap.SimpleEntry<>(accessToken, userInfo));

            String hash = hash(userId + accessToken);
            tokenHashes.put(hash, userId);

            if (updateDB) {
                db.addToken(userId, accessToken, userInfo);
            }
        }
    }

    private String hash(String key) {
        return Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
    }

    private final Map<UUID, Integer> nationTokens = new ConcurrentHashMap<>();
    private final Map<UUID, Long> userTokens = new ConcurrentHashMap<>();

//    private Map.Entry<Integer, Long> getAuth(Context context, boolean throwAuthError) {
//        Map<String, String> cookies = context.cookieMap();
//        String discordAuth = cookies.get(cookieId(CookieType.DISCORD));
//
//        Long userId = null;
//        Integer nationId = null;
//
//        // If discord auth exists
//        if (discordAuth != null) {
//            Long discordId = tokenHashes.get(discordAuth);
//            if (discordId != null) {
//                Map.Entry<String, JsonObject> userInfo = tokenToUserMap.get(discordId);
//                if (userInfo != null) {
//                    userInfo.getValue();
//                }
//            }
//        }
//
//        // if command auth exists
//        String commandAuth = cookies.get(cookieId(CookieType.COMMAND));
//
//        // if mail auth exists
//        String mailAuth = cookies.get(cookieId(CookieType.MAIL));
//
//
//        if (userId == null && nationId == null) {
//            // get the path
//            String path = context.path();
//
//            System.out.println(":||Path " + path);
//            if (path.equalsIgnoreCase("auth")) {
//                Map<String, List<String>> queryMap = context.queryParamMap();
//
//                String token = StringMan.join(queryMap.getOrDefault("token", new ArrayList<>()), ",");
//                // Check token in temporary map
//                if (token != null) {
//                    try {
//                        UUID uuid = UUID.fromString(token);
//                        Integer validatedNation = nationTokens.remove(uuid);
//                        Long validatedUser = userTokens.remove(uuid);
//
//                        if (validatedUser != null) {
//                            // set user cookie
//                        }
//
//                    } catch (IllegalArgumentException e) {
//                        // invalid token, ignore it
//                        e.printStackTrace();
//                    }
//                }
//
//                String allianceStr = StringMan.join(queryMap.getOrDefault("alliance", new ArrayList<>()), ",");
//                String authType = StringMan.join(queryMap.getOrDefault("type", new ArrayList<>()), ",");
//
//
//            }
//            if (userId == null && nationId == null) {
//                // prompt for auth
//                String authPage = createAuthPage(context, null, null);
//                // set context to authPage
//                context.result(authPage);
//
//
//                if (throwAuthError) {
//                    throw new UnauthorizedResponse("Authentication page response returned to context. Catch this error");
//                }
//
//                return null;
//            }
//        }
//
//        if (userId != null || nationId != null) {
//            if (nationId == null) {
//                // get nation from user id
//            }
//            if (userId == null) {
//                // get user id from nation
//            }
//
//            DBNation nation = null;
//            User user = null;
//
//            if (nation != null && user != null) {
//                return new AbstractMap.SimpleEntry<>(nationId, userId);
//            }
//            // delete cookies and prompt for auth
//        }
//
//
//    }

    public Long getUserId(JsonObject user) {
        JsonElement idStr = user.get("id");
        if (idStr != null) {
            return Long.parseLong(idStr.getAsString());
        }
        return null;
    }


    public JsonObject getDiscordUserJson(Context context, boolean login) throws IOException {
        String addr = context.ip();
//        if (addr.equals("0:0:0:0:0:0:0:1") || addr.equals("[0:0:0:0:0:0:0:1]") || addr.equals("127.0.0.1") || addr.equals("[127.0.0.1]")) {
//            return JsonParser.parseString("{\"id\":\"664156861033086987\",\"username\":\"borg\",\"avatar\":\"14aa8f752d52c066ad5ccb87116c90fa\",\"discriminator\":\"5729\",\"public_flags\":128,\"flags\":128,\"locale\":\"en-US\",\"mfa_enabled\":true}").getAsJsonObject();
//        }
        Map<String, String> cookies = context.cookieMap();
        String cookieId = cookieId(CookieType.DISCORD);
        String cookieData = cookies.get(cookieId);
        if (cookieData != null) {
            Long discordId = tokenHashes.get(cookieData);
            if (discordId != null) {
                Map.Entry<String, JsonObject> userInfo = tokenToUserMap.get(discordId);
                if (userInfo != null) {
                    return userInfo.getValue();
                }
            }
        }

        Map<String, List<String>> queries = context.queryParamMap();
        List<String> code = queries.get("code");
        if (code != null && code.size() == 1) {
            try {
                String codeSingle = code.get(0);
                String access_token = getAccessToken(codeSingle);
                if (access_token != null) {
                    JsonObject user = getUser(access_token);

                    JsonElement idStr = user.get("id");
                    if (idStr != null) {
                        long id = Long.parseLong(idStr.getAsString());
                        String hash = hash(id + access_token);

                        context.cookie(cookieId, hash, 60 * 60 * 24 * 30);

                        addAccessToken(access_token, user, true);
                        return user;
                    }
                }
            } finally {
                String redirect = ORIGINAL_PAGE.remove(context.ip());
                context.header("Location", redirect);
                context.header("cache-control", "no-store");
                context.redirect(redirect);
            }
        }

        if (login) {
            login(context);
        }

        return null;
    }

    @Override
    public Long getDiscordUser(Context ctx, boolean login) throws IOException {
        // tODO combine this with getDiscorduseer and move login stuff to here
        JsonObject userJson = getDiscordUserJson(ctx, true);
        if (userJson == null) {
            return null;
        }
        Long userId = Long.parseLong(userJson.get("id").getAsString());
        return userId;
    }

    @Override
    public DBNation getNation(Context ctx) throws IOException {
        Long userId = getDiscordUser(ctx);
        DBNation nation = DiscordUtil.getNation(userId);
        return nation;
    }

}

package link.locutus.discord.web.auth;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import link.locutus.discord.config.Settings;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.web.test.WebDB;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        String cookieId = cookieId(context);
        context.removeCookie(cookieId);

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

    private String cookieId(Context context) {
        StringBuilder key = new StringBuilder();
        key.append(COOKIE_ID);
        key.append(Settings.INSTANCE.BOT_TOKEN.hashCode());

        String hash = Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
        return hash;
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



    private void addAccessToken(String accessToken, JsonObject user, boolean updateDB) {
        JsonElement idStr = user.get("id");
        if (idStr != null) {
            long id = Long.parseLong(idStr.getAsString());
            tokenToUserMap.put(id, new AbstractMap.SimpleEntry<>(accessToken, user));

            String hash = hash(id + accessToken);
            tokenHashes.put(hash, id);

            if (updateDB) {
                db.addToken(id, accessToken, user);
            }
        }
    }

    private String hash(String key) {
        return Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
    }

    public JsonObject getDiscordUser(Context context) throws IOException {
        String addr = context.ip();
//        if (addr.equals("0:0:0:0:0:0:0:1") || addr.equals("[0:0:0:0:0:0:0:1]") || addr.equals("127.0.0.1") || addr.equals("[127.0.0.1]")) {
//            return JsonParser.parseString("{\"id\":\"664156861033086987\",\"username\":\"borg\",\"avatar\":\"14aa8f752d52c066ad5ccb87116c90fa\",\"discriminator\":\"5729\",\"public_flags\":128,\"flags\":128,\"locale\":\"en-US\",\"mfa_enabled\":true}").getAsJsonObject();
//        }
        Map<String, String> cookies = context.cookieMap();
        String cookieId = cookieId(context);
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

        return null;
    }

}

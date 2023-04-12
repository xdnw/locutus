package link.locutus.discord.web.auth;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthHandler implements IAuthHandler {
    private final WebDB db;

    private final Map<Long, Map.Entry<String, JsonObject>> tokenToUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenHashes = new ConcurrentHashMap<>();

    private Map<String, String> ORIGINAL_PAGE = new ConcurrentHashMap<>();

    private final Map<UUID, Auth> pendingWebCmdTokens = new ConcurrentHashMap<>();

    private final Map<UUID, Auth> webCommandAuth;

    public AuthHandler() {
        try {
            this.db = new WebDB();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        ;
        for (Map.Entry<Long, Map.Entry<String, JsonObject>> entry : db.loadTokens().entrySet()) {
            addAccessToken(entry.getValue().getKey(), entry.getValue().getValue(), false);
        }
        webCommandAuth = new ConcurrentHashMap<>(db.loadTempTokens());
    }

    public static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String API_URL = "https://discord.com/api/users/@me";
    public static String COOKIE_ID = "LCTS";

    @Override
    public void logout(Context context) {
        for (CookieType type : CookieType.values()) {
            context.removeCookie(cookieId(type));
        }
        context.redirect(WebRoot.REDIRECT);
    }

    @Override
    public void login(Context context) {
        String url = context.url();
        if (!url.toLowerCase().contains("logout")) {
            ORIGINAL_PAGE.put(context.ip(), context.url());
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
        URL,
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

    private Auth getAuth(Context context, boolean allowRedirect) {
        Map<String, String> cookies = context.cookieMap();
        String discordAuth = cookies.get(cookieId(CookieType.DISCORD));

        String message = null;

        // If discord auth exists
        if (discordAuth != null) {
            Long discordId = tokenHashes.get(discordAuth);
            if (discordId != null) {
                Map.Entry<String, JsonObject> userInfo = tokenToUserMap.get(discordId);
                if (userInfo != null) {
                    JsonObject json = userInfo.getValue();
                    JsonElement idStr = json.get("id");
                    if (idStr != null) {
                        Long userId = Long.parseLong(idStr.getAsString());
                        Auth auth = new Auth(null, userId, Long.MAX_VALUE);
                        if (auth.isValid() && !auth.isExpired()) return auth;
                    }
                }
            }
            context.removeCookie(cookieId(CookieType.DISCORD));
        }

        {
            // if command auth exists
            String commandAuth = cookies.get(cookieId(CookieType.URL));
            if (commandAuth != null) {
                UUID uuid = UUID.fromString(commandAuth);
                Auth  auth = webCommandAuth.get(uuid);
                if (auth.isValid() && !auth.isExpired()) return auth;
                context.removeCookie(cookieId(CookieType.URL));
            }
        }

        Integer allianceIdFilter = null;
        Integer nationIdFilter = null;
        {
            // get the path
            String path = context.path();

            System.out.println(":||Path " + path);
            if (path.equalsIgnoreCase("auth")) {
                Map<String, List<String>> queryMap = context.queryParamMap();

                String allianceStr = StringMan.join(queryMap.getOrDefault("alliance", new ArrayList<>()), ",");
                if (allianceStr != null) {
                    allianceIdFilter = PnwUtil.parseAllianceId(allianceStr);
                }
                String nationStr = StringMan.join(queryMap.getOrDefault("nation", new ArrayList<>()), ",");
                if (nationStr != null) {
                    nationIdFilter = PnwUtil.parseAllianceId(nationStr);
                }

                String token = StringMan.join(queryMap.getOrDefault("token", new ArrayList<>()), ",");
                // Check token in temporary map
                if (token != null) {
                    try {
                        UUID uuid = UUID.fromString(token);
                        Auth  auth = pendingWebCmdTokens.remove(uuid);
                        if (auth != null) {
                            if (auth.timestamp() < System.currentTimeMillis() + TimeUnit.DAYS.toMinutes(15)) {
                                Long userId = auth.userId();
                                Integer nationId = auth.nationId();

                                if (auth.getUser() == null && auth.getNation() == null) {
                                    if (userId != null) {
                                        message = "Could not find user for id: " + userId;
                                        userId = null;
                                    }
                                    if (nationId != null) {
                                        message = "Could not find nation for id: " + nationId;
                                        nationId = null;
                                    }
                                    auth = null;
                                } else {
                                    UUID verifiedUid = UUID.randomUUID();
                                    context.cookie(cookieId(CookieType.URL), verifiedUid.toString(), 60 * 60 * 24 * 30);
                                    context.removeCookie(cookieId(CookieType.DISCORD));
                                    webCommandAuth.put(verifiedUid, auth);
                                    db.addTempToken(verifiedUid, auth);

                                    if (allowRedirect) {
                                        String redirect = getRedirect(context);
                                        context.header("Location", redirect);
                                        context.header("cache-control", "no-store");
                                        context.redirect(redirect);

                                        throw new UnauthorizedResponse("Authentication page response returned to context. Catch this error");
                                    }
                                    if (auth.isValid() && !auth.isExpired()) return auth;
                                }
                            } else {
                                message = "This authorization page has expired. Please try again.";
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // invalid token, ignore it
                        e.printStackTrace();
                    }
                }
            }
        }

        if (allowRedirect) {

//            if (nationIdFilter != null) {
//                DBNation nation = DBNation.byId(nationIdFilter);
//                if (nation != null) {
//
//                    // Send mail
//                    // redirect to mail page
//
//                    UUID uuid = UUID.randomUUID();
//
//                }
//            }
//            String authPage = createAuthPage(context, allianceIdFilter, nationIdFilter, message);
//            // set context to authPage
//            context.result(authPage);
//            throw new UnauthorizedResponse("Authentication page response returned to context. Catch this error");
        }
        return null;
    }

    private String getRedirect(Context context) {
        String redirect = context.queryParam("redirect");
        if (redirect == null || redirect.isEmpty()) {
            String fingerprint = (context.ip() + context.userAgent());
            String hash = Hashing.sha256().hashString(fingerprint, StandardCharsets.UTF_8).toString();
            redirect = ORIGINAL_PAGE.remove(hash);
            if (redirect == null || redirect.contains("auth") || redirect.contains("logout")) {
                redirect = WebRoot.REDIRECT;
            }
        }
        return redirect;
    }

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
                if (redirect == null) redirect = WebRoot.REDIRECT;
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

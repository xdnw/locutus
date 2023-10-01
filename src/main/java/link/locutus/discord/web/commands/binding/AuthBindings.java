package link.locutus.discord.web.commands.binding;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.RedirectResponse;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.mail.Mail;
import link.locutus.discord.util.task.mail.SearchMailTask;
import link.locutus.discord.web.commands.WM;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.web.test.WebDB;
import net.dv8tion.jda.api.entities.Guild;
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
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthBindings extends WebBindingHelper {
    @Binding
    @Me
    public static Auth auth(Context context) {
        throw new IllegalStateException("No auth found in command locals");
    }
    @Binding
    @Me
    public static User user(Context context, @Me @Default Auth auth) {
        User user = auth == null ? null : auth.getUser();
        if (user != null) return user;
        DBNation nation = auth == null ? null : auth.getNation();
        String url;
        if (nation != null) {
            user = nation.getUser();
            if (user != null) {
                return user;
            }
            url =  WebRoot.REDIRECT + "/page/login";
        } else {
            System.out.println(":||Remove Redirect discord oauth user page");
            url = AuthBindings.getDiscordAuthUrl();
        }
        throw new RedirectResponse(0, url);
    }
    @Binding
    @Me
    public static DBNation nation(Context context, @Me @Default Auth auth) {
        DBNation nation = auth == null ? null : auth.getNation();
        if (nation != null) return nation;

        User user = auth == null ? null : auth.getUser();
        String url;
        if (auth != null) {
            nation = DiscordUtil.getNation(user);
            if (nation != null) {
                return nation;
            }
            System.out.println(":||Remove Redirect register page");
            url =  WebRoot.REDIRECT + "/page/register";
        } else {
            url = WebRoot.REDIRECT + "/page/login?nation";
        }
        throw new RedirectResponse(0, url);
    }

    public static void setGuild(Context context, Guild guild) {
        String guildCookieId = PageHandler.CookieType.GUILD_ID.getCookieId();
        context.cookie(guildCookieId, String.valueOf(guild.getIdLong()), (int) TimeUnit.DAYS.toSeconds(30));
    }

    @Me
    @Binding
    public static Guild guild(Context context, @Default @Me DBNation nation, @Default @Me User user) {
        return guild(context, nation, user, true);
    }

    public static Guild guild(Context context, @Default @Me DBNation nation, @Default @Me User user, boolean allowRedirect) {
        String guildCookieId = PageHandler.CookieType.GUILD_ID.getCookieId();
        String guildStr = context.cookie(guildCookieId);
        String message = null;
        if (guildStr != null && MathMan.isInteger(guildStr)) {
            long id = Long.parseLong(guildStr);
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(id);
            if (guild == null) {
                message = "Guild not found with id: `" + id + "`";
            } else {
                return guild;
            }
        }
        if (allowRedirect) {
            if (user == null && nation == null) {
                throw new RedirectResponse(0, WebRoot.REDIRECT + "/page/login");
            }

            throw new RedirectResponse(0, WM.guildselect.cmd.toPageUrl());
        }
        return null;
    }

    public record Auth(Integer nationId, Long userId, long timestamp) {

        public User getUser() {
            return userId == null ? null : DiscordUtil.getUser(userId);
        }

        public DBNation getNation() {
            return nationId == null ? null : DBNation.getById(nationId);
        }

        public boolean isValid() {
            return getUser() != null || getNation() != null;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(30);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            if (userId != null) {
                data.put("user", userId);
                data.put("user_valid", (getUser() != null));
            }
            if (nationId != null) {
                data.put("nation", nationId);
                data.put("nation_valid", (isValid()));
            }
            data.put("expires", (timestamp));
            return data;
        }
    }

    private static final Map<Long, Map.Entry<String, JsonObject>> tokenToUserMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> tokenHashes = new ConcurrentHashMap<>();

    private static Map<String, String> ORIGINAL_PAGE = new ConcurrentHashMap<>();

    private static final Map<UUID, Auth> pendingWebCmdTokens = new ConcurrentHashMap<>();

    private static final Map<UUID, Auth> webCommandAuth;

    private static final WebDB webDb;

    static {
        try {
            webDb = new WebDB();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        for (Map.Entry<Long, Map.Entry<String, JsonObject>> entry : webDb.loadTokens().entrySet()) {
            addAccessToken(entry.getValue().getKey(), entry.getValue().getValue(), false);
        }
        webCommandAuth = new ConcurrentHashMap<>(webDb.loadTempTokens());
    }
    public static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String API_URL = "https://discord.com/api/users/@me";

    public static void logout(Context context) {
        logout(context, true);
    }

    public static void logout(Context context, boolean redirect) {
        for (PageHandler.CookieType type : PageHandler.CookieType.values()) {
            context.removeCookie(type.getCookieId());
        }
        if (redirect) {
            context.redirect(WebRoot.REDIRECT);
        }
    }

    public static String getDiscordAuthUrl() {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", Settings.INSTANCE.APPLICATION_ID + ""));
        params.add(new BasicNameValuePair("redirect_uri", WebRoot.REDIRECT));
        params.add(new BasicNameValuePair("response_type", "code"));
        params.add(new BasicNameValuePair("scope", "identify guilds"));
        String query = URLEncodedUtils.format(params, "UTF-8");
        return AUTHORIZE_URL + "?" + query;
    }
    public static JsonObject getUser(String accessToken) throws IOException {
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

    public static String getAccessToken(String code) throws IOException {
        CloseableHttpClient client = HttpClients.custom().build();


        System.out.println("Redirect " + WebRoot.REDIRECT);
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

        System.out.println("Get access token " + obj);

        return accessToken == null ? null : accessToken.getAsString();
    }



    private static void addAccessToken(String accessToken, JsonObject userInfo, boolean updateDB) {
        JsonElement userIdStr = userInfo.get("id");
        if (userIdStr != null) {
            long userId = Long.parseLong(userIdStr.getAsString());
            tokenToUserMap.put(userId, new AbstractMap.SimpleEntry<>(accessToken, userInfo));

            String hash = hash(userId + accessToken);
            tokenHashes.put(hash, userId);

            if (updateDB) {
                webDb.addToken(userId, accessToken, userInfo);
            }
        }
    }

    private static String hash(String key) {
        return Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
    }

    public static Auth getAuth(Context ctx) {
        try {
            return getAuth(ctx, false, false, false);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Auth getAuth(Context context, boolean allowRedirect, boolean requireNation, boolean requireUser) throws IOException {
        if ((requireNation || requireUser) && !allowRedirect) {
            throw new IllegalArgumentException("Cannot require nation or user without allowing redirect");
        }
        Map<String, String> cookies = context.cookieMap();

        String oAuthCookieId = PageHandler.CookieType.DISCORD_OAUTH.getCookieId();
        String discordAuth = cookies.get(oAuthCookieId);
        if (discordAuth == null) {
            String setCookie = context.res.getHeader("Set-Cookie");
            if (setCookie != null && !setCookie.isEmpty()) {
                List<HttpCookie> httpCookies = HttpCookie.parse(setCookie);
                for (HttpCookie cookie : httpCookies) {
                    if (cookie.getName().equals(oAuthCookieId)) {
                        discordAuth = cookie.getValue();
                        break;
                    }
                }
            }
        }
        String message = null;

        Long discordIdFinal = null;
        Integer nationIdFinal = null;

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
                        discordIdFinal = userId;
                    }
                }
            }
            if (discordIdFinal == null) {
                context.removeCookie(PageHandler.CookieType.DISCORD_OAUTH.getCookieId());
            }
        }
        if (discordIdFinal == null) {
            Map<String, List<String>> queries = context.queryParamMap();
            List<String> code = queries.get("code");
            if (code != null && code.size() == 1) {
                String codeSingle = code.get(0);
                new Exception().printStackTrace();
                String access_token = getAccessToken(codeSingle);
                if (access_token != null) {
                    JsonObject user = getUser(access_token);
                    JsonElement idStr = user.get("id");

                    if (idStr != null) {
                        discordIdFinal = Long.parseLong(idStr.getAsString());
                        String hash = hash(discordIdFinal + access_token);
                        context.cookie(oAuthCookieId, hash, 60 * 60 * 24 * 30);
                        addAccessToken(access_token, user, true);
                    }
                }
            }
        }

        {
            // if command auth exists
            String commandAuth = cookies.get(PageHandler.CookieType.URL_AUTH.getCookieId());
            if (commandAuth != null) {
                UUID uuid = UUID.fromString(commandAuth);
                Auth auth = webCommandAuth.get(uuid);
                if (auth != null) {
                    if (auth.nationId != null) {
                        nationIdFinal = auth.nationId;
                    }
                    if (auth.userId != null) {
                        discordIdFinal = auth.userId;
                    }
                }
            }
        }

        Map<String, List<String>> queryMap = context.queryParamMap();
        String path = context.path();
        boolean isLoginPage = path.contains("page/login");

        if (isLoginPage) {
            if (queryMap.containsKey("token")) {
                String token = StringMan.join(queryMap.getOrDefault("token", new ArrayList<>()), ",");
                if (token != null) {
                    try {
                        UUID uuid = UUID.fromString(token);
                        Auth auth = pendingWebCmdTokens.remove(uuid);
                        if (auth != null) {
                            if (auth.timestamp() > System.currentTimeMillis() - TimeUnit.DAYS.toMinutes(15)) {
                                if (auth.nationId != null) {
                                    nationIdFinal = auth.nationId;
                                }
                                if (auth.userId != null) {
                                    discordIdFinal = auth.userId;
                                }
                                UUID verifiedUid = UUID.randomUUID();
                                context.cookie(PageHandler.CookieType.URL_AUTH.getCookieId(), verifiedUid.toString(), 60 * 60 * 24 * 30);
                                webCommandAuth.put(verifiedUid, auth);
                                webDb.addTempToken(verifiedUid, auth);
                                String redirect = getRedirect(context);
                                if (redirect != null) {
                                    throw new RedirectResponse(0, redirect);
                                }
                                return null;
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

        Auth auth = discordIdFinal == null && nationIdFinal == null ? null : new Auth(nationIdFinal, discordIdFinal, Long.MAX_VALUE);
        if (auth != null) {
            DBNation nation = auth.getNation();
            User user = auth.getUser();
            if (requireUser && user == null) {
                throw new RedirectResponse(0, getDiscordAuthUrl());
            }
            if ((user != null || nation != null) && (!requireNation || nation != null)) {
                return auth;
            }
        }

        if (requireUser) {
            throw new RedirectResponse(0, getDiscordAuthUrl());
        }

        if (requireNation || isLoginPage) {
            requireNation |= queryMap.containsKey("nation") || queryMap.containsKey("alliance");
            String nationStr = StringMan.join(queryMap.getOrDefault("nation", new ArrayList<>()), ",");
            Integer nationIdFilter = null;
            if (!nationStr.isEmpty()) {
                nationIdFilter = DiscordUtil.parseNationId(nationStr);
            }

            List<String> errors = new ArrayList<>();
            String errorsStr = StringMan.join(queryMap.getOrDefault("message", new ArrayList<>()), "\n");
            if (!errorsStr.isEmpty()) {
                errors.addAll(Arrays.asList(errorsStr.split("\n")));
            }
            if (nationIdFilter != null) {
                if (message != null) errors.add(message);
                DBNation nation = DBNation.getById(nationIdFilter);
                if (nation != null) {
                    UUID tmpUid = UUID.randomUUID();
                    String authUrl = WebRoot.REDIRECT + "/page/login?token=" + tmpUid + "&nation=" + nation.getNation_id();
                    Auth tmp = new Auth(nation.getNation_id(), null, System.currentTimeMillis());
                    pendingWebCmdTokens.put(tmpUid, tmp);

                    String title = "Locutus Login | timestamp:" + System.currentTimeMillis();
                    String body = "<b>DO NOT SHARE THIS URL OR OPEN IT IF YOU DID NOT REQUEST IT:</b><br>" +
                            MarkupUtil.htmlUrl(Settings.INSTANCE.WEB.REDIRECT + " | Verify Login", authUrl);

                    ApiKeyPool pool = ApiKeyPool.create(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY);
                    JsonObject result = nation.sendMail(pool, title, body, true);
                    JsonElement success = result.get("success");
                    if (success != null && success.getAsBoolean()) {
                        List<Mail> mails = new SearchMailTask(Locutus.imp().getRootAuth(), title, true, true, false, null).call();

                        String mailUrl;
                        if (mails.size() > 0) {
                            mailUrl = Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + mails.get(0).id;
                        } else {
                            mailUrl = Settings.INSTANCE.PNW_URL() + "/mail/inbox";
                        }
                        throw new RedirectResponse(0, mailUrl);
                    } else {
                        errors.add("Could not send mail to nation: " + nationIdFilter + " | " + result);
                    }
                } else {
                    errors.add("Could not find nation with id: " + nationIdFilter);
                }
            }

            String allianceStr = StringMan.join(queryMap.getOrDefault("alliance", new ArrayList<>()), ",");
            Set<Integer> allianceIdFilter = null;
            if (!allianceStr.isEmpty()) {
                allianceIdFilter = PnwUtil.parseAlliances(null, allianceStr);
            }
            // Please select your nation
            List<DBNation> nations;
            if (allianceIdFilter != null) {
                nations = new ArrayList<>(Locutus.imp().getNationDB().getNations(allianceIdFilter));
            } else {
                nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
            }
            // Sort nations by lasst_active (descending)
            nations.sort((o1, o2) -> Long.compare(o2.lastActiveMs(), o1.lastActiveMs()));
            // Name,Url
            List<String> nationNames = nations.stream().map(DBNation::getNation).toList();
            List<Integer> nationIds = nations.stream().map(DBNation::getId).toList();

            JsonArray nationArray = new JsonArray();
            for (String name : nationNames) {
                nationArray.add(name);
            }
            JsonArray nationIdArray = new JsonArray();
            for (Integer id : nationIds) {
                nationIdArray.add(id);
            }
            if (requireNation) {
                String html = rocker.auth.nationpicker.template(errors, nationArray, nationIdArray).render().toString();
                throw new RedirectResponse(0, html);
            } else {
                allowRedirect = true;
            }
        }

        if (allowRedirect) {
            String discordAuthUrl = getDiscordAuthUrl();
            String mailAuthUrl = WebRoot.REDIRECT + "/page/login?nation";

            String html = rocker.auth.picker.template(discordAuthUrl, mailAuthUrl).render().toString();
            throw new RedirectResponse(0, html);
        }
        return null;
    }

    public static String setRedirect(Context context) {
        String fingerprint = (context.ip() + context.userAgent());
        String hash = Hashing.sha256().hashString(fingerprint, StandardCharsets.UTF_8).toString();
        ORIGINAL_PAGE.put(hash, context.url());
        return hash;
    }

    public static String getRedirect(Context context) {
        return getRedirect(context, false);
    }
    public static String getRedirect(Context context, boolean indexDefault) {
        String redirect = context.queryParam("redirect");
        if (redirect == null || redirect.isEmpty()) {
            String fingerprint = (context.ip() + context.userAgent());
            String hash = Hashing.sha256().hashString(fingerprint, StandardCharsets.UTF_8).toString();
            redirect = ORIGINAL_PAGE.remove(hash);
            if (redirect != null && (redirect.contains("login") || redirect.contains("logout"))) {
                redirect = null;
            }
            if (indexDefault && redirect == null) {
                redirect = WebRoot.REDIRECT;
            }
        }
        return redirect;
    }
}

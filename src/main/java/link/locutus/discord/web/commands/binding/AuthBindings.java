package link.locutus.discord.web.commands.binding;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
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
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.web.jooby.PageHandler.cookieId;

public class AuthBindings extends WebBindingHelper {

    @Binding
    @Me
    public User user(Context context, @Me @Default DBNation nation) {
        if (nation != null) {
            User user = nation.getUser();
            if (user != null) {
                return user;
            }
            // Register page
            String url = CM.register.cmd.toCommandUrl();
            context.redirect(url);
            throw new UnauthorizedResponse("Please register your nation first: " + MarkupUtil.htmlUrl(url, url));
        }
        throw new IllegalStateException("No nation found in command locals");
//        getAuth(context, false);
    }
    @Binding
    @Me
    public DBNation nation(Context context, @Default @Me User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) throw new IllegalArgumentException("Please use " + CM.register.cmd.toSlashMention() + "");
        return nation;
    }

    @Me
    @Binding
    public Guild guild(Context context, @Default @Me DBNation nation, @Default @Me User user) {
        String guildCookieId = cookieId(PageHandler.CookieType.GUILD_ID);
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
        if (user == null && nation == null) {
            // auth page
        }

        // TODO have the guild select page show a link to register if you aren't already
        // TODO have the guild select page show a message if you are not in an alliance

        // TODO have the guild select page show if your alliance does not have Locutus
        // TODO have the guild select page show your alliance guild

        if (user == null && nation != null) {
            // Register user or use nation's DB
            GuildDB db = nation.getGuildDB();
            if (db != null) {
                return db.getGuild();
            }
        }
//        String url = WM.guildindex.cmd.toPageUrl();
//        context.redirect(url);
        throw new IllegalStateException("No guild set in command locals");
    }

    public record Auth(Integer nationId, Long userId, long timestamp) {

        public User getUser() {
            return userId == null ? null : DiscordUtil.getUser(userId);
        }

        public DBNation getNation() {
            return nationId == null ? null : DBNation.byId(nationId);
        }

        public boolean isValid() {
            return getUser() != null || getNation() != null;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(30);
        }
    }

    private final WebDB webDb;
    private final Map<Long, Map.Entry<String, JsonObject>> tokenToUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenHashes = new ConcurrentHashMap<>();

    private Map<String, String> ORIGINAL_PAGE = new ConcurrentHashMap<>();

    private final Map<UUID, Auth> pendingWebCmdTokens = new ConcurrentHashMap<>();

    private final Map<UUID, Auth> webCommandAuth;

    public AuthBindings(boolean ignore) {
        try {
            this.webDb = new WebDB();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        ;
        for (Map.Entry<Long, Map.Entry<String, JsonObject>> entry : webDb.loadTokens().entrySet()) {
            addAccessToken(entry.getValue().getKey(), entry.getValue().getValue(), false);
        }
        webCommandAuth = new ConcurrentHashMap<>(webDb.loadTempTokens());
    }

    public static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String API_URL = "https://discord.com/api/users/@me";
    public void logout(Context context) {
        for (PageHandler.CookieType type : PageHandler.CookieType.values()) {
            context.removeCookie(cookieId(type));
        }
        context.redirect(WebRoot.REDIRECT);
    }

    private String getDiscordAuthUrl() {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", Settings.INSTANCE.APPLICATION_ID + ""));
        params.add(new BasicNameValuePair("redirect_uri", WebRoot.REDIRECT));
        params.add(new BasicNameValuePair("response_type", "code"));
        params.add(new BasicNameValuePair("scope", "identify guilds"));
        String query = URLEncodedUtils.format(params, "UTF-8");
        return AUTHORIZE_URL + "?" + query;
    }

    public void login(Context context) {
        setRedirect(context);
        String url = getDiscordAuthUrl();
        context.header("cache-control", "no-store");
        context.redirect(url);
        throw new UnauthorizedResponse("Login via discord OAuth initiated. Redirect to " + url);
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
                webDb.addToken(userId, accessToken, userInfo);
            }
        }
    }

    private String hash(String key) {
        return Hashing.sha256()
                .hashString(key.toString(), StandardCharsets.UTF_8)
                .toString();
    }

    public Auth getAuth(Context ctx) {
        try {
            return getAuth(ctx, false);
        } catch (IOException e) {
            return null;
        }
    }

    private Auth getAuth(Context context, boolean allowRedirect) throws IOException {
        Map<String, String> cookies = context.cookieMap();
        String oAuthCookieId = cookieId(PageHandler.CookieType.DISCORD_OAUTH);
        String discordAuth = cookies.get(oAuthCookieId);

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
                        message = ("Unable to verify login user id (1): " + userId);
                    }
                }
            }
            context.removeCookie(cookieId(PageHandler.CookieType.DISCORD_OAUTH));
        }
        {
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
                            long userId = Long.parseLong(idStr.getAsString());
                            String hash = hash(userId + access_token);

                            context.cookie(oAuthCookieId, hash, 60 * 60 * 24 * 30);

                            addAccessToken(access_token, user, true);

                            Auth auth = new Auth(null, userId, Long.MAX_VALUE);
                            if (auth.isValid() && !auth.isExpired()) return auth;
                            message = ("Unable to verify login user id (2): " + userId);
                        }
                    }
                } finally {
                    if (allowRedirect) {
                        String redirect = ORIGINAL_PAGE.remove(context.ip());
                        if (redirect == null) redirect = WebRoot.REDIRECT;
                        context.header("Location", redirect);
                        context.header("cache-control", "no-store");
                        context.redirect(redirect);
                        throw new UnauthorizedResponse("Discord OAuth `code` authentication redirect set in context. Catch this error");
                    }
                }
            }
        }

        {
            // if command auth exists
            String commandAuth = cookies.get(cookieId(PageHandler.CookieType.URL_AUTH));
            if (commandAuth != null) {
                UUID uuid = UUID.fromString(commandAuth);
                Auth  auth = webCommandAuth.get(uuid);
                if (auth.isValid() && !auth.isExpired()) return auth;
                context.removeCookie(cookieId(PageHandler.CookieType.URL_AUTH));
            }
        }

        Set<Integer> allianceIdFilter = null;
        Integer nationIdFilter = null;
        {
            // get the path
            String path = context.path();

            System.out.println(":||Path " + path);
            if (path.equalsIgnoreCase("auth")) {
                Map<String, List<String>> queryMap = context.queryParamMap();

                String allianceStr = StringMan.join(queryMap.getOrDefault("alliance", new ArrayList<>()), ",");
                if (allianceStr != null) {
                    allianceIdFilter = PnwUtil.parseAlliances(null, allianceStr);
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
                                    }
                                    if (nationId != null) {
                                        message = "Could not find nation for id: " + nationId;
                                    }
                                    auth = null;
                                } else {
                                    UUID verifiedUid = UUID.randomUUID();
                                    context.cookie(cookieId(PageHandler.CookieType.URL_AUTH), verifiedUid.toString(), 60 * 60 * 24 * 30);
                                    context.removeCookie(cookieId(PageHandler.CookieType.DISCORD_OAUTH));
                                    webCommandAuth.put(verifiedUid, auth);
                                    webDb.addTempToken(verifiedUid, auth);

                                    if (allowRedirect) {
                                        String redirect = getRedirect(context);
                                        context.header("Location", redirect);
                                        context.header("cache-control", "no-store");
                                        context.redirect(redirect);

                                        throw new UnauthorizedResponse("URL Authentication page response returned to context. Catch this error");
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

                if (allowRedirect) {
                    List<String> errors = new ArrayList<>();
                    if (message != null) errors.add(message);

                    if (nationIdFilter != null) {
                        DBNation nation = DBNation.byId(nationIdFilter);
                        if (nation != null) {
                            UUID tmpUid = UUID.randomUUID();
                            String authUrl = WebRoot.REDIRECT + "/auth?token=" + tmpUid + "&nation=" + nation.getNation_id();
                            Auth auth = new Auth(nation.getNation_id(), null, System.currentTimeMillis());
                            pendingWebCmdTokens.put(tmpUid, auth);

                            String title = "Locutus Login | timestamp:" + System.currentTimeMillis();
                            String body = "<b>DO NOT SHARE THIS URL OR OPEN IT IF YOU DID NOT REQUEST IT:</b><br>" +
                                    MarkupUtil.htmlUrl("https://locutus.link | Verify Login", authUrl);

                            ApiKeyPool pool = ApiKeyPool.create(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY);
                            JsonObject result = nation.sendMail(pool, title, body);
                            JsonElement success = result.get("success");
                            if (success != null && success.getAsBoolean()) {
                                List<Mail> mails = new SearchMailTask(Locutus.imp().getRootAuth(), title, true, true, false, null).call();

                                String mailUrl;
                                if (mails.size() > 0) {
                                    mailUrl = WebRoot.REDIRECT + "/inbox/message/id=" + mails.get(0).id;
                                } else {
                                    mailUrl = WebRoot.REDIRECT + "/mail/inbox";
                                }
                                // set REDIRECT map to the current url

                                // redirect them to the mail page
                                context.header("Location", mailUrl);
                                context.header("cache-control", "no-store");
                                context.redirect(mailUrl);

                                throw new UnauthorizedResponse("Authentication page response returned to context. Catch this error");
                            } else {
                                errors.add("Could not send mail to nation: " + nationIdFilter + " | " + result);
                            }
                        } else {
                            errors.add("Could not find nation with id: " + nationIdFilter);
                        }
                    }

                    // Error alerts

                    // Please select your nation
                    List<DBNation> nations;
                    if (allianceIdFilter != null) {
                        nations = new ArrayList<>(Locutus.imp().getNationDB().getNations(allianceIdFilter));
                    } else {
                        nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
                    }
                    // Sort nations by lasst_active (descending)

                    // Name,Url

                    // TODO return auth page

//                    String authPage = createAuthPage(context, allianceIdFilter, nationIdFilter, message);
//                    context.result(authPage);
                    throw new UnauthorizedResponse("Authentication page response returned to context. Catch this error");
                }
            }
        }

        if (allowRedirect) {
            setRedirect(context);

            String discordAuthUrl = getDiscordAuthUrl();
            String mailAuthUrl = WebRoot.REDIRECT + "/auth";

            String html = rocker.auth.picker.template(discordAuthUrl, mailAuthUrl).render().toString();
            context.result(html);
            throw new UnauthorizedResponse("Authentication (1) page response returned to context. Catch this error");
        }

        return null;
    }

    private String setRedirect(Context context) {
        String fingerprint = (context.ip() + context.userAgent());
        String hash = Hashing.sha256().hashString(fingerprint, StandardCharsets.UTF_8).toString();
        ORIGINAL_PAGE.put(hash, context.url());
        return hash;
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
//        String addr = context.ip();
////        if (addr.equals("0:0:0:0:0:0:0:1") || addr.equals("[0:0:0:0:0:0:0:1]") || addr.equals("127.0.0.1") || addr.equals("[127.0.0.1]")) {
////            return JsonParser.parseString("{\"id\":\"664156861033086987\",\"username\":\"borg\",\"avatar\":\"14aa8f752d52c066ad5ccb87116c90fa\",\"discriminator\":\"5729\",\"public_flags\":128,\"flags\":128,\"locale\":\"en-US\",\"mfa_enabled\":true}").getAsJsonObject();
////        }
        Map<String, String> cookies = context.cookieMap();
        String cookieId = cookieId(PageHandler.CookieType.DISCORD_OAUTH);
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

    public Long getDiscordUser(Context ctx) throws IOException {
        // tODO combine this with getDiscorduseer and move login stuff to here
        JsonObject userJson = getDiscordUserJson(ctx, true);
        if (userJson == null) {
            return null;
        }
        Long userId = Long.parseLong(userJson.get("id").getAsString());
        return userId;
    }

    //    @Override
    public DBNation getNation(Context ctx) throws IOException {
        Long userId = getDiscordUser(ctx);
        DBNation nation = DiscordUtil.getNation(userId);
        return nation;
    }
}

package link.locutus.discord.web.jooby;


import io.javalin.http.Handler;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.web.jooby.handler.LocutusSSLHandler;
import link.locutus.discord.web.jooby.handler.SseClient2;
import link.locutus.discord.web.jooby.handler.SseHandler2;
import link.locutus.discord.web.test.WebDB;
import com.fizzed.rocker.runtime.RockerRuntime;
import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.server.Server;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WebRoot {
    private static final String authorizeURL = "https://discord.com/api/oauth2/authorize";
    private static final String tokenURL = "https://discord.com/api/oauth2/token";
    private static final String apiURLBase = "https://discord.com/api/users/@me";

    public static String REDIRECT = "https://locutus.link";
    private static String COOKIE_ID = "LCTS";
    private final PageHandler pageHandler;
    private final File fileRoot;
    private final WebDB db;

    private final Map<Long, Map.Entry<String, JsonObject>> tokenToUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenHashes = new ConcurrentHashMap<>();

    private final Javalin app;

    private static WebRoot INSTANCE;

    private final BankRequestHandler legacyBankHandler;


    public WebRoot(int portMain, int portHTTPS) {
        if (Settings.INSTANCE.CLIENT_SECRET.isEmpty()) throw new IllegalArgumentException("Please set CLIENT_SECRET in " + Settings.INSTANCE.getDefaultFile());
        if (INSTANCE != null) throw new IllegalArgumentException("Already initialized");
        if (portHTTPS > 0 && portHTTPS != 443) {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT + ":" + portHTTPS;
        } else if (portHTTPS <= 0) {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT + ":" + portMain;
        }
        INSTANCE = this;

        RockerRuntime.getInstance().setReloading(true);

        this.legacyBankHandler = new BankRequestHandler();

        Map<String, String> staticFileMap = new LinkedHashMap<>();
        staticFileMap.put("src/main/css", "/css");
        staticFileMap.put("src/main/js", "/js");
        staticFileMap.put("src/main/img", "/");

        this.app = Javalin.create(config -> {
            config.server(() -> {
                Server server = new Server(); // configure this however you want
                LocutusSSLHandler.configureServer(server, portMain, portHTTPS);
                return server;
            });
            for (Map.Entry<String, String> entry : staticFileMap.entrySet()) {
                config.addStaticFiles(staticFiles -> {
                    staticFiles.hostedPath = entry.getValue();                   // change to host files on a subpath, like '/assets'
                    staticFiles.directory = entry.getKey();              // the directory where your files are located
                    staticFiles.location = Location.CLASSPATH;      // Location.CLASSPATH (jar) or Location.EXTERNAL (file system)
                    staticFiles.precompress = !Settings.INSTANCE.WEB.DEVELOPMENT;                // if the files should be pre-compressed and cached in memory (optimization)
//                staticFiles.aliasCheck = null;                  // you can configure this to enable symlinks (= ContextHandler.ApproveAliases())
//                staticFiles.headers = Map.of(...);              // headers that will be set for the files
//                staticFiles.skipFileFunction = req -> false;    // you can use this to skip certain files in the dir, based on the HttpServletRequest
                });
            }
        }).start();

        this.pageHandler = new PageHandler(this);

        try {
            this.db = new WebDB();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        for (Map.Entry<Long, Map.Entry<String, JsonObject>> entry : db.loadTokens().entrySet()) {
            addAccessToken(entry.getValue().getKey(), entry.getValue().getValue(), false);
        }

        this.app.get("/bankrequests", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                if (!Settings.WHITELISTED_IPS.contains(context.ip())) {
                    System.out.println("Not whitelisted: " + context.ip());
                    return;
                }
                JSONObject request = legacyBankHandler.pollRequest();
                if (request != null) {
                    context.result(request.toString());
                } else {
                    context.result("");
                }
            }
        });

        this.app.post("/bankcallback**", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                if (!Settings.WHITELISTED_IPS.contains(context.ip())) {
                    System.out.println("Not whitelisted: " + context.ip());
                    return;
                }
                System.out.println("IP : " + context.ip());
                UUID token = UUID.fromString(context.queryParam("token"));
                String message = context.queryParam("result");

                legacyBankHandler.callBack(token, message);
            }
        });

        this.app.get("/robots.txt", ctx -> ctx.result("User-agent: *\nDisallow: /"));
        this.app.get("/logout", ctx -> logout(ctx));

        this.app.get("/{guild_id}/sse/**", new SseHandler2(new Consumer<SseClient2>() {
            @Override
            public void accept(SseClient2 sse) {
                pageHandler.sse(sse);
            }
        }));

        this.app.get("/{guild_id}/sse_reaction**", new SseHandler2(new Consumer<SseClient2>() {
            @Override
            public void accept(SseClient2 sse) {
                pageHandler.sseReaction(sse);
            }
        }));
        this.app.get("/{guild_id}/sse_cmd_str**", new SseHandler2(new Consumer<SseClient2>() {
            @Override
            public void accept(SseClient2 sse) {
                pageHandler.sseCmdStr(sse);
            }
        }));

        this.app.get("cmd_page/{guild_id}/**", new SseHandler2(new Consumer<SseClient2>() {
            @Override
            public void accept(SseClient2 sse) {
                try {
                    pageHandler.sseCmdPage(sse);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }));

        this.app.get("/discordids", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                Map<Long, PNWUser> users = Locutus.imp().getDiscordDB().getRegisteredUsers();
                StringBuilder result = new StringBuilder();
                for (Map.Entry<Long, PNWUser> entry : users.entrySet()) {
                    PNWUser user = entry.getValue();
                    result.append(user.getNationId() + "\t" + user.getDiscordId() + "\t" + user.getDiscordName() + "\n");
                }
                context.result(result.toString().trim());
            }
        });

        for (String cmd : pageHandler.getCommands().getSubCommandIds()) {
            List<String> patterns = Arrays.asList(
//                    "/" + cmd + "/**",
//                    "/" + cmd,
                    "/{guild_id}/" + cmd + "/**",
                    "/{guild_id}/" + cmd
            );
            for (String pattern : patterns) {
                this.app.get(pattern, ctx -> {
                    pageHandler.handle(ctx);
                });
                this.app.post(pattern, ctx -> {
                    pageHandler.handle(ctx);
                });
            }
        }

        this.fileRoot = new File("files");

        this.app.get("/", ctx -> {
            pageHandler.handle(ctx);
        });
//        get("/favicon.ico", ctx -> null);
    }

    public BankRequestHandler getLegacyBankHandler() {
        return legacyBankHandler;
    }

    public static WebRoot getInstance() {
        return INSTANCE;
    }

    public File getFileRoot() {
        return fileRoot;
    }

    //    {
//        get("/{guildid}", ctx -> {
//            Map<String, String> path = ctx.pathMap();
//            Map<String, String> headers = ctx.headerMap();
//            String url = ctx.getRequestURL();
//            Map<String, String> query = ctx.queryMap();
//            String method = ctx.getMethod();
//            Map<String, Object> attributes = ctx.getAttributes();
//        });
//    }

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
                .setUri(apiURLBase)
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
                .setUri(tokenURL)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .addParameter("grant_type", "authorization_code")
                .addParameter("client_id", Settings.INSTANCE.APPLICATION_ID + "")
                .addParameter("client_secret", Settings.INSTANCE.CLIENT_SECRET)
                .addParameter("redirect_uri", REDIRECT)
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

    public void logout(Context context) throws IOException {
        String cookieId = cookieId(context);
        context.removeCookie(cookieId);

        context.redirect(REDIRECT);
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

    public PageHandler getPageHandler() {
        return pageHandler;
    }

    public WebDB getDb() {
        return db;
    }

    private Map<String, String> ORIGINAL_PAGE = new ConcurrentHashMap<>();

    public void login(Context context) {
        String url = context.fullUrl();
        if (!url.toLowerCase().contains("logout")) {
            ORIGINAL_PAGE.put(context.ip(), url);
        }

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", Settings.INSTANCE.APPLICATION_ID + ""));
        params.add(new BasicNameValuePair("redirect_uri", REDIRECT));
        params.add(new BasicNameValuePair("response_type", "code"));
        params.add(new BasicNameValuePair("scope", "identify guilds"));
        String query = URLEncodedUtils.format(params, "UTF-8");
        String redirect = authorizeURL + "?" + query;
        context.header("Location", redirect);
        context.header("cache-control", "no-store");

        context.redirect(redirect);
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException, LoginException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT_HTTPS = 0;
        Settings.INSTANCE.WEB.PORT_HTTP = 8000;
        Settings.INSTANCE.WEB.REDIRECT = "http://localhost";
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();

        Locutus locutus = Locutus.create().start();

        System.out.println("Port " + Settings.INSTANCE.WEB.PORT_HTTP + " | " + Settings.INSTANCE.WEB.PORT_HTTPS);
        WebRoot webRoot = new WebRoot(Settings.INSTANCE.WEB.PORT_HTTP, Settings.INSTANCE.WEB.PORT_HTTPS);
    }
}

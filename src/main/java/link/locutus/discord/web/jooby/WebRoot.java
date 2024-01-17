package link.locutus.discord.web.jooby;


import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.community.ssl.SSLPlugin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.staticfiles.StaticFileConfig;
import io.javalin.rendering.JavalinRenderer;
import io.javalin.rendering.template.JavalinJte;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.web.jooby.handler.SseClient2;
import link.locutus.discord.web.jooby.handler.SseHandler2;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class WebRoot {
    public static String REDIRECT = "https://locutus.link";

    private final PageHandler pageHandler;
    private final File fileRoot;
    private final Javalin app;

    private static WebRoot INSTANCE;

    private final BankRequestHandler legacyBankHandler;


    private TemplateEngine createTemplateEngine() {
        return TemplateEngine.createPrecompiled(Path.of("src/main/jte"), ContentType.Plain);
    }

    public WebRoot(int portMain, int portHTTPS) {
        if (Settings.INSTANCE.CLIENT_SECRET.isEmpty()) throw new IllegalArgumentException("Please set CLIENT_SECRET in " + Settings.INSTANCE.getDefaultFile());
        if (INSTANCE != null) throw new IllegalArgumentException("Already initialized");
        if (portHTTPS > 0 && portHTTPS != 443) {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT + ":" + portHTTPS;
        } else if (portHTTPS <= 0) {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT + ":" + portMain;
        } else {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT;
        }
        INSTANCE = this;

        this.legacyBankHandler = new BankRequestHandler();

        Map<String, String> staticFileMap = new LinkedHashMap<>();
        staticFileMap.put("/css", "/css");
        staticFileMap.put("/js", "/js");
        staticFileMap.put("/img", "/");

        SSLPlugin plugin = new SSLPlugin(conf -> {
            if (Settings.INSTANCE.WEB.PRIVKEY_PASSWORD.isEmpty()) {
                conf.pemFromPath(Settings.INSTANCE.WEB.CERT_PATH, Settings.INSTANCE.WEB.PRIVKEY_PATH);
            } else {
                conf.pemFromPath(Settings.INSTANCE.WEB.CERT_PATH, Settings.INSTANCE.WEB.PRIVKEY_PATH, Settings.INSTANCE.WEB.PRIVKEY_PASSWORD);
            }
            conf.securePort = Settings.INSTANCE.WEB.PORT_HTTPS;
            if (Settings.INSTANCE.WEB.PORT_HTTP > 0) {
                conf.insecurePort = Settings.INSTANCE.WEB.PORT_HTTP;
            }
        });
        JavalinJte.init(createTemplateEngine());
//        BrotliLoader.isBrotliAvailable();
        this.app = Javalin.create(config -> {
            config.plugins.register(plugin);
//            config.enableCorsForOrigin();
            config.compression.brotliAndGzip();
//            config.server(() -> {
//                Server server = new Server(); // configure this however you want
//                LocutusSSLHandler.configureServer(server, portMain, portHTTPS);
//                return server;
//            });
            for (Map.Entry<String, String> entry : staticFileMap.entrySet()) {
                config.staticFiles.add(new Consumer<StaticFileConfig>() {
                    @Override
                    public void accept(StaticFileConfig staticFiles) {
                        staticFiles.hostedPath = entry.getValue();                   // change to host files on a subpath, like '/assets'
                        staticFiles.directory = entry.getKey();              // the directory where your files are located
                        staticFiles.location = Location.CLASSPATH;      // Location.CLASSPATH (jar) or Location.EXTERNAL (file system)
                        staticFiles.precompress = true;                // if the files should be pre-compressed and cached in memory (optimization)
                    }
                });
            }
        }).start();

        System.out.println("Started");

        this.pageHandler = new PageHandler(this);

        this.app.get("/test", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                pageHandler.handle(context);
            }
        });

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
        this.app.get("/logout", ctx -> pageHandler.logout(ctx));

        this.app.get("/sse/**", new SseHandler2(new Consumer<SseClient2>() {
            @Override
            public void accept(SseClient2 sse) {
                pageHandler.sse(sse);
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

        for (String cmd : Locutus.imp().getCommandManager().getV2().getCommands().getSubCommandIds()) {
            List<String> patterns = Arrays.asList(
                    "/command/" + cmd + "/**",
                    "/command/" + cmd
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

        this.app.get("/page/**", ctx -> {
            pageHandler.handle(ctx);
        });
        this.app.post("/page/**", ctx -> {
            pageHandler.handle(ctx);
        });
        this.app.get("/rest/**", ctx -> {
            pageHandler.handle(ctx);
        });
        this.app.post("/rest/**", ctx -> {
            pageHandler.handle(ctx);
        });

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

    public PageHandler getPageHandler() {
        return pageHandler;
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException, LoginException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();

        Locutus locutus = Locutus.create().start();

        System.out.println("Port " + Settings.INSTANCE.WEB.PORT_HTTP + " | " + Settings.INSTANCE.WEB.PORT_HTTPS);
        WebRoot webRoot = new WebRoot(Settings.INSTANCE.WEB.PORT_HTTP, Settings.INSTANCE.WEB.PORT_HTTPS);
    }
}

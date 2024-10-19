package link.locutus.discord.web.jooby;


import com.aayushatharva.brotli4j.Brotli4jLoader;
import link.locutus.discord.Logg;
import link.locutus.discord.web.test.WebDB;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import gg.jte.watcher.DirectoryWatcher;
import io.javalin.Javalin;
import io.javalin.community.ssl.SSLPlugin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.staticfiles.StaticFileConfig;
import io.javalin.rendering.template.JavalinJte;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.web.jooby.handler.SseClient2;
import link.locutus.discord.web.jooby.handler.SseHandler2;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

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
    private final WebDB webDB;
    private final File fileRoot;
    private final Javalin app;

    private static WebRoot INSTANCE;

    private final BankRequestHandler legacyBankHandler;
    private final TemplateEngine jteEngine;
    private final DirectoryCodeResolver jteResolver;

    private static TemplateEngine createTemplateEngine() {
        TemplateEngine engine = TemplateEngine.createPrecompiled(Path.of("src/main/jte"), ContentType.Plain);
        engine.setHtmlCommentsPreserved(false);
        engine.setTrimControlStructures(true);
        engine.setBinaryStaticContent(true);
        return engine;
    }

    private static DirectoryCodeResolver createResolver() {
        return new DirectoryCodeResolver(Path.of("src/main/jte"));
    }

    public WebRoot(int port, boolean ssl) throws SQLException, ClassNotFoundException {
        if (Settings.INSTANCE.CLIENT_SECRET.isEmpty()) throw new IllegalArgumentException("Please set CLIENT_SECRET in " + Settings.INSTANCE.getDefaultFile());
        if (INSTANCE != null) throw new IllegalArgumentException("Already initialized");
        if (port > 0 && port != (ssl ? 443 : 80)) {
            REDIRECT = Settings.INSTANCE.WEB.BACKEND_DOMAIN + ":" + port;
        } else {
            REDIRECT = Settings.INSTANCE.WEB.BACKEND_DOMAIN;
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
            conf.securePort = port;
            conf.redirect = true;
            conf.insecure = false;
        });

        this.jteEngine = createTemplateEngine();
        this.jteResolver = createResolver();
        JavalinJte.init(jteEngine);
        {
            DirectoryWatcher watcher = new DirectoryWatcher(jteEngine, jteResolver);
            watcher.start(templates -> {
                for (String template : templates) {
                    Logg.text("Reloaded template " + template);
                }
            });
        }

        Logg.text("Starting on port " + port);
//        BrotliLoader.isBrotliAvailable();
        this.app = Javalin.create(config -> {
            if (ssl) {
                config.plugins.register(plugin);
            }
            config.plugins.enableDevLogging(); // Approach 1
//            config.enableCorsForOrigin();
            // check if brotli available
            if (Brotli4jLoader.isAvailable()) {
                Logg.text("Using brotli");
                config.compression.brotliAndGzip();
            } else {
                Logg.text("Using gzip");
                config.compression.gzipOnly();
            }
            for (Map.Entry<String, String> entry : staticFileMap.entrySet()) {
                config.staticFiles.add(new Consumer<StaticFileConfig>() {
                    @Override
                    public void accept(StaticFileConfig staticFiles) {
                        staticFiles.hostedPath = entry.getValue();
                        staticFiles.directory = entry.getKey();
                        staticFiles.location = Location.CLASSPATH;
                        staticFiles.precompress = true;
                    }
                });
            }
            new File("/files").mkdirs();
            config.staticFiles.add(new Consumer<StaticFileConfig>() {
                @Override
                public void accept(StaticFileConfig staticFiles) {
                    staticFiles.hostedPath = "/files";
                    staticFiles.directory = "files";
                    staticFiles.location = Location.EXTERNAL;
                }
            });
        }).start(port);

        this.pageHandler = new PageHandler(this);
        this.webDB = new WebDB();

        this.app.get("/bankrequests", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                if (!Settings.WHITELISTED_IPS.contains(context.ip())) {
                    Logg.text("Not whitelisted: " + context.ip());
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

        this.app.post("/bankcallback*", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                if (!Settings.WHITELISTED_IPS.contains(context.ip())) {
                    Logg.text("Not whitelisted: " + context.ip());
                    return;
                }
                UUID token = UUID.fromString(context.queryParam("token"));
                String message = context.queryParam("result");

                legacyBankHandler.callBack(token, message);
            }
        });

        this.app.get("/robots.txt", ctx -> ctx.result("User-agent: *\nDisallow: /"));

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
                    "/command/" + cmd + "/*",
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

        this.app.get("/page/*", ctx -> {
            System.out.println("Handle page " + ctx.path());
            long start = System.currentTimeMillis();
            pageHandler.handle(ctx);
            long diff = System.currentTimeMillis() - start;
            if (diff > 0) {
                Logg.text("Handled " + ctx.path() + " in " + (System.currentTimeMillis() - start) + "ms");
            }
        });
        this.app.post("/page/*", ctx -> {
            pageHandler.handle(ctx);
        });
//        this.app.get("/api/*", ctx -> {
//            pageHandler.handle(ctx);
//        });
        // Only post requests
        this.app.post("/api/*", ctx -> {
            pageHandler.handle(ctx);
        });

        this.fileRoot = new File("files");

        this.app.get("/", ctx -> {
            pageHandler.handle(ctx);
        });
    }

    public static WebDB db() {
        return INSTANCE.webDB;
    }

    public Javalin getApp() {
        return app;
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

    public PageHandler getPageHandler() {
        return pageHandler;
    }
}

package link.locutus.discord.web.jooby;


import com.aayushatharva.brotli4j.Brotli4jLoader;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.config.SizeUnit;
import link.locutus.discord.Logg;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.test.WebDB;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import gg.jte.watcher.DirectoryWatcher;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.staticfiles.StaticFileConfig;
import io.javalin.rendering.template.JavalinJte;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.web.jooby.handler.SseMessageOutput;
import link.locutus.discord.web.jooby.handler.SseHandler2;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

import static link.locutus.discord.web.WebUtil.getPortOrSchemeDefault;

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

        SslPlugin plugin = !ssl ? null : new SslPlugin(conf -> {
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
        {
            DirectoryWatcher watcher = new DirectoryWatcher(jteEngine, jteResolver);
            watcher.start(templates -> {
                for (String template : templates) {
                    Logg.text("Reloaded template " + template);
                }
            });
        }

        Logg.text("Starting on port " + port);

        this.app = Javalin.create(config -> {
            config.fileRenderer(new JavalinJte());
            // default
            config.http.generateEtags = true;
            config.http.maxRequestSize = 1024 * 1024 * 1024; // 1gb
            config.http.asyncTimeout = 60_000; // 60 seconds
            config.jetty.multipartConfig.cacheDirectory("c:/temp");
            config.jetty.multipartConfig.maxFileSize(100, SizeUnit.MB);
            config.jetty.multipartConfig.maxInMemoryFileSize(10, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB);

            if (ssl) {
                config.registerPlugin(plugin);
            }
            config.bundledPlugins.enableDevLogging();

            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(f -> {
                        f.allowCredentials = true;
                        f.allowHost(
                                Settings.INSTANCE.WEB.BACKEND_DOMAIN,
                        Settings.INSTANCE.WEB.FRONTEND_DOMAIN,
                        "http://localhost",
                                "http://localhost:" + getPortOrSchemeDefault(Settings.INSTANCE.WEB.FRONTEND_DOMAIN),
                                "http://localhost:5173",
                        "http://127.0.0.1",
                        "http://127.0.0.1:" + getPortOrSchemeDefault(Settings.INSTANCE.WEB.FRONTEND_DOMAIN)
                        );
                });
            });

            // check if brotli available
            if (Brotli4jLoader.isAvailable()) {
                Logg.text("Using brotli");
                config.http.brotliAndGzipCompression();
            } else {
                Logg.text("Using gzip");
                config.http.gzipOnlyCompression();
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

//        // Disabled. Client doesn't reliably send referrer info
//        app.beforeMatched(ctx -> {
//            if (WebUtil.isThirdPartyRequest(ctx)) {
//                ctx.result("Requests from third party links are not allowed");
//                ((JavalinServletContext) ctx).getTasks().clear();
//            }
//        });

        this.app.get("/robots.txt", ctx -> ctx.result("User-agent: *\nDisallow: /"));

        this.app.get("/sse/**", new SseHandler2(new Consumer<SseMessageOutput>() {
            @Override
            public void accept(SseMessageOutput sse) {
                pageHandler.sse(sse);
            }
        }));

        this.app.get("/discordids", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                String userId = context.queryParam("user");
                if (userId != null) {
                    Long id = Long.parseLong(userId);
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(id, null, null);
                    if (user == null) {
                        context.result("-1\t" + id + "\t");
                    } else {
                        context.result(user.getNationId() + "\t" + user.getDiscordId() + "\t" + user.getDiscordName());
                    }
                    return;
                }
                String nationId = context.queryParam("nation");
                if (nationId != null) {
                    DBNation nation = DiscordUtil.parseNation(nationId);
                    PNWUser user = nation == null ? null : nation.getDBUser();
                    if (user == null) {
                        context.result(nationId + "\t-1\t");
                    } else {
                        context.result(user.getNationId() + "\t" + user.getDiscordId() + "\t" + user.getDiscordName());
                    }
                    return;
                }
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
        this.app.get("/command/", ctx -> {
            pageHandler.handle(ctx);
        });

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
            ctx.contentType(io.javalin.http.ContentType.APPLICATION_JSON);
            pageHandler.handle(ctx);
        });

        this.fileRoot = new File("files");

        this.app.get("/", ctx -> {
            pageHandler.handle(ctx);
        });
    }

//    private void registerClasses(Fury fury) {
////        fury.register(CacheType.class, "CacheType");
////        fury.register(SetGuild.class, "SetGuild");
////        fury.register(TradePriceByDayJson.class, "TradePriceByDayJson");
////        fury.register(WebAnnouncement.class, "WebAnnouncement");
////        fury.register(WebAnnouncements.class, "WebAnnouncements");
////        fury.register(WebAudit.class, "WebAudit");
////        fury.register(WebAudits.class, "WebAudits");
////        fury.register(WebBalance.class, "WebBalance");
////        fury.register(WebBankAccess.class, "WebBankAccess");
////        fury.register(WebBulkQuery.class, "WebBulkQuery");
//////        fury.register(WebMyWar.class, "WebMyWar");
//////        fury.register(WebMyWars.class, "WebMyWars");
////        fury.register(WebOptions.class, "WebOptions");
////        fury.register(WebSession.class, "WebSession");
////        fury.register(WebSuccess.class, "WebSuccess");
////        fury.register(WebInt.class, "WebSuccessInt");
////        fury.register(WebTable.class, "WebTable");
////        fury.register(WebTarget.class, "WebTarget");
////        fury.register(WebTargets.class, "WebTargets");
////        fury.register(WebTransferResult.class, "WebTransferResult");
////        fury.register(WebUrl.class, "WebUrl");
//        fury.register(WebValue.class, "WebValue");
//    }

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

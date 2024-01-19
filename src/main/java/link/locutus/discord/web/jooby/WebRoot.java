package link.locutus.discord.web.jooby;


import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.google.gson.JsonObject;
import com.locutus.wiki.BotWikiGen;
import com.locutus.wiki.WikiGenHandler;
import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.html.HtmlInterceptor;
import gg.jte.html.HtmlPolicy;
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

    public WebRoot(int port, boolean ssl) {
        if (Settings.INSTANCE.CLIENT_SECRET.isEmpty()) throw new IllegalArgumentException("Please set CLIENT_SECRET in " + Settings.INSTANCE.getDefaultFile());
        if (INSTANCE != null) throw new IllegalArgumentException("Already initialized");
        if (port > 0 && port != (ssl ? 443 : 80)) {
            REDIRECT = Settings.INSTANCE.WEB.REDIRECT + ":" + port;
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
                    System.out.println("Reloaded template " + template);
                }
            });
        }

        System.out.println("Starting on port " + port);
//        BrotliLoader.isBrotliAvailable();
        this.app = Javalin.create(config -> {
            if (ssl) {
                config.plugins.register(plugin);
            }
            config.plugins.enableDevLogging(); // Approach 1
//            config.enableCorsForOrigin();
            // check if brotli available
            if (Brotli4jLoader.isAvailable()) {
                System.out.println("Using brotli");
                config.compression.brotliAndGzip();
            } else {
                System.out.println("Using gzip");
                config.compression.gzipOnly();
            }
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
        }).start(port);



        System.out.println("Started on port " + port);

        this.pageHandler = new PageHandler(this);

        this.app.get("/test", new Handler() {
            @Override
            public void handle(@NotNull Context context) throws Exception {
                System.out.println("Test");
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
            System.out.println("Index: Hello World");
            pageHandler.handle(ctx);
        });

//        get("/favicon.ico", ctx -> null);
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

    private JSONObject siteMap;

    public JSONObject generateSiteMap() {
        if (siteMap != null) return siteMap;
        siteMap = new JSONObject();
        // Pages
        siteMap.put("pages", pageHandler.getCommands().toCommandMap());
        // Commands
        siteMap.put("command", Locutus.cmd().getV2().getCommands().toCommandMap());
        // Wiki
        JSONObject wiki = new JSONObject();
        wiki.put("home", "");
        WikiGenHandler gen = new WikiGenHandler("", Locutus.cmd().getV2());
        for (BotWikiGen page : gen.getIntroPages()) {
            wiki.put("intro", page.getPageName());
        }
        for (BotWikiGen page : gen.getTopicPages()) {
            wiki.put("topic", page.getPageName());
        }
        for (BotWikiGen page : gen.getCommandPages()) {
            wiki.put("command", page.getPageName());
        }
        siteMap.put("wiki", wiki);
        return siteMap;
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

//    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException, LoginException {
////        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
////        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
////        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
////
////        Locutus locutus = Locutus.create().start();
////
////        System.out.println("Port " + Settings.INSTANCE.WEB.PORT_HTTP + " | " + Settings.INSTANCE.WEB.PORT_HTTPS);
////        WebRoot webRoot = new WebRoot(Settings.INSTANCE.WEB.PORT_HTTP, Settings.INSTANCE.WEB.PORT_HTTPS);
//
//        SSLPlugin plugin = new SSLPlugin(conf -> {
//            if (Settings.INSTANCE.WEB.PRIVKEY_PASSWORD.isEmpty()) {
//                conf.pemFromPath(Settings.INSTANCE.WEB.CERT_PATH, Settings.INSTANCE.WEB.PRIVKEY_PATH);
//            } else {
//                conf.pemFromPath(Settings.INSTANCE.WEB.CERT_PATH, Settings.INSTANCE.WEB.PRIVKEY_PATH, Settings.INSTANCE.WEB.PRIVKEY_PASSWORD);
//            }
//            conf.redirect = true;
//            // set ports
//            conf.securePort = Settings.INSTANCE.WEB.PORT;
//        });
//        JavalinJte.init(createTemplateEngine());
//        Javalin javalin = Javalin.create(config -> {
////            if (Settings.INSTANCE.WEB.PORT_HTTPS > 0) {
////                config.plugins.register(plugin);
////            }
//            config.plugins.enableDevLogging();
////            config.enableCorsForOrigin();
//            if (Brotli4jLoader.isAvailable()) {
//                System.out.println("Using brotli");
//                config.compression.brotliAndGzip();
//            } else {
//                System.out.println("Using gzip");
//                config.compression.gzipOnly();
//            }
//        }).start(Settings.INSTANCE.WEB.PORT);
//
//        // print hello world
//        javalin.get("/", ctx -> {
//            System.out.println("Hello World");
//            ctx.result("Hello World");
//        });
//
//        javalin.start();
//    }
}

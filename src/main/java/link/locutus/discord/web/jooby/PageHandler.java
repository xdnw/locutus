package link.locutus.discord.web.jooby;

import com.google.common.hash.Hashing;
import gg.jte.generated.precompiled.JtealertGenerated;
import gg.jte.generated.precompiled.JteerrorGenerated;
import io.javalin.http.Header;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoForm;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.GPTBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.*;
import link.locutus.discord.web.commands.alliance.AlliancePages;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.page.BankPages;
import link.locutus.discord.web.commands.page.EconPages;
import link.locutus.discord.web.commands.page.EndpointPages;
import link.locutus.discord.web.commands.page.GrantPages;
import link.locutus.discord.web.commands.page.IAPages;
import link.locutus.discord.web.commands.page.IndexPages;
import link.locutus.discord.web.commands.page.NationListPages;
import link.locutus.discord.web.commands.binding.DiscordWebBindings;
import link.locutus.discord.web.commands.binding.JavalinBindings;
import link.locutus.discord.web.commands.binding.PrimitiveWebBindings;
import link.locutus.discord.web.commands.binding.WebPWBindings;
import link.locutus.discord.web.commands.page.StatPages;
import link.locutus.discord.web.commands.page.TestPages;
import link.locutus.discord.web.commands.page.TradePages;
import link.locutus.discord.web.commands.page.WarPages;
import link.locutus.discord.web.jooby.handler.SseClient2;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PageHandler implements Handler {
    private Logger logger = Logger.getLogger(PageHandler.class.getSimpleName());
    private final WebRoot root;

    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public PageHandler(WebRoot root) {
        this.root = root;

        this.store = new SimpleValueStore<>();

        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new PWBindings().register(store);
        new GPTBindings().register(store);
        new SheetBindings().register(store);
//        new StockBinding().register(store);

        new DiscordWebBindings().register(store);
        new JavalinBindings().register(store);
        new PrimitiveWebBindings().register(store);
        new WebPWBindings().register(store);

        new AuthBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.commands = CommandGroup.createRoot(store, validators);

        this.commands.registerSubCommands(new IndexPages(), "page");
        this.commands.registerSubCommands(new IAPages(), "page");
        this.commands.registerSubCommands(new EconPages(), "page");
        this.commands.registerSubCommands(new StatPages(), "page");
        this.commands.registerSubCommands(new WarPages(), "page");
        this.commands.registerSubCommands(new GrantPages(), "page");
        this.commands.registerSubCommands(new BankPages(), "page");
        this.commands.registerSubCommands(new TradePages(), "page");
        this.commands.registerSubCommands(new AlliancePages(), "page");
        this.commands.registerSubCommands(new NationListPages(), "page");

        this.commands.registerSubCommands(new EndpointPages(), "rest");

        this.commands.registerCommands(new TestPages());
        this.commands.registerCommands(this);

        Map<Key, Parser> parsers = Locutus.imp().getCommandManager().getV2().getStore().getParsers();

        List<Key> missingKeys = new ArrayList<>();

        for (Map.Entry<Key, Parser> entry : parsers.entrySet()) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(Locutus.cmd().getV2().getStore())) continue;

            Key htmlKey = parser.getKey().append(HtmlInput.class);
            try {
                Parser htmlParser = store.get(htmlKey);
                if (htmlParser == null) {
                    missingKeys.add(htmlKey);
                }
            } catch (Exception e) {
                missingKeys.add(htmlKey);
            }
        }
        // print missing
        if (!missingKeys.isEmpty()) {
            for (Key missingKey : missingKeys) {
                System.out.println("Missing: " + missingKey);
            }
        }

    }

    public CommandGroup getCommands() {
        return commands;
    }

    public ValueStore<Object> getStore() {
        return store;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public boolean canView(User user) {
        if (Roles.MEMBER.hasOnRoot(user)) return true;
        return false;
    }

    private void sseMessage(SseClient2 sse, String message, boolean success) {
        JsonObject resultObj = new JsonObject();
        resultObj.addProperty("content", message);
        resultObj.addProperty("success", success);
        sse.sendEvent(resultObj);
    }

    /**
     * From argument list
     * @param sse
     * @throws InterruptedException
     * @throws IOException
     */
    public void sse(SseClient2 sse) {
        try {

            Context ctx = sse.ctx;
            WebIO io = new WebIO(sse);

            Map<String, List<String>> queryMap = sse.ctx.queryParamMap();
            List<String> cmds = queryMap.getOrDefault("cmd", Collections.emptyList());
            if (cmds.isEmpty()) {
                ArgumentStack stack = createStack(ctx);
                String path = stack.consumeNext();
                if (!path.equalsIgnoreCase("command")) {
                    sseMessage(sse, "Invalid path (not command): " + path, false);
                    return;
                }

                List<String> args = stack.getRemainingArgs();
                CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
                Map.Entry<CommandCallable, String> cmdAndPath = manager.getCallableAndPath(args);
                CommandCallable cmd = cmdAndPath.getKey();

                try {
                    cmd.validatePermissions(stack.getStore(), stack.getPermissionHandler());
                } catch (Throwable e) {
                    e.printStackTrace();
                    sseMessage(sse, "No permission: " + e.getMessage(), false);
                    return;
                }

                if (cmd instanceof ParametricCallable) {
                    LocalValueStore locals = stack.getStore();
                    Map<String, String> fullCmdStr = parseQueryMap(ctx.queryParamMap());
                    locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr));
                    locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
                    setupLocals(locals, ctx, null);

                    ParametricCallable parametric = (ParametricCallable) cmd;

                    Object[] parsed = parametric.parseArgumentMap(fullCmdStr, stack);
                    Object result = parametric.call(null, stack.getStore(), parsed);
                    if (result != null) {
                        String formatted = (result + "").trim(); // MarkupUtil.markdownToHTML
                        if (!formatted.isEmpty()) {
                            sseMessage(sse, formatted, true);
                        }
                    }
                } else {
                    sseMessage(sse, "Invalid command: " + StringMan.getString(args), true);
                }

            } else if (cmds.size() == 1){
                CommandManager2 v2 = Locutus.imp().getCommandManager().getV2();
                LocalValueStore locals = setupLocals(null, ctx, null);
                String cmdStr = cmds.get(0);
                v2.run(locals, io, cmdStr, false, true);
            } else {
                sseMessage(sse, "Too many commands: " + StringMan.getString(cmds), false);
            }
        } catch (Throwable e) {
            sseMessage(sse, "Error: " + e.getMessage(), false);
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            try {
                Thread.sleep(2000);
                sse.ctx.res().getOutputStream().close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

//    /**
//     * From argument list
//     * @param sse
//     * @throws InterruptedException
//     * @throws IOException
//     */
//    public void sseReaction(SseClient2 sse) {
//        Map<String, List<String>> queryMap = sse.ctx.queryParamMap();
//        List<String> emojiList = queryMap.getOrDefault("emoji", Collections.emptyList());
//        List<String> msgJsonList = queryMap.getOrDefault("msg", Collections.emptyList());
//        if (emojiList.size() != 1 || msgJsonList.size() != 1) {
//            return;
//        }
//
//        try {
//            Context ctx = sse.ctx;
//            {
//                String path = ctx.path();
//                path = path.substring(1);
//                List<String> args = new ArrayList<>(Arrays.asList(path.split("/")));
//                if (args.isEmpty()) {
//                    return;
//                }
//                DataObject json = DataObject.fromJson(msgJsonList.get(0));
//
//                long messageId = json.getLong("id");
//
//                List<MessageEmbed> embeds = new ArrayList<>();
//
//                DataObject embedJson = json.optObject("embed").orElse(null);
//                if (embedJson != null) {
//                    embedJson.put("type", "rich");
//                    embeds.add(entBuilder.createMessageEmbed(embedJson));
//                }
//                DataArray embedsData = json.optArray("embeds").orElse(null);
//                if (embedsData != null) {
//                    for (int i = 0; i < embedsData.length(); i++) {
//                        embedJson = embedsData.getObject(i);
//                        embedJson.put("type", "rich");
//                        embeds.add(entBuilder.createMessageEmbed(embedJson));
//                    }
//                }
//                if (embeds.size() > 0) {
//                    msgBuilder.setEmbeds(embeds);
//                }
//
//                JoobyChannel channel = new JoobyChannel(null, sse, root.getFileRoot());
//                JoobyMessageAction action = new JoobyMessageAction(jda, channel, root.getFileRoot(), sse);
//                action.setId(messageId);
//
//                Message embedMessage = msgBuilder.build();
//                JoobyMessage sseMessage = new JoobyMessage(action, embedMessage, messageId);
//                action.load(sseMessage);
//
//                DataArray reactionsData = json.optArray("reactions").orElse(null);
//                if (reactionsData != null) {
//                    for (int i = 0; i < reactionsData.length(); i++) {
//                        String unicode = reactionsData.getString(i);
//                        sseMessage.addReaction(unicode);
//                    }
//                }
//
//                String emojiUnparsed = emojiList.get(0);
//                String emoji = StringEscapeUtils.unescapeHtml4(emojiUnparsed);
//                MessageReaction.ReactionEmote emote = MessageReaction.ReactionEmote.fromUnicode(emoji, jda);
//
//
//                Locutus.imp().onMessageReact(sseMessage, null, emote, 0, false); // TODO make onMessageReact  accept nation
//            }
//        } catch (Throwable e) {
//            logger.log(Level.SEVERE, e.getMessage(), e);
//        } finally {
//            try {
//                Thread.sleep(2000);
//                sse.ctx.res.getOutputStream().close();
//            } catch (IOException | InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    public static Map<String, String> parseQueryMap( Map<String, List<String>> queryMap) {
        Map<String, List<String>> post = new HashMap<>(queryMap);
        post.entrySet().removeIf(f -> f.getValue().isEmpty() || (f.getValue().size() == 1 && f.getValue().get(0).isEmpty()));

        Set<String> toJson = new HashSet<>();
        post.entrySet().removeIf(f -> f.getValue().isEmpty() || (f.getValue().size() == 1 && f.getValue().get(0).isEmpty()));
        Map<String, String> combined = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : post.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                if (key.contains(".")) {
                    String[] split = key.split("\\.", 2);
                    key = split[0];
                    value = split[1] + ":" + value;
                    toJson.add(key);
                }
                String existing = combined.get(key);
                if (existing != null) {
                    combined.put(key, existing + "," + value);
                } else {
                    combined.put(key, value);
                }
            }
        }
        for (String key : toJson) {
            combined.put(key, "{" + combined.get(key) + "}");
        }
        return combined;
    }

//    @Command()
//    public Object command(@Me GuildDB db, ArgumentStack stack, Context ctx) {
//        List<String> args = stack.getRemainingArgs();
//        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
//        CommandCallable cmd = manager.getCallable(args);
//
//        return cmd.toHtml(stack.getStore(), stack.getPermissionHandler());
//    }

    @NotNull
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        logger.info("Page method " + ctx.method());
        try {
            handleCommand(ctx);
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private ArgumentStack createStack(Context ctx) {
        String content = ctx.path();
        if (content.isEmpty() || content.equals("/")) content = "/page/index";
        content = content.substring(1);
        List<String> args = new ArrayList<>(Arrays.asList(content.split("/")));
        for (int i = 0; i < args.size(); i++) {
            args.set(i, URLDecoder.decode(args.get(i)));
        }
        return createStack(ctx, args);
    }

    private ArgumentStack createStack(Context ctx, List<String> args) {
        LocalValueStore locals = setupLocals(null, ctx, args);

        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
        locals.addProvider(stack);
        return stack;
    }

    private void handleCommand(Context ctx) {
        try {
            ArgumentStack stack = createStack(ctx);
            ctx.header("Content-Type", "text/html;charset=UTF-8");
            String path = stack.getCurrent();
            switch (path.toLowerCase(Locale.ROOT)) {
                case "command" -> {
                    stack.consumeNext();
                    List<String> args = new ArrayList<>(stack.getRemainingArgs());
                    CommandManager2 manager = Locutus.cmd().getV2();

                    CommandCallable cmd = manager.getCommands().getCallable(args);
                    if (cmd == null) {
                        throw new IllegalArgumentException("No command found for `/" + StringMan.join(args, " ") + "`");
                    }

                    cmd.validatePermissions(stack.getStore(), permisser);
                    String endpoint = Settings.INSTANCE.WEB.REDIRECT + "/command";
                    ctx.result(WebUtil.minify(cmd.toHtml(stack.getStore().getProvided(WebStore.class), stack.getPermissionHandler(), endpoint, true)));
                    break;
                }
                // case "page" ->
                default -> {
                    List<String> args = new ArrayList<>(stack.getRemainingArgs());
                    CommandCallable cmd = commands.getCallable(args, true);
                    if (cmd == null) {
                        throw new IllegalArgumentException("No page found for `/" + StringMan.join(args, " ") + "`");
                    }
                    boolean run = !ctx.queryParamMap().isEmpty() || !args.isEmpty() || (cmd instanceof ParametricCallable param && (param.getUserParameters().isEmpty() || param.getAnnotation(NoForm.class) != null));
                    Object result;
                    if (cmd instanceof ParametricCallable parametric && run) {
                        Map<String, String> queryMap;
                        if (!args.isEmpty()) {
                            queryMap = parametric.formatArgumentsToMap(stack.getStore(), args);
                        } else {
                            queryMap = parseQueryMap(ctx.queryParamMap());
                            queryMap.remove("code");
                        }
                        Object[] parsed = parametric.parseArgumentMap(queryMap, stack.getStore(), validators, permisser);
                        Object cmdResult = parametric.call(parametric.getObject(), stack.getStore(), parsed);
                        result = wrap(stack.getStore().getProvided(WebStore.class), cmdResult, ctx);
                    } else {
                        result = cmd.toHtml(stack.getStore().getProvided(WebStore.class), stack.getPermissionHandler(), false);
                    }
                    if (result != null && (!(result instanceof String) || !result.toString().isEmpty())) {
                        ctx.result(WebUtil.minify(result.toString()));
                    } else if (result != null) {
                        throw new IllegalArgumentException("Illegal result: " + result + " for " + path);
                    } else {
                        throw new IllegalArgumentException("Null result for : " + path);
                    }
                }
            }
        } catch (Throwable e) {
            handleErrors(e, ctx);
        }
    }

    private void handleErrors(Throwable e, Context ctx) {
        while (e.getCause() != null) {
            Throwable tmp = e.getCause();
            if (tmp == e) break;
            e = tmp;
        }
        if (e instanceof RedirectResponse redirectResponse) {
            String msg = redirectResponse.getMessage();
            if (msg.startsWith("<")) {
                ctx.header("Content-Type", "text/html");
                ctx.header(Header.CACHE_CONTROL, "no-cache");
                ctx.result(WebUtil.minify(msg));
                return;
            }
            e.printStackTrace();
            System.out.println("Redirect " + redirectResponse.getMessage());
            ctx.redirect(redirectResponse.getMessage());
            ctx.result("Redirecting to " + MarkupUtil.htmlUrl(msg, msg) + ". If you are not redirected, click the link.");
            ctx.header(Header.CACHE_CONTROL, "no-cache");
            return;
        }

        e.printStackTrace();

        Map.Entry<String, String> entry = StringMan.stacktraceToString(e);

        ctx.result(WebUtil.minify(WebStore.render(f -> JteerrorGenerated.render(f, null, new WebStore(null, ctx), entry.getKey(), entry.getValue()))));
    }

    private Object wrap(WebStore ws, Object call, Context ctx) {
        String contentType = ctx.header("Content-Type");
        if (contentType == null || contentType.contains("text/html")) {
            if (call instanceof String) {
                String str = (String) call;
                str = str.trim();
                if (str.isEmpty()) return str;
                char char0 = str.charAt(0);
                char charlast = str.charAt(str.length() - 1);
                if ((char0 == '<' && charlast == '>')) {
                    if (contentType == null) {
                        ctx.header("Content-Type", "text/html");
                    }
                    return str;
                }
                if ((char0 == '[' && charlast == ']') || (char0 == '{' && charlast == '}')) {
                    ctx.header("Content-Type", "application/json");
                    return str;
                }
                String finalStr = str;
                return WebStore.render(f -> JtealertGenerated.render(f, null, ws, "Response", finalStr));
            }
        }
        return call;
    }

    private LocalValueStore setupLocals(LocalValueStore<Object> locals, Context ctx, List<String> args) {
        WebStore ws;
        if (locals == null) {
            locals = new LocalValueStore<>(store);
            ws = new WebStore(locals, ctx);
            locals.addProvider(Key.of(WebStore.class), ws);
        } else {
            ws = (WebStore) locals.getProvided(Key.of(WebStore.class));
        }
        AuthBindings.Auth auth = AuthBindings.getAuth(ws, ctx);
        if (auth != null) {
            locals.addProvider(Key.of(AuthBindings.Auth.class, Me.class), auth);
            User user = auth.getUser(true);
            DBNation nation = auth.getNation(true);
            if (user != null) {
                locals.addProvider(Key.of(User.class, Me.class), user);
            }
            if (nation != null) {
                locals.addProvider(Key.of(DBNation.class, Me.class), nation);
            }
        }

        locals.addProvider(Key.of(Context.class), ctx);
        Map<String, String> path = ctx.pathParamMap();
        if (path.containsKey("guild_id") && args != null && !args.isEmpty()) {
            Long guildId = Long.parseLong(path.get("guild_id"));
            args.remove(guildId + "");
            GuildDB guildDb = Locutus.imp().getGuildDB(guildId);
            if (guildDb == null) {
                throw new IllegalArgumentException("No guild found for id: " + guildId);
            }
            Guild guild = guildDb.getGuild();

            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            locals.addProvider(Key.of(GuildDB.class, Me.class), guildDb);
        }
        return locals;
    }

    public void sseCmdPage(SseClient2 sse) throws IOException {
        try {
            Context ctx = sse.ctx;
            String pathStr = ctx.path();
            if (pathStr.startsWith("/")) pathStr = pathStr.substring(1);
            List<String> path = new ArrayList<>(Arrays.asList(pathStr.split("/")));
            path.remove("command");
            LocalValueStore locals = setupLocals(null, ctx, path);
            CommandCallable cmd = commands.getCallable(path);
            if (cmd == null) {
                sseMessage(sse, "Command not found: " + path, false);
                return;
            }
            if (!(cmd instanceof ParametricCallable)) {
                sseMessage(sse, "Not a valid executable command", false);
                return;
            }

            try {
                cmd.validatePermissions(locals, permisser);
            } catch (Throwable e) {
                sseMessage(sse, "No permission (2): " + e.getMessage(), false);
                return;
            }

            String redirectBase = Settings.INSTANCE.WEB.REDIRECT + "/command/" + cmd.getFullPath("/").toLowerCase() + "/";

            Map<String, String> combined = parseQueryMap(ctx.queryParamMap());
            ParametricCallable parametric = (ParametricCallable) cmd;
            List<String> orderedArgs = parametric.orderArgumentMap(combined, false);

            String redirect = redirectBase + StringMan.join(orderedArgs, "/");

            JsonObject response = new JsonObject();
            response.addProperty("action", "redirect");
            response.addProperty("value", redirect);
            sse.sendEvent(response);
        } catch (Throwable e) {
            System.out.println("Handle errors");
            handleErrors(e, sse.ctx);
        }
    }

    public void logout(Context context) {
        for (CookieType type : CookieType.values()) {
            context.removeCookie(type.getCookieId());
        }
        context.redirect(WebRoot.REDIRECT);
    }

    public enum CookieType {
        DISCORD_OAUTH,
        URL_AUTH,
        GUILD_ID,

        ;

        private String cookieId;

        // set cookie id
        CookieType() {
            cookieId = getCookieId(this);
        }

        public String getCookieId() {
            return cookieId;
        }

        private String getCookieId(CookieType type) {
            StringBuilder key = new StringBuilder(COOKIE_ID);
            key.append(Settings.INSTANCE.BOT_TOKEN.hashCode());
            if (type != CookieType.DISCORD_OAUTH) {
                key.append(type.name());
            }

            return Hashing.sha256()
                    .hashString(key.toString(), StandardCharsets.UTF_8)
                    .toString();
        }

    }

    public static String COOKIE_ID = "LCTS";


}

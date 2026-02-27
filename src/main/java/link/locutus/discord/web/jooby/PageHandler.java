package link.locutus.discord.web.jooby;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import gg.jte.generated.precompiled.JtealertGenerated;
import gg.jte.generated.precompiled.JteerrorGenerated;
import io.javalin.http.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.DenyPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.GPTBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import link.locutus.discord.web.commands.WebIO;
import link.locutus.discord.web.commands.alliance.AlliancePages;
import link.locutus.discord.web.commands.api.*;
import link.locutus.discord.web.commands.binding.*;
import link.locutus.discord.web.commands.options.WebOptionBindings;
import link.locutus.discord.web.commands.page.*;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import link.locutus.discord.web.jooby.handler.SseMessageOutput;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PageHandler implements Handler {
    private final Logger logger = Logger.getLogger(PageHandler.class.getSimpleName());

    private final CommandGroup commands;
    private final ValueStore<Object> store;
   private final ValueStore<Object> webOptionStore; 
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final ObjectMapper serializer;
    private final PlaceholdersMap placeholders;

    public PageHandler(PlaceholdersMap placeholders) {
        this.placeholders = placeholders;
        this.store = PWBindings.createDefaultStore();
        for (Class<?> type : placeholders.getTypes()) {
            Placeholders<?, ?> ph = placeholders.get(type);
            ph.register(store);
        }

        new JavalinBindings().register(store);
        new AuthBindings().register(store);
        new DiscordWebBindings(placeholders).register(store);
        new WebPWBindings().register(store);
        new PrimitiveWebBindings().register(store);

        this.validators = PWBindings.createDefaultValidators();
        this.permisser = PWBindings.createDefaultPermisser();

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

        // endpoints
        this.commands.registerSubCommands(new EndpointPages(), "api");
        this.commands.registerSubCommands(new TradeEndpoints(), "api");
        this.commands.registerSubCommands(new IAEndpoints(), "api");
        this.commands.registerSubCommands(new AdminEndpoints(), "api");
        this.commands.registerSubCommands(new StatEndpoints(), "api");
        this.commands.registerSubCommands(new ConflictEndpoints(), "api");
        this.commands.registerSubCommands(new CoalitionGraphEndpoints(), "api");
        this.commands.registerSubCommands(new GraphEndpoints(), "api");
        this.commands.registerSubCommands(new TaxEndpoints(), "api");
        this.commands.registerSubCommands(new MultiEndpoints(), "api");
        this.commands.registerSubCommands(new TreatyEndpoints(), "api");

        this.commands.registerCommands(new TestPages());
        this.commands.registerCommands(this);

        this.serializer = new ObjectMapper(new MessagePackFactory());

        this.webOptionStore = new SimpleValueStore<>();
        new WebOptionBindings().register(webOptionStore);
        
        store.addProvider(Key.of(PlaceholdersMap.class), placeholders);
    }

    public MCPHandler createMCP() {
        return new MCPHandler(
                store,
                webOptionStore,
                validators,
                permisser,
                (locals, ctx) -> locals == null ? createLocals(ctx) : setupLocals(locals, ctx),
                true,
                null
        );
    }

    public ObjectMapper getSerializer() {
        return serializer;
    }

    private volatile Map<String, WebOption> queryOptions;
    private final Object queryOptionsLock = new Object();

    private Map<String, WebOption> getQueryOptions() {
        Map<String, WebOption> tmp = queryOptions;
        if (tmp == null) {
            synchronized (queryOptionsLock) {
                tmp = queryOptions;
                if (tmp == null) {
                    tmp = new ConcurrentHashMap<>();

                    for (Parser optionParser : webOptionStore.getParsers().values()) {
                        try {
                            Object option = optionParser.apply(store, null);
                            if (!(option instanceof WebOption)) {
                                continue;
                            }
                            WebOption optionCasted = (WebOption) option;
                            if (optionCasted.isAllowQuery()) {
                                tmp.put(optionCasted.getName(), optionCasted);
                            }
                        } catch (Throwable e) {
                            Logg.error("Error: " + optionParser);
                            e.printStackTrace();
                        }
                    }

                    queryOptions = tmp;
                }
            }
        }
        return tmp;
    }

    public WebOption getQueryOption(String name) {
        return getQueryOptions().get(name);
    }

    public Set<String> getQueryOptionNames() {
        return getQueryOptions().keySet();
    }

    public CommandGroup getCommands() {
        return commands;
    }

    public ValueStore<Object> getStore() {
        return store;
    }

    public ValueStore<Object> getWebOptionStore() {
        return webOptionStore;
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

    /**
     * From argument list
     * @param sse
     * @throws InterruptedException
     * @throws IOException
     */
    public void sse(SseMessageOutput sse) {
        try {
            Context ctx = sse.ctx();
            Map<String, List<String>> queryMap = new Object2ObjectOpenHashMap<>(sse.ctx().queryParamMap());
            for (Map.Entry<String, List<String>> entry : sse.ctx().formParamMap().entrySet()) {
                queryMap.put(entry.getKey(), entry.getValue());
            }

            List<String> cmds = queryMap.getOrDefault("cmd", Collections.emptyList());
            WebIO io = new WebIO(sse, AuthBindings.guild(ctx, null, null, false));

            Logg.text("SSE Command: cmds=" + StringMan.getString(cmds) + " | queryParams=" + queryMap.entrySet().stream().map(f -> f.getKey() + "=" + StringMan.getString(f.getValue())).collect(Collectors.joining(", ")));
            if (cmds.isEmpty()) {
                List<String> inputArgs = StringMan.split(URLDecoder.decode(ctx.path().substring(1).replace("/", " ")), " ");
                ArgumentStack stack = createStack(ctx, inputArgs);
                String path = stack.consumeNext();
                if (!path.equalsIgnoreCase("sse")) {
                    Logg.text("Invalid path (not sse): " + path);
                    sse.sseMessage("Invalid path (not command): " + path, false);
                    return;
                }

                List<String> args = stack.getRemainingArgs();
                CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
                Map.Entry<CommandCallable, String> cmdAndPath = manager.getCommands().getCallableAndPath(args);
                CommandCallable cmd = cmdAndPath.getKey();

                try {
                    cmd.validatePermissions(stack.getStore(), stack.getPermissionHandler());
                } catch (Throwable e) {
                    e.printStackTrace();
                    Logg.text("No permission: " + e.getMessage());
                    sse.sseMessage("No permission: " + e.getMessage(), false);
                    return;
                }

                if (cmd instanceof ParametricCallable) {
                    LocalValueStore<?> locals = stack.getStore();
                    Map<String, Object> fullCmdStr = parseQueryMap(queryMap, null);
                    locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr).put("", cmd.getFullPath()));
                    locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
                    setupLocals(locals, ctx);

                    ParametricCallable parametric = (ParametricCallable) cmd;
                    Logg.text("Web SSE: " + path + "/" + StringMan.join(args, " ") + " | [" + logInfo(locals) + "] " + fullCmdStr);

                    Object[] parsed = parametric.parseArgumentMap2(fullCmdStr, locals, validators, permisser, false);
                    Object result = parametric.call(null, locals, parsed);
                    if (result != null) {
                        String formatted = MarkupUtil.formatDiscordMarkdown((result + "").trim(), io.getGuildOrNull());
                        if (!formatted.isEmpty()) {
                            Logg.text("SSE Result: " + formatted + " | " + result);
                            sse.sseMessage(formatted, true);
                        } else {
                            Logg.text("SSE Result: empty | `" + result + "`");
                        }
                    } else {
                        Logg.text("SSE Result is null");
                    }
                } else {
                    Logg.text("Invalid command: " + StringMan.getString(args));
                    sse.sseMessage("Invalid command: " + StringMan.getString(args), true);
                }

            } else if (cmds.size() == 1){
                CommandManager2 v2 = Locutus.imp().getCommandManager().getV2();
                LocalValueStore locals = createLocals(ctx);
                String cmdStr = cmds.get(0);
                Logg.text("Web SSE(legacy): " + cmdStr + " | [" + logInfo(locals) + "]");
                v2.run(locals, io, cmdStr, false, true);
            } else {
                sse.sseMessage( "Too many commands: " + StringMan.getString(cmds), false);
            }
        } catch (Throwable e) {
            Logg.error("Throwable in SSE: " + e.getMessage());
            e.printStackTrace();
            sse.sseMessage( "Error: " + e.getMessage(), false);
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            Logg.text("SSE connection closing.");
            sse.close();
        }
    }

    private static String logInfo(LocalValueStore valueStore) {
        Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class), false);
        DBNation nation = (DBNation) valueStore.getProvided(Key.of(DBNation.class, Me.class), false);
        User user = (User) valueStore.getProvided(Key.of(User.class, Me.class), false);
        List<String> parts = new ObjectArrayList<>();
        if (guild != null) {
            parts.add(guild.toString());
        }
        if (nation != null) {
            parts.add(nation.getName() + "(" + nation.getNation_id() + ")");
        }
        if (user != null) {
            parts.add(user.getName() + "(" + user.getId() + ")");
        }
        if (parts.isEmpty()) {
            parts.add("unknown");
        }
        return String.join(", ", parts);
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

    public static Map<String, Object> parseQueryMap( Map<String, List<String>> queryMap, Set<String> allowList) {
        Map<String, List<String>> post = new HashMap<>(queryMap);
        post.entrySet().removeIf(f -> f.getValue().isEmpty() || (f.getValue().size() == 1 && f.getValue().get(0).isEmpty()));

        Set<String> toJson = new HashSet<>();

        Map<String, Object> combined = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : post.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (allowList != null && allowList.contains(key)) {
                combined.put(key, values);
                continue;
            }
            for (String value : values) {
                if (key.contains(".")) {
                    String[] split = key.split("\\.", 2);
                    key = split[0];
                    value = split[1] + ":" + value;
                    toJson.add(key);
                }
                Object existing = combined.get(key);
                if (existing instanceof String) {
                    combined.put(key, existing + "," + value);
                } else if (values.size() == 1) {
                    combined.put(key, value);
                }
            }
        }
        for (String key : toJson) {
            combined.put(key, "{" + combined.get(key) + "}");
        }
        return combined;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        try {
            handleCommand(ctx);
        } catch (Throwable e) {
            e.printStackTrace();
            String errorMessage = "<html><body><h1>Error</h1><pre>" + StringMan.stripApiKey(e.getMessage()) + "</pre></body></html>";
            ctx.status(500).html(errorMessage);
        }
    }

    private ArgumentStack createStack(Context ctx) {
        String content = ctx.path();
        String separator = "/";
        boolean decode = true;

        if (content.isEmpty() || content.equals("/")) content = "/page/index";
        content = content.substring(1);
        List<String> args = new ArrayList<>(Arrays.asList(content.split(separator)));
        for (int i = 0; i < args.size(); i++) {
            String decoded = decode ? URLDecoder.decode(args.get(i)) : args.get(i);
            args.set(i, decoded);
        }
        return createStack(ctx, args);
    }

    private ArgumentStack createStack(Context ctx, List<String> args) {
        LocalValueStore locals = createLocals(ctx);
        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
        locals.addProvider(stack);
        return stack;
    }

    private void handleCommand(Context ctx) {
        boolean isApi = false;
        WebStore ws = null;
        try {
            ArgumentStack stack = createStack(ctx);
            ws = stack.getStore().getProvided(WebStore.class);
            ctx.header("Content-Type", "text/html;charset=UTF-8");
            String path = stack.getCurrent();
            boolean isPost = ctx.method() == HandlerType.POST;
            List<String> args = new ArrayList<>(stack.getRemainingArgs());
            CommandManager2 manager = Locutus.cmd().getV2();

            String logMsg = "Web request: " + path + "/" + StringMan.join(args, " ") + " | [" + ws.getCachedAuth() + "]";
            Logg.text(logMsg);

            switch (path.toLowerCase(Locale.ROOT)) {
                case "command": {
                    stack.consumeNext();
                    CommandCallable cmd = manager.getCommands().getCallable(args);
                    if (cmd == null) {
                        throw new IllegalArgumentException("No command found for `/" + StringMan.join(args, " ") + "`");
                    }

                    String prefix = cmd instanceof ParametricCallable ? "sse" : "command";
                    String endpoint = WebRoot.REDIRECT + "/" + prefix + "/" + cmd.getFullPath("/");
                    if (!endpoint.endsWith("/")) endpoint += "/";
                    ctx.result(WebUtil.minify(cmd.toHtml(ws, stack.getPermissionHandler(), endpoint, true)));
                    break;
                }
                case "api":
                    isApi = true;
                default: {
                    CommandCallable cmd = commands.getCallable(args, true);
                    if (cmd == null) {
                        throw new IllegalArgumentException("No page found for `/" + StringMan.join(args, " ") + "`");
                    }
                    boolean run = isPost || (cmd instanceof ParametricCallable param && param.isViewable());
                    Object result;
                    if (cmd instanceof ParametricCallable<?> parametric && run) {
                        Map<String, Object> queryMap;
                        // where type = List<String>
                        Set<String> allowList = parametric.getUserParameters().stream().filter(f -> {
                            Type type = f.getType();
                            if (type instanceof ParameterizedType pType) {
                                Type rawType = pType.getRawType();
                                if (rawType instanceof Class<?> clazz) {
                                    if (clazz == List.class) {
                                        Type argType = pType.getActualTypeArguments()[0];
                                        if (argType instanceof Class<?> argClazz) {
                                            return argClazz == String.class;
                                        }
                                    }
                                }
                            }
                            return false;
                        }).map(ParameterData::getName).collect(Collectors.toSet());
                        if (!args.isEmpty()) {
                            queryMap = (Map) parametric.formatArgumentsToMap(stack.getStore(), args);
                        } else {
                            Map<String, List<String>> queryParams = ctx.queryParamMap();
                            if (queryParams.isEmpty()) {
                                queryMap = parseQueryMap(ctx.formParamMap(), allowList);
                            } else {
                                queryMap = parseQueryMap(queryParams, allowList);
                                queryMap.remove("code");
                            }
                        }
                        long start = System.currentTimeMillis();
                        Object[] parsed = parametric.parseArgumentMap2(queryMap, stack.getStore(), validators, permisser, true);
                        Object cmdResult = parametric.call(null, stack.getStore(), parsed);
                        result = wrap(ws, cmdResult, ctx, isApi);
                    } else if (!isApi) {
                        result = cmd.toHtml(ws, stack.getPermissionHandler(), false);
                    } else if (cmd instanceof ParametricCallable parametric) {
                        throw new IllegalArgumentException("API endpoint is not viewable: `" + path + "`. Only informational commands can be executed without user confirmation.");
                    } else {
                        throw new IllegalArgumentException("No subcommand specified for `" + path + "` | remaining: " + StringMan.join(args, " "));
                    }
                    if (result != null) {
                        if (result instanceof byte[] bytes) {
                            ctx.result(bytes);
                            ctx.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                            ctx.header("Accept", "application/msgpack");
                        } else if (!(result instanceof String) || !result.toString().isEmpty()) {
                            ctx.result(WebUtil.minify(result.toString()));
                        } else {
                            throw new IllegalArgumentException("Illegal result: " + result + " for " + path);
                        }
                    } else {
                        throw new IllegalArgumentException("Null result for : " + path);
                    }
                }
            }
        } catch (Throwable e) {
            Logg.error("Throwable " + e.getMessage());
            handleErrors(e, ws, ctx, isApi);
        }
    }

    private void handleErrors(Throwable e, WebStore ws, Context ctx, boolean isApi) {
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
            PageHelper.redirect(ws, ctx, redirectResponse.getMessage(), false);
            return;
        }
        if (isApi) {
            Map.Entry<String, String> errorMsg = StringMan.stacktraceToString(e, 10, f -> f.startsWith("link.locutus."));
            Map<String, Serializable> raw = Map.of("success", false,
                    "error", errorMsg.getKey() + "\n" + errorMsg.getValue());
            try {
                byte[] data = serializer.writeValueAsBytes(raw);
                ctx.result(data);
                ctx.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                ctx.header("Accept", "application/msgpack");
            } catch (JsonProcessingException ex) {
                ex.printStackTrace();
                ctx.result("Internal server error");
                throw new RuntimeException(ex);
            }
            return;
        }
        Map.Entry<String, String> entry = StringMan.stacktraceToString(e);
        ctx.result(WebUtil.minify(WebStore.render(f -> JteerrorGenerated.render(f, null, new WebStore(null, ctx), entry.getKey(), entry.getValue()))));
    }

    private Object wrap(WebStore ws, Object call, Context ctx, boolean isApi) {
        if (isApi) {
            if (call instanceof byte[] bytes) {
                return bytes;
            } else {
                try {
                    return serializer.writeValueAsBytes(call);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (call instanceof String) {
            String contentType = ctx.header("Content-Type");
            if (contentType == null || contentType.contains("text/html")) {
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

    private LocalValueStore setupLocals(LocalValueStore<?> locals, Context ctx) {
        WebStore ws;
        if (locals == null) {
            locals = new LocalValueStore<>(store);
            ws = new WebStore(locals, ctx);
            locals.addProvider(Key.of(WebStore.class), ws);
        } else {
            ws = (WebStore) locals.getProvided(Key.of(WebStore.class));
        }
        DBAuthRecord auth = AuthBindings.getAuth(ws, ctx);
        if (auth != null) {
            locals.addProvider(Key.of(DBAuthRecord.class, Me.class), auth);
            User user = auth.getUser(true);
            DBNation nation = auth.getNation(true);
            if (user != null) {
                locals.addProvider(Key.of(User.class, Me.class), user);
            }
            if (nation != null) {
                locals.addProvider(Key.of(DBNation.class, Me.class), nation);
            }

            Guild guild = AuthBindings.guild(ctx, nation, user, false);
            if (guild != null) {
                GuildDB guildDb = Locutus.imp().getGuildDB(guild);
                locals.addProvider(Key.of(Guild.class, Me.class), guild);
                locals.addProvider(Key.of(GuildDB.class, Me.class), guildDb);
            }
        }

        locals.addProvider(Key.of(Context.class), ctx);
//        Map<String, String> path = ctx.pathParamMap();
//
//        if (path.containsKey("guild_id") && args != null && !args.isEmpty()) {
//            Long guildId = Long.parseLong(path.get("guild_id"));
//            args.remove(guildId + "");
//            GuildDB guildDb = Locutus.imp().getGuildDB(guildId);
//            if (guildDb == null) {
//                throw new IllegalArgumentException("No guild found for id: " + guildId);
//            }
//            Guild guild = guildDb.getGuild();
//
//            locals.addProvider(Key.of(Guild.class, Me.class), guild);
//            locals.addProvider(Key.of(GuildDB.class, Me.class), guildDb);
//        }
        return locals;
    }

    public LocalValueStore<?> createLocals(Context ctx) {
        return setupLocals(null, ctx);
    }

    public void sseCmdPage(SseMessageOutput sse) throws IOException {
        WebStore ws = null;
        try {
            Context ctx = sse.ctx();
            String pathStr = ctx.path();
            if (pathStr.startsWith("/")) pathStr = pathStr.substring(1);
            List<String> path = new ArrayList<>(Arrays.asList(pathStr.split("/")));
            path.remove("command");
            LocalValueStore locals = createLocals(ctx);
            ws = (WebStore) locals.getProvided(WebStore.class);
            CommandCallable cmd = commands.getCallable(path);
            if (cmd == null) {
                sse.sseMessage("Command not found: " + path, false);
                return;
            }
            if (!(cmd instanceof ParametricCallable)) {
                sse.sseMessage("Not a valid executable command", false);
                return;
            }

            try {
                cmd.validatePermissions(locals, permisser);
            } catch (Throwable e) {
                sse.sseMessage("No permission (2): " + e.getMessage(), false);
                return;
            }

            String redirectBase = WebRoot.REDIRECT + "/command/" + cmd.getFullPath("/").toLowerCase() + "/";

            Map<String, Object> combined = parseQueryMap(ctx.queryParamMap(), null);
            ParametricCallable parametric = (ParametricCallable) cmd;
            List<String> orderedArgs = parametric.orderArgumentMap((Map) combined, false);

            String redirect = redirectBase + StringMan.join(orderedArgs, "/");

            Map<String, Object> data = Map.of("action", "redirect", "value", redirect);
            sse.sendEvent(data);
        } catch (Throwable e) {
            handleErrors(e, ws, sse.ctx(), false);
        }
    }

    private Object call(ParametricCallable cmd, WebStore ws, Context context, List<String> values) {
        ArgumentStack stack = new ArgumentStack(values, (LocalValueStore<?>) ws.store(), validators, permisser);
        Object[] parsed = cmd.parseArguments(stack);
        return cmd.call(null, ws.store(), parsed);
    }

    public Object call(ParametricCallable cmd, WebStore ws, Context context, Object value) {
        Map<String, Object> combined;
        if (value == null) combined = Collections.emptyMap();
        else if (value instanceof Map map) {
            combined = (Map) map;
        }
        else if (value instanceof String myString) {
            List<String> args = new ObjectArrayList<>(1);
            args.add(myString);
            return call(cmd, ws, context, args);
        } else if (value instanceof List myList) {
            return call(cmd, ws, context, new ObjectArrayList<>((List<String>) myList));
        } else {
            throw new IllegalArgumentException("Invalid value: " + value + " | " + value.getClass().getSimpleName());
        }
        Object[] parsed = cmd.parseArgumentMap2(combined, ws.store(), validators, permisser, true);
        return cmd.call(null, ws.store(), parsed);
    }

    public enum CookieType {
        DISCORD_OAUTH("lc_discord"),
        URL_AUTH("lc_token"),
        GUILD_ID("lc_guild"),
        REDIRECT("lc_redirect"),
        URL_AUTH_SET("lc_token_exists"),
        ;

        private final String cookieId;

        // set cookie id
        CookieType(String cookieId) {
            this.cookieId = cookieId;
        }

        public String getCookieId() {
            return cookieId;
        }
    }

    public static String COOKIE_ID = "LCTS";


}

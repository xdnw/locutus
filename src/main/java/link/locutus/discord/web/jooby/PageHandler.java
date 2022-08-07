package link.locutus.discord.web.jooby;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.StockBinding;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.commands.*;
import link.locutus.discord.web.commands.alliance.AlliancePages;
import link.locutus.discord.web.commands.binding.NationListPages;
import link.locutus.discord.web.commands.binding.StringWebBinding;
import link.locutus.discord.web.commands.binding.WebPrimitiveBinding;
import link.locutus.discord.web.jooby.adapter.JoobyChannel;
import link.locutus.discord.web.jooby.adapter.JoobyMessage;
import link.locutus.discord.web.jooby.adapter.JoobyMessageAction;
import link.locutus.discord.web.jooby.handler.SseClient2;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        new SheetBindings().register(store);
        new StockBinding().register(store);
        new JoobyBindings().register(store);
        new WebPrimitiveBinding().register(store);
        new StringWebBinding().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.commands = CommandGroup.createRoot(store, validators);

        this.commands.registerCommands(new IndexPages());
        this.commands.registerCommands(new IAPages());
        this.commands.registerCommands(new EconPages());
        this.commands.registerCommands(new StatPages());
        this.commands.registerCommands(new WarPages());
        this.commands.registerCommands(new GrantPages());
        this.commands.registerCommands(new BankPages());
        this.commands.registerCommands(new TradePages());
        this.commands.registerCommands(new AlliancePages());
        this.commands.registerCommands(new NationListPages());

        this.commands.registerCommands(new TestPages());


        this.commands.registerCommands(this);
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
    public void sseCmdStr(SseClient2 sse) {
        Map<String, List<String>> queryMap = sse.ctx.queryParamMap();
        List<String> cmds = queryMap.getOrDefault("cmd", Collections.emptyList());
        if (cmds.size() != 1) return;

        try {
            String cmdStr = cmds.get(0);
            if (cmdStr.isEmpty()) return;
            if (cmdStr.charAt(0) == '!') cmdStr = Settings.commandPrefix(true) + cmdStr.substring(1);
            if (cmdStr.charAt(0) == '$') cmdStr = Settings.commandPrefix(false) + cmdStr.substring(1);

            Context ctx = sse.ctx;
            JsonObject userJson = root.getDiscordUser(ctx);
            if (userJson == null) {
                sseMessage(sse, "Not registered", false);
                return;
            }
            Long userId = Long.parseLong(userJson.get("id").getAsString());
            User user = Locutus.imp().getDiscordApi().getUserById(userId);
            DBNation nation = DiscordUtil.getNation(userId);
            if (nation == null || user == null) {
                sseMessage(sse, "User not verfied", false);
                return;
            }

            String path = ctx.path();
            path = path.substring(1);
            List<String> args = new ArrayList<>(Arrays.asList(path.split("/")));

            if (args.isEmpty()) {
                return;
            }
            String guildIdStr = args.get(0);
            if (!MathMan.isInteger(guildIdStr)) {
                return;
            }

            GuildDB guildDb = Locutus.imp().getGuildDB(Long.parseLong(guildIdStr));
            if (guildDb == null) {
                return;
            }

            JoobyChannel channel = new JoobyChannel(guildDb.getGuild(), sse, root.getFileRoot());
            JoobyMessageAction action = new JoobyMessageAction(guildDb.getGuild().getJDA(), channel, root.getFileRoot(), sse);

            Message embedMessage = new MessageBuilder().setContent(cmdStr).build();
            JoobyMessage sseMessage = new JoobyMessage(action, embedMessage, -1);
            sseMessage.setUser(user);
            action.load(sseMessage);

            MessageReceivedEvent finalEvent = new DelegateMessageEvent(guildDb.getGuild(), -1, sseMessage);
            Locutus.imp().getCommandManager().run(finalEvent, false, true);

        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            try {
                Thread.sleep(2000);
                sse.ctx.res.getOutputStream().close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * From argument list
     * @param sse
     * @throws InterruptedException
     * @throws IOException
     */
    public void sseReaction(SseClient2 sse) {
        Map<String, List<String>> queryMap = sse.ctx.queryParamMap();
        List<String> emojiList = queryMap.getOrDefault("emoji", Collections.emptyList());
        List<String> msgJsonList = queryMap.getOrDefault("msg", Collections.emptyList());
        if (emojiList.size() != 1 || msgJsonList.size() != 1) {
            return;
        }

        try {
            Context ctx = sse.ctx;
            JsonObject userJson = root.getDiscordUser(ctx);
            if (userJson == null) {
                sseMessage(sse, "Not registered", false);
                return;
            }
            Long userId = Long.parseLong(userJson.get("id").getAsString());
            User user = Locutus.imp().getDiscordApi().getUserById(userId);
            DBNation nation = DiscordUtil.getNation(userId);
            if (nation == null || user == null) {
                sseMessage(sse, "User not verfied", false);
                return;
            }
            {
                String path = ctx.path();
                path = path.substring(1);
                List<String> args = new ArrayList<>(Arrays.asList(path.split("/")));

                if (args.isEmpty()) {
                    return;
                }
                String guildIdStr = args.get(0);
                if (!MathMan.isInteger(guildIdStr)) {
                    return;
                }

                GuildDB guildDb = Locutus.imp().getGuildDB(Long.parseLong(guildIdStr));
                if (guildDb == null) {
                    return;
                }

                JDA jda = guildDb.getGuild().getJDA();

                DataObject json = DataObject.fromJson(msgJsonList.get(0));
                MessageBuilder msgBuilder = new MessageBuilder();
                EntityBuilder entBuilder = new EntityBuilder(jda);

                long messageId = json.getLong("id");

                DataObject content = json.optObject("content").orElse(null);
                if (content != null) {
                    msgBuilder.setContent(content.toString());
                } else {
                    msgBuilder.setContent("");
                }

                List<MessageEmbed> embeds = new ArrayList<>();

                DataObject embedJson = json.optObject("embed").orElse(null);
                if (embedJson != null) {
                    embedJson.put("type", "rich");
                    embeds.add(entBuilder.createMessageEmbed(embedJson));
                }
                DataArray embedsData = json.optArray("embeds").orElse(null);
                if (embedsData != null) {
                    for (int i = 0; i < embedsData.length(); i++) {
                        embedJson = embedsData.getObject(i);
                        embedJson.put("type", "rich");
                        embeds.add(entBuilder.createMessageEmbed(embedJson));
                    }
                }
                if (embeds.size() > 0) {
                    msgBuilder.setEmbeds(embeds);
                }

                JoobyChannel channel = new JoobyChannel(guildDb.getGuild(), sse, root.getFileRoot());
                JoobyMessageAction action = new JoobyMessageAction(jda, channel, root.getFileRoot(), sse);
                action.setId(messageId);

                Message embedMessage = msgBuilder.build();
                JoobyMessage sseMessage = new JoobyMessage(action, embedMessage, messageId);
                action.load(sseMessage);

                DataArray reactionsData = json.optArray("reactions").orElse(null);
                if (reactionsData != null) {
                    for (int i = 0; i < reactionsData.length(); i++) {
                        String unicode = reactionsData.getString(i);
                        sseMessage.addReaction(unicode);
                    }
                }

                String emojiUnparsed = emojiList.get(0);
                String emoji = StringEscapeUtils.unescapeHtml4(emojiUnparsed);
                MessageReaction.ReactionEmote emote = MessageReaction.ReactionEmote.fromUnicode(emoji, jda);


                Locutus.imp().onMessageReact(sseMessage, user, emote, 0, false);
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            try {
                Thread.sleep(2000);
                sse.ctx.res.getOutputStream().close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Map.Entry<Long, DBNation> getUserIdNationPair(Context ctx) {
        try {
            JsonObject userJson = userJson = root.getDiscordUser(ctx);

            if (userJson == null) {
                throw new IllegalArgumentException("Not  registered");
            }
            Long id = Long.parseLong(userJson.get("id").getAsString());
            DBNation nation = DiscordUtil.getNation(id);
            if (nation == null) {
                throw new IllegalArgumentException("User not verified");
            }
            return new AbstractMap.SimpleEntry<>(id, nation);
        } catch (IOException e) {
            throw new IllegalArgumentException("IO error: " + e.getMessage());
        }
    }

    public Map<String, String> parseQueryMap(ValueStore locals, @Nullable SseClient2 client, Map<String, List<String>> queryMap) {
        Map<String, List<String>> post = new HashMap<>(queryMap);
        post.entrySet().removeIf(f -> f.getValue().isEmpty() || (f.getValue().size() == 1 && f.getValue().get(0).isEmpty()));

        Guild guild = (Guild) locals.getProvided(Key.of(Guild.class, Me.class));
        if (client != null) {
            JoobyChannel channel = new JoobyChannel(guild, client, root.getFileRoot());
            locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        }

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

    /**
     * From context argument MAP
     * @param sse
     * @throws InterruptedException
     * @throws IOException
     */
    public void sse(SseClient2 sse) {
        try {
            Context ctx = sse.ctx;
            JsonObject userJson = root.getDiscordUser(ctx);
            if (userJson == null) {
                sseMessage(sse, "Not registered", false);
                return;
            }
            Long id = Long.parseLong(userJson.get("id").getAsString());
            DBNation nation = DiscordUtil.getNation(id);
            if (nation == null) {
                sseMessage(sse, "User not verfied", false);
                return;
            }
            ArgumentStack stack = createStack(nation, id, ctx);
            stack.consumeNext();
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
                ValueStore locals = stack.getStore();
                Map<String, String> combined = parseQueryMap(locals, sse, ctx.queryParamMap());

                ParametricCallable parametric = (ParametricCallable) cmd;

                String cmdRaw = parametric.stringifyArgumentMap(combined, " ");
                Message embedMessage = new MessageBuilder().setContent(cmdRaw).build();
                locals.addProvider(Key.of(Message.class, Me.class), embedMessage);

                Object[] parsed = parametric.parseArgumentMap(combined, stack);
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
        } catch (Throwable e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            try {
                Thread.sleep(2000);
                sse.ctx.res.getOutputStream().close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Command()
    public Object command(@Me GuildDB db, @Me User user, ArgumentStack stack, Context ctx) {
        List<String> args = stack.getRemainingArgs();
        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
        CommandCallable cmd = manager.getCallable(args);

        return cmd.toHtml(stack.getStore(), stack.getPermissionHandler());
    }

    @NotNull
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        logger.info("Page method " + ctx.method());
        try {
            JsonObject userJson = root.getDiscordUser(ctx);
            if (userJson == null) {
                root.login(ctx);
                return;
            }
            Long id = Long.parseLong(userJson.get("id").getAsString());
            User user = Locutus.imp().getDiscordApi().getUserById(id);
            if (user == null) {
                String url = "https://discord.gg/H9XnGxc";
                ctx.result("Please join the Politics & War discord: " + MarkupUtil.htmlUrl(url, url));
                ctx.header("Content-Type", "text/html;charset=UTF-8");
                return;
            }
            DBNation nation = DiscordUtil.getNation(id);
            if (nation == null) {
                ctx.result("Please use <b>" + Settings.commandPrefix(true) + "verify</b> in " + MarkupUtil.htmlUrl("#bot-spam", "https://discord.com/channels/216800987002699787/400030171765276672/") + "\n" +
                        "You are currently signed in as " + user.getName() + "#" + user.getDiscriminator() + ": " + MarkupUtil.htmlUrl("Logout", WebRoot.REDIRECT + "/logout"));
                ctx.header("Content-Type", "text/html;charset=UTF-8");
                return;
            }

            handleCommand(nation, id, ctx);
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private ArgumentStack createStack(DBNation nation, Long id, Context ctx) {
        String content = ctx.path();
        if (content.isEmpty() || content.equals("/")) content = "/index";
        content = content.substring(1);
        List<String> args = new ArrayList<>(Arrays.asList(content.split("/")));
        for (int i = 0; i < args.size(); i++) {
            args.set(i, URLDecoder.decode(args.get(i)));
        }
        return createStack(nation, id, ctx, args);
    }

    private ArgumentStack createStack(DBNation nation, Long id, Context ctx, List<String> args) {
        LocalValueStore<Object> locals = new LocalValueStore<>(store);
        setupLocals(locals, nation, id, ctx, args);

        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
        locals.addProvider(stack);
        return stack;
    }

    private void handleCommand(DBNation nation, Long id, Context ctx) {
        ArgumentStack stack = createStack(nation, id, ctx);

        System.out.println("Stack:|| " + StringMan.getString(stack.getRemainingArgs()) + " | " + StringMan.getString(stack.getArgs()));

        try {
            ctx.header("Content-Type", "text/html;charset=UTF-8");

            List<String> args = new ArrayList<>(stack.getRemainingArgs());

            try {
                Object result = wrap(commands.call(stack), ctx);
                if (result != null && (!(result instanceof String) || !result.toString().isEmpty())) {
                    ctx.result(result.toString());
                }
            } catch (CommandUsageException e2) {
                String handler = ctx.endpointHandlerPath();
                logger.info("Handler " + handler);
                e2.printStackTrace();
                CommandCallable cmd = commands.getCallable(args);

                cmd.validatePermissions(stack.getStore(), permisser);

                if (cmd != null) {
                    String endpoint = WebRoot.REDIRECT + "/cmd_page";
                    ctx.result(cmd.toHtml(stack.getStore(), stack.getPermissionHandler(), endpoint));
                } else {
                    throw e2;
                }
            }
        } catch (Throwable e) {
            handleErrors(e, ctx);
        }
    }

    private void handleErrors(Throwable e, Context ctx) {
        e.printStackTrace();

        Map.Entry<String, String> entry = StringMan.stacktraceToString(e);

        ctx.result(rocker.error.template(entry.getKey(), entry.getValue()).render().toString());
    }

    private Object wrap(Object call, Context ctx) {
        String contentType = ctx.header("Content-Type");
        logger.info("HEADERS " + StringMan.getString(ctx.headerMap()));
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
                return rocker.alert.template("Response", str).render().toString();
            }
        }
        return call;
    }

    private LocalValueStore setupLocals(LocalValueStore<Object> locals, DBNation nation, Long userId, Context ctx, List<String> args) {
        if (locals == null) {
            locals = new LocalValueStore<>(store);
        }
        locals.addProvider(Key.of(Context.class), ctx);

        if (nation != null) {
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
        }

        User user = Locutus.imp().getDiscordApi().getUserById(userId);

        if (user != null) {
            locals.addProvider(Key.of(User.class, Me.class), user);
        }
        Map<String, String> path = ctx.pathParamMap();
        if (path.containsKey("guild_id")) {
            Long guildId = Long.parseLong(path.get("guild_id"));
            args.remove(guildId + "");
            GuildDB guildDb = Locutus.imp().getGuildDB(guildId);
            if (guildDb == null) {
                throw new IllegalArgumentException("No guild found for id: " + guildId);
            }
            Guild guild = guildDb.getGuild();

            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            locals.addProvider(Key.of(GuildDB.class, Me.class), guildDb);
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    locals.addProvider(Key.of(Member.class, Me.class), member);
                }
            }
        }
        return locals;
    }

    public void sseCmdPage(SseClient2 sse) throws IOException {
        Context ctx = sse.ctx;
        String pathStr = ctx.path();
        if (pathStr.startsWith("/")) pathStr = pathStr.substring(1);
        List<String> path = new ArrayList<>(Arrays.asList(pathStr.split("/")));
        path.remove("cmd_page");

        Map.Entry<Long, DBNation> userNationPair = getUserIdNationPair(sse.ctx);
        long userId = userNationPair.getKey();
        DBNation nation = userNationPair.getValue();

        LocalValueStore locals = setupLocals(null, nation, userId, ctx, path);

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

        GuildDB db = (GuildDB) locals.getProvided(Key.of(GuildDB.class, Me.class));
        String redirectBase = WebRoot.REDIRECT + "/" + (db != null ? db.getIdLong() + "/" : "") + cmd.getFullPath("/").toLowerCase() + "/";

        Map<String, String> combined = parseQueryMap(locals, sse, ctx.queryParamMap());
        ParametricCallable parametric = (ParametricCallable) cmd;
        List<String> orderedArgs = parametric.orderArgumentMap(combined, false);

        String redirect = redirectBase + StringMan.join(orderedArgs, "/");

        JsonObject response = new JsonObject();
        response.addProperty("action", "redirect");
        response.addProperty("value", redirect);
        sse.sendEvent(response);
    }
}

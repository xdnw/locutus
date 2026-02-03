package link.locutus.discord.web.commands.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.WebIO;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.web.jooby.handler.QueueMessageOutput;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static link.locutus.discord.web.WebUtil.resolveOAuthRedirect;
import static link.locutus.discord.web.jooby.PageHandler.CookieType.*;

public class EndpointPages extends PageHelper {
    @Command
    @ReturnType(WebPermission.class)
    public WebPermission permission(WebStore ws, ICommand<?> command, @Me @Default User user, @Me @Default Guild guild) {
        try {
            command.validatePermissions(ws.store(), ws.permisser());
            return new WebPermission(null, true);
        } catch (Throwable e) {
            String msg = e.getMessage();
            return new WebPermission(msg, false);
        }
    }

    @Command
    @ReturnType(WebBulkQuery.class)
    public WebBulkQuery query(Context context, WebStore ws, List<Map.Entry<String, Map<String, Object>>> queries) throws IOException {
        PageHandler handler = WebRoot.getInstance().getPageHandler();
        CommandGroup commands = handler.getCommands();
        CommandCallable apiCommands = commands.get("api");

        List<Object> result = new ObjectArrayList<>(Collections.nCopies(queries.size(), null));

        for (int i = 0; i < queries.size(); i++) {
            try {
                Map.Entry<String, Map<String, Object>> entry = queries.get(i);
                String key = entry.getKey();
                if (key.equals("command")) {
                    Object cmdResult = command(context, ws, entry.getValue());
                    result.set(i, cmdResult);
                    continue;
                }
                Map<String, Object> value = entry.getValue();
                List<String> path = key.contains("/") ? StringMan.split(key, "/") : List.of(key);
                CommandCallable callable = apiCommands.getCallable(path);
                if (callable instanceof ParametricCallable cmd) {
                    result.set(i, handler.call(cmd, ws, context, value));
                    continue;
                } else {
                    result.set(i, error("Invalid command: " + key));
                }
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                root.printStackTrace();
                result.set(i, error(root.getMessage()));
            }
        }
        return new WebBulkQuery(result);
    }

    private AtomicLong commandUid = new AtomicLong(0);

    @Command
    @ReturnType(WebViewCommand.class)
    public Object command(Context context, WebStore ws,
                              Map<String, Object> data) {
        if (data.size() == 1 && data.keySet().iterator().next().equals("data")) {
            Object tmp = data.get("data");
            if (tmp instanceof Map) {
                data = (Map<String, Object>) tmp;
            }
        }
        Object cmdObj = data.get("");
        if (cmdObj == null || !(cmdObj instanceof String)) throw new IllegalArgumentException("No command provided");
        String cmdStr = (String) cmdObj;
        List<String> split = StringMan.split(cmdStr, " ");

        CommandManager2 manager = Locutus.cmd().getV2();
        Map.Entry<CommandCallable, String> cmdAndPath = manager.getCommands().getCallableAndPath(split);
        String remainder = cmdAndPath.getValue();

        CommandCallable callable = cmdAndPath.getKey();
        if (callable instanceof ParametricCallable pc) {
            if (!pc.isViewable()) {
                throw new IllegalArgumentException("Command not viewable");
            }
            QueueMessageOutput queue = new QueueMessageOutput();
            WebIO io = new WebIO(queue,  ws.getGuild());

            LocalValueStore locals = manager.createLocals(null, ws.getGuild(), null, ws.getUser(), null, io, null);
            JSONObject cmdJson = new JSONObject(data);
            locals.addProvider(Key.of(JSONObject.class, Me.class), cmdJson);

            Map<String, Object> params = new Object2ObjectLinkedOpenHashMap<>(data);
            params.remove("");
            Object[] parsed = pc.parseArgumentMap2(params, locals, manager.getValidators(), manager.getPermisser(), true);
            Object result = pc.call(null, locals, parsed);
            if (result != null) {
                String formatted = MarkupUtil.formatDiscordMarkdown((result + "").trim(), io.getGuildOrNull());
                if (!formatted.isEmpty()) {
                    io.send(formatted);
                }
            }
            return new WebViewCommand(commandUid.incrementAndGet(), queue.getQueue());
        } else {
            throw new IllegalArgumentException("Invalid command: " + cmdStr);
        }
    }

    @Command
    @ReturnType(WebSuccess.class)
    public Object set_token(Context context, UUID token, @Default Long guild_id) {
        DBAuthRecord record = WebRoot.db().get(token);
        if (record == null) return error("Invalid token");
        int keepAlive = (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
        WebUtil.setCookieViaHeader(context, URL_AUTH.getCookieId(), token.toString(), keepAlive, true, null);

        if (guild_id != null && guild_id > Integer.MAX_VALUE) {
            WebUtil.setCookieViaHeader(context, GUILD_ID.getCookieId(), Long.toString(guild_id), -1, true, null);
        }

        return success();
    }

    @Command
    @ReturnType(SetGuild.class)
    public SetGuild set_guild(Context context, Guild guild, @Me @Default DBAuthRecord auth) {
        String id = guild.getId();
        String name = guild.getName();
        String icon = guild.getIconUrl();
        WebUtil.setCookieViaHeader(context, GUILD_ID.getCookieId(), guild.getId(), -1, true, null);
        return new SetGuild(id, name, icon);
    }

    @Command
    @ReturnType(WebSuccess.class)
    public WebSuccess unset_guild(Context context) {
        String removeCookieStrings = GUILD_ID.getCookieId() + "=; Max-Age=0; Path=/; HttpOnly";
        context.header("Set-Cookie", removeCookieStrings);
        return success();
    }

    @Command
    @ReturnType(WebUrl.class)
    public Object login_mail(Context context, DBNation nation, @Me @Default DBAuthRecord auth) throws IOException {
        if (auth != null) {
            return error("Already logged in");
        }
        String mailUrl = WebUtil.mailLogin(nation, false,true);
        return new WebUrl(mailUrl);
    }

    @Command(viewable = true)
    @ReturnType(value = WebOptions.class, cache = CacheType.LocalStorage, duration = 30)
    public Object input_options(String type, @Me @Default GuildDB db, @Me @Default User user, @Me @Default DBNation nation) {
        PageHandler ph = WebRoot.getInstance().getPageHandler();
        WebOption option = ph.getQueryOption(type);
        if (option == null) {
            return error("Invalid option type (" + type + "). available options: " + ph.getQueryOptionNames());
        }
        if (option.isRequiresGuild() && db == null) {
            return error(option.getName() + " requires a guild. Please select a guild.");
        }
        if (option.isRequiresNation() && nation == null) {
            return error(option.getName() + " requires a nation. Please select a nation.");
        }
        if (option.isRequiresUser() && user == null) {
            return error(option.getName() + " requires a user. Please select a user.");
        }
        try {
            return option.getQueryOptions(db, user, nation);
        } catch (Exception e) {
            e.printStackTrace();
            return error("Failed to get options for " + option.getName() + ": " + e.getMessage());
        }
    }

    @Command
    @ReturnType(WebSuccess.class)
    public Object set_oauth_code(Context context, @Me @Default DBNation me, String code) throws IOException {
        String redirect = resolveOAuthRedirect(context);
        System.out.println("OAuth Redirect: " + redirect);
        String access_token = AuthBindings.getAccessToken(code, redirect);
        if (access_token == null) {
            return error("Cannot fetch access_token from OAuth2 code");
        }
        JsonObject user = AuthBindings.getUser(access_token);
        if (user == null) {
            return error("Fetched access_token successfully, but failed to fetch user using it");
        }
        JsonElement idStr = user.get("id");
        if (idStr == null) {
            return error("Fetched access_token and user, but failed to fetch ID from user");
        }
        DBAuthRecord record = AuthBindings.generateAuthRecord(context, Long.parseLong(idStr.getAsString()), me == null ? null : me.getNation_id());
        int keepAlive = (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
        WebUtil.setCookieViaHeader(context, URL_AUTH.getCookieId(), record.getUUID().toString(), keepAlive, true, null);
        return success();
    }

    @Command
    @ReturnType(WebSuccess.class)
    public Object logout(WebStore ws, Context context, @Me @Default DBAuthRecord auth) {
        if (auth == null) {
            return error("No auth record found");
        }
        AuthBindings.logout(ws, context, auth, false);
        List<String> cookiesToRemove = Arrays.asList(
                URL_AUTH.getCookieId(),
                URL_AUTH_SET.getCookieId()
        );
        List<String> removeCookieStrings = new ObjectArrayList<>();
        for (String cookie : cookiesToRemove) {
            removeCookieStrings.add(cookie + "=; Max-Age=0; Path=/; HttpOnly");
        }
        context.header("Set-Cookie", StringMan.join(removeCookieStrings, ", "));
        return success();
    }

    @Command(viewable = true)
    @ReturnType(value = WebSession.class, cache = CacheType.LocalStorage)
    public Object session(WebStore ws, Context context, @Me @Default DBAuthRecord auth) throws IOException {
        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(true), auth.getUser(true), false);
        if (auth != null) {
            WebSession data = auth.toMap();
            if (guild != null) {
                data.setGuild(guild);
            }
            return data;
        } else {
            return error("No session record found");
        }
    }

    @Command
    @ReturnType(WebValue.class)
    public Object unregister(@Me @Default DBAuthRecord auth, boolean confirm) {
        if (auth == null) {
            return error("No auth record found");
        }
        Long discordId = auth.getUserId();
        Integer nationId = auth.getNationId();
        if (discordId == null) {
            return error("No user found");
        }
        if (nationId == null) {
            return error("No nation found");
        }
        if (!confirm) {
            return error("Please confirm to unregister");
        }
        Locutus.imp().getDiscordDB().unregister(nationId, discordId);
        return new WebValue(auth.getUUID().toString());
    }

    @Command
    @ReturnType(WebSuccess.class)
    public Object register(@Me @Default DBAuthRecord auth, boolean confirm) {
        if (auth == null) {
            return error("No auth record found");
        }
        Long discordId = auth.getUserId();
        Integer nationId = auth.getNationId();
        if (nationId == null) {
            return error("No nation found");
        }
        if (discordId == null) {
            return error("No user found");
        }
        if (DBNation.getById(nationId) == null) {
            return error("Nation with that ID was not found");
        }
        if (!confirm) {
            return error("Please confirm to register");
        }
        String userName = DiscordUtil.getUserName(discordId);
        Locutus.imp().getDiscordDB().addUser(new PNWUser(nationId, discordId, userName));
        return success();
    }
}

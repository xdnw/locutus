package link.locutus.discord.web.commands.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.web.jooby.PageHandler.CookieType.URL_AUTH;
import static link.locutus.discord.web.jooby.PageHandler.CookieType.URL_AUTH_SET;

public class EndpointPages extends PageHelper {

    @Command
    @ReturnType(WebSuccess.class)
    public WebSuccess set_token(Context context, UUID token) {
        DBAuthRecord record = WebRoot.db().get(token);
        if (record == null) return error("Invalid token");
        int keepAlive = (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
        WebUtil.setCookieViaHeader(context, URL_AUTH.getCookieId(), token.toString(), keepAlive, true, null);
        return success();
    }

    @Command
    @ReturnType(SetGuild.class)
    public WebSuccess set_guild(Context context, @Me @Default User user, Guild guild, @Me @Default DBAuthRecord auth) {
        if (user == null) return error("No user found, please login via discord");
        String id = guild.getId();
        String name = guild.getName();
        String icon = guild.getIconUrl();
        return new SetGuild(id, name, icon);
    }

    @Command
    @ReturnType(WebUrl.class)
    public WebSuccess login_mail(Context context, DBNation nation, @Me @Default DBAuthRecord auth) throws IOException {
        if (auth != null) {
            return error("Already logged in");
        }
        String mailUrl = WebUtil.mailLogin(nation, false,true);
        return new WebUrl(mailUrl);
    }

    @Command
    @ReturnType(value = WebOptions.class, cache = CacheType.LocalStorage, duration = 30)
    public WebSuccess input_options(String type, @Me @Default GuildDB db, @Me @Default User user, @Me @Default DBNation nation) {
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
    public WebSuccess set_oauth_code(Context context, @Me @Default DBNation me, String code) throws IOException {
        String access_token = AuthBindings.getAccessToken(code, Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#/oauth2");
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
    public WebSuccess logout(WebStore ws, Context context, @Me @Default DBAuthRecord auth) {
        if (auth == null) {
            return error("No auth record found");
        }
        AuthBindings.logout(ws, context, auth, false);
        List<String> cookiesToRemove = Arrays.asList(
                URL_AUTH.getCookieId(),
                URL_AUTH_SET.getCookieId()
        );
        List<String> removeCookieStrings = new ArrayList<>();
        for (String cookie : cookiesToRemove) {
            removeCookieStrings.add(cookie + "=; Max-Age=0; Path=/; HttpOnly");
        }
        context.header("Set-Cookie", StringMan.join(removeCookieStrings, ", "));
        return success();
    }

    @Command
    @ReturnType(WebBulkQuery.class)
    public WebBulkQuery query(Context context, WebStore ws, List<Map.Entry<String, Map<String, Object>>> queries) throws IOException {
        PageHandler handler = WebRoot.getInstance().getPageHandler();
        CommandGroup commands = handler.getCommands();
        CommandCallable apiCommands = commands.get("api");

        List<WebSuccess> result = new ObjectArrayList<>(Collections.nCopies(queries.size(), null));

        for (int i = 0; i < queries.size(); i++) {
            Map.Entry<String, Map<String, Object>> entry = queries.get(i);
            String key = entry.getKey();
            Map<String, Object> value = entry.getValue();
            List<String> path = key.contains("/") ? StringMan.split(key, "/") : List.of(key);
            CommandCallable callable = apiCommands.getCallable(path);
            String error;
            if (callable instanceof ParametricCallable cmd) {
                try {
                    result.set(i, (WebSuccess) handler.call(cmd, ws, context, value));
                    continue;
                } catch (Exception e) {
                    e.printStackTrace();
                    error = e.getMessage();
                }
            } else {
                error = "Invalid command: " + key;
            }
            result.set(i, error(error));
        }
        return new WebBulkQuery(result);
    }

    @Command
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
    public WebSuccess unregister(@Me @Default DBAuthRecord auth, boolean confirm) {
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
    public WebSuccess register(@Me @Default DBAuthRecord auth, boolean confirm) {
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

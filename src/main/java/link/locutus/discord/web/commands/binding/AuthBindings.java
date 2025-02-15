package link.locutus.discord.web.commands.binding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.jte.generated.precompiled.auth.JtenationpickerGenerated;
import gg.jte.generated.precompiled.auth.JtepickerGenerated;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.WM;
import link.locutus.discord.web.commands.page.PageHelper;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AuthBindings extends WebBindingHelper {
    @Binding
    @Me
    public static DBAuthRecord auth(Context context) {
        throw new IllegalStateException("No auth found in command locals");
    }

    @Binding
    @Me
    public static User user(Context context, @Me @Default DBAuthRecord auth) {
        User user = auth == null ? null : auth.getUser(false);
        if (user != null) return user;
        DBNation nation = auth == null ? null : auth.getNation(false);
        String url;
        if (nation != null) {
            user = nation.getUser();
            if (user != null) {
                return user;
            }
            url =  WebRoot.REDIRECT + "/page/login";
        } else {
            url = AuthBindings.getDiscordAuthUrl();
        }
        throw new RedirectResponse(HttpStatus.SEE_OTHER, url);
    }
    @Binding
    @Me
    public static DBNation nation(Context context, @Me @Default DBAuthRecord auth) {
        DBNation nation = auth == null ? null : auth.getNation(false);
        if (nation != null) return nation;

        User user = auth == null ? null : auth.getUser(false);
        String url;
        if (auth != null) {
            nation = DiscordUtil.getNation(user);
            if (nation != null) {
                return nation;
            }
            url =  WebRoot.REDIRECT + "/page/register";
        } else {
            url = WebRoot.REDIRECT + "/page/login?nation";
        }
        throw new RedirectResponse(HttpStatus.SEE_OTHER, url);
    }

    public static void setGuild(Context context, Guild guild) {
        String guildCookieId = PageHandler.CookieType.GUILD_ID.getCookieId();
        WebUtil.setCookie(context, guildCookieId, String.valueOf(guild.getIdLong()), (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS));
    }

    @Me
    @Binding
    public static Guild guild(Context context, @Default @Me DBNation nation, @Default @Me User user) {
        return guild(context, nation, user, true);
    }

    public static Guild guild(Context context, @Default @Me DBNation nation, @Default @Me User user, boolean allowRedirect) {
        if (nation != null && user == null) {
            GuildDB db = nation.getGuildDB();
            if (db != null) return db.getGuild();
        }
        String guildCookieId = PageHandler.CookieType.GUILD_ID.getCookieId();
        String guildStr = context.cookie(guildCookieId);
        String message = null;
        if (guildStr != null && MathMan.isInteger(guildStr)) {
            long id = Long.parseLong(guildStr);
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(id);
            if (guild == null) {
                message = "Guild not found with id: `" + id + "`";
            } else {
                return guild;
            }
        }
        if (allowRedirect) {
            if (user == null && nation == null) {
                throw new RedirectResponse(HttpStatus.SEE_OTHER, WebRoot.REDIRECT + "/page/login");
            }

            throw new RedirectResponse(HttpStatus.SEE_OTHER, WM.page.guildselect.cmd.toPageUrl());
        }
        return null;
    }

    public static final String AUTHORIZE_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String API_URL = "https://discord.com/api/users/@me";

    public static void logout(WebStore ws, Context context, @Nullable DBAuthRecord auth, boolean redirect) {
        for (PageHandler.CookieType type : PageHandler.CookieType.values()) {
            context.removeCookie(type.getCookieId());
        }
        if (auth != null) {
            WebRoot.db().removeToken(auth.getUUID(), auth.getNationIdRaw(), auth.getUserIdRaw());
        }
        if (redirect) {
            PageHelper.redirect(ws, context, WebRoot.REDIRECT, false);
        }
    }

    public static String getDiscordAuthUrl() {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", Settings.INSTANCE.APPLICATION_ID + ""));
        params.add(new BasicNameValuePair("redirect_uri", WebRoot.REDIRECT));
        params.add(new BasicNameValuePair("response_type", "code"));
        params.add(new BasicNameValuePair("scope", "identify guilds"));
        String query = URLEncodedUtils.format(params, StandardCharsets.UTF_8);
        return AUTHORIZE_URL + "?" + query;
    }

    public static JsonObject getUser(String accessToken) throws IOException {
        CloseableHttpClient client = HttpClients.custom().build();

        HttpUriRequest request = RequestBuilder.get()
                .setUri(API_URL)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        CloseableHttpResponse response = client.execute(request);
        String json = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.ISO_8859_1);

        JsonObject user  = JsonParser.parseString(json).getAsJsonObject();
        if (user != null && user.get("id") != null) {
//            addAccessToken(accessToken, user, true);
            return user;
        }
        return null;
    }

    public static String getAccessToken(String code, String redirect) throws IOException {
        if (redirect == null) redirect = WebRoot.REDIRECT;
        CloseableHttpClient client = HttpClients.custom().build();

        HttpUriRequest request = RequestBuilder.post()
                .setUri(TOKEN_URL)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .addParameter("grant_type", "authorization_code")
                .addParameter("client_id", Settings.INSTANCE.APPLICATION_ID + "")
                .addParameter("client_secret", Settings.INSTANCE.CLIENT_SECRET)
                .addParameter("redirect_uri", redirect)
                .addParameter("code", code)
                .addParameter("scope", "identify guilds")
                .build();

        CloseableHttpResponse response = client.execute(request);
        String json = new String(response.getEntity().getContent().readAllBytes());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonElement accessToken = obj.get("access_token");
        return accessToken == null ? null : accessToken.getAsString();
    }

    public static DBAuthRecord getAuth(WebStore ws, Context ctx) {
        try {
            return getAuth(ws, ctx, false, false, false);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static DBAuthRecord generateAuthRecord(Context context, long userId, Integer previousNationId) {
        UUID newUUID = WebUtil.generateSecureUUID();
        DBNation nationExisting = DiscordUtil.getNation(userId);
        Integer nationId = null;
        if (nationExisting == null && previousNationId != null && DBNation.getById(previousNationId) != null) {
            nationId = previousNationId;
            String fullDiscriminator = DiscordUtil.getUserName(userId);
            PNWUser pnwUser = new PNWUser(nationId, userId, fullDiscriminator);
            Locutus.imp().getDiscordDB().addUser(pnwUser);
        }
        return WebRoot.db().updateToken(newUUID, nationId, userId);
    }

    public static DBAuthRecord getAuth(WebStore ws, Context context, boolean allowRedirect, boolean requireNation, boolean requireUser) throws IOException {
        if ((requireNation || requireUser) && !allowRedirect) {
            throw new IllegalArgumentException("Cannot require nation or user without allowing redirect");
        }
        boolean pageDesiresRedirect = false;
        boolean isBackend = true;

        List<String> errors = new ArrayList<>();
        DBAuthRecord record = null;

        String header = context.header("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // Strip "Bearer " prefix
            UUID uuid = UUID.fromString(token);
            record = WebRoot.db().get(uuid);
        }

        if (record == null) {
            Map<String, String> cookies = context.cookieMap();
            String oAuthCookieId = PageHandler.CookieType.DISCORD_OAUTH.getCookieId();
            String uuidStr = cookies.get(oAuthCookieId);
            if (uuidStr == null) {
                String setCookie = context.res().getHeader("Set-Cookie");
                if (setCookie != null && !setCookie.isEmpty()) {
                    List<HttpCookie> httpCookies = HttpCookie.parse(setCookie);
                    for (HttpCookie cookie : httpCookies) {
                        if (cookie.getName().equals(oAuthCookieId)) {
                            uuidStr = cookie.getValue();
                            break;
                        }
                    }
                }
            }
            if (uuidStr != null) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (uuid != null) {
                        record = WebRoot.db().get(uuid);
                    }
                } catch (IllegalArgumentException e) {
                    context.removeCookie(PageHandler.CookieType.DISCORD_OAUTH.getCookieId());
                    uuidStr = null;
                    errors.add("Invalid cookie: " + uuidStr);
                }
            }

            if (record == null || record.getUserId() == null) {
                Map<String, List<String>> queries = context.queryParamMap();
                List<String> code = queries.get("code");
                if (code != null && code.size() == 1) {
                    pageDesiresRedirect = true;
                    String codeSingle = code.get(0);
                    new Exception().printStackTrace();
                    String access_token = getAccessToken(codeSingle, null);
                    if (access_token != null) {
                        JsonObject user = getUser(access_token);
                        JsonElement idStr = user.get("id");
                        if (idStr != null) {
                            Integer previousNationId = record == null ? null : record.getNationIdRaw();
                            record = generateAuthRecord(context, Long.parseLong(idStr.getAsString()), previousNationId);
                            WebUtil.setCookie(context, PageHandler.CookieType.DISCORD_OAUTH.getCookieId(), record.getUUID().toString(), (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS));
                        } else {
                            errors.add("Invalid user: " + user);
                        }
                    }
                }
            }
            {
                // if command auth exists
                String commandAuth = cookies.get(PageHandler.CookieType.URL_AUTH.getCookieId());
                if (commandAuth != null) {
                    UUID uuid = UUID.fromString(commandAuth);
                    record = WebRoot.db().get(uuid);
                }
            }
        }

        Map<String, List<String>> queryMap = context.queryParamMap();
        String path = context.path();
        boolean isLoginPage = switch (path.toLowerCase(Locale.ROOT)) {
            case "/page/login", "/page/login/" -> true;
            default -> false;
        };
        if (isLoginPage) {
            if (queryMap.containsKey("token")) {
                String token = StringMan.join(queryMap.getOrDefault("token", new ArrayList<>()), ",");
                if (token != null) {
                    pageDesiresRedirect = true;
                    try {
                        UUID uuid = UUID.fromString(token);
                        if (record == null || !record.getUUID().equals(uuid)) {
                            record = WebRoot.db().get(uuid);
                            if (record != null) {
                                isLoginPage = false;
                                WebUtil.setCookie(context, PageHandler.CookieType.URL_AUTH.getCookieId(), uuid.toString(), (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS));
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        errors.add("Invalid token: " + token);
                    }
                }
            }
        }

        if (((record == null || record.getNationId() == null) && requireNation) || isLoginPage) {
            requireNation |= queryMap.containsKey("nation") || queryMap.containsKey("alliance");
            String nationStr = StringMan.join(queryMap.getOrDefault("nation", new ArrayList<>()), ",");
            Integer nationIdFilter = null;
            if (!nationStr.isEmpty()) {
                nationIdFilter = DiscordUtil.parseNationId(nationStr);
            }

            String errorsStr = StringMan.join(queryMap.getOrDefault("message", new ArrayList<>()), "\n");
            if (!errorsStr.isEmpty()) {
                errors.addAll(Arrays.asList(errorsStr.split("\n")));
            }
            if (nationIdFilter != null) {
                DBNation nation = DBNation.getById(nationIdFilter);
                if (nation != null) {
                    try {
                        String mailUrl = WebUtil.mailLogin(nation, isBackend, false);
                        throw new RedirectResponse(HttpStatus.SEE_OTHER, mailUrl);
                    } catch (IllegalArgumentException e) {
                        errors.add(e.getMessage());
                    }
                } else {
                    errors.add("Could not find nation with id: " + nationIdFilter);
                }
            }

            if (requireNation) {
                String allianceStr = StringMan.join(queryMap.getOrDefault("alliance", new ArrayList<>()), ",");
                Set<Integer> allianceIdFilter = null;
                if (!allianceStr.isEmpty()) {
                    allianceIdFilter = PW.parseAlliances(null, allianceStr);
                }
                String html = nationPicker(ws, errors, allianceIdFilter);
                throw new RedirectResponse(HttpStatus.SEE_OTHER, html);
            } else {
                allowRedirect = true;
            }
        }

        if (record != null && record.isExpired()) {
            UUID verifiedUid = WebUtil.generateSecureUUID();
            record = WebRoot.db().updateToken(verifiedUid, record.getNationId(), record.getUserId());
            WebUtil.setCookie(context, PageHandler.CookieType.URL_AUTH.getCookieId(), verifiedUid.toString(), (int) TimeUnit.DAYS.toSeconds(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS));
        }

        if (record != null) {
            DBNation nation = record.getNation(true);
            User user = record.getUser(true);
            if (requireUser && user == null) {
                throw new RedirectResponse(HttpStatus.SEE_OTHER, getDiscordAuthUrl());
            }
            if ((user != null || nation != null) && (!requireNation || nation != null)) {
                return record;
            }
        }

        if (pageDesiresRedirect && allowRedirect) {
            String redirect = getRedirect(context);
            if (redirect != null) {
                throw new RedirectResponse(HttpStatus.SEE_OTHER, redirect);
            }
        }

        if (requireUser) {
            throw new RedirectResponse(HttpStatus.SEE_OTHER, getDiscordAuthUrl());
        }

        if (allowRedirect) {
            String discordAuthUrl = getDiscordAuthUrl();
            String mailAuthUrl = WebRoot.REDIRECT + "/page/login?nation";
            String html = WebStore.render(f -> JtepickerGenerated.render(f, null, ws, discordAuthUrl, mailAuthUrl));
            throw new RedirectResponse(HttpStatus.SEE_OTHER, html);
        }
        return record;
    }

    public static String nationPicker(WebStore ws, List<String> errors, Set<Integer> allianceIdFilter) {
        // Please select your nation
        List<DBNation> nations;
        if (allianceIdFilter != null) {
            nations = new ArrayList<>(Locutus.imp().getNationDB().getNationsByAlliance(allianceIdFilter));
        } else {
            nations = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        }
        // Sort nations by lasst_active (descending)
        nations.sort((o1, o2) -> Long.compare(o2.lastActiveMs(), o1.lastActiveMs()));
        // Name,Url
        List<String> nationNames = nations.stream().map(DBNation::getNation).toList();
        List<Integer> nationIds = nations.stream().map(DBNation::getId).toList();

        JsonArray nationArray = new JsonArray();
        for (String name : nationNames) {
            nationArray.add(name);
        }
        JsonArray nationIdArray = new JsonArray();
        for (Integer id : nationIds) {
            nationIdArray.add(id);
        }

        String html = WebStore.render(f -> JtenationpickerGenerated.render(f, null, ws, errors, nationArray, nationIdArray));
        throw new RedirectResponse(HttpStatus.SEE_OTHER, html);
    }

    public static String getRedirect(Context context) {
        return getRedirect(context, false);
    }

    public static void setRedirect(Context context, String url) {
        String id = PageHandler.CookieType.REDIRECT.getCookieId();
        WebUtil.setCookie(context, id, url, (int) 60);
    }

    public static String getRedirect(Context context, boolean indexDefault) {
        String redirect = context.cookie(PageHandler.CookieType.REDIRECT.getCookieId());
        if (redirect != null) {
            if (redirect != null && (redirect.contains("login") || redirect.contains("logout"))) {
                redirect = null;
            }
        }
        if (indexDefault && redirect == null) {
            redirect = WebRoot.REDIRECT;
        }
        return redirect;
    }
}

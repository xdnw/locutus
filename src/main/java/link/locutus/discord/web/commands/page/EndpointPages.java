package link.locutus.discord.web.commands.page;

import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoForm;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.jooby.PageHandler;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class EndpointPages extends PageHelper {
    private static final Gson gson = new Gson();

    @Command
    @NoForm
    public String login(WebStore ws, Context context, @Default Integer nation, @Default Long user) throws IOException {
        System.out.println("method " + context.method());
        System.out.println("headers " + context.res().getHeaderNames());
        Iterator<String> iter = context.req().getHeaderNames().asIterator();
        while (iter.hasNext()) {
            String header = iter.next();
            System.out.println("- " + header + " " + context.req().getHeader(header));
        }
        System.out.println("login " + context.req().getRemoteUser());
        System.out.println("attr " + context.attributeMap());
        System.out.println("host " + context.host());
        System.out.println("ip " + context.ip());
        System.out.println("session " + context.sessionAttributeMap());


        Map<String, String> queryMap = PageHandler.parseQueryMap(context.queryParamMap());
        boolean requireNation = queryMap.containsKey("nation");
        boolean requireUser = queryMap.containsKey("user");

        try {
            AuthBindings.Auth auth = AuthBindings.getAuth(ws, context, requireNation || requireUser, requireNation, requireUser);
            if (auth != null && (auth.userId() != null || auth.nationId() != null)) {
                Map<String, Object> data = auth.toMap();
                Guild guild = AuthBindings.guild(context, auth.getNation(), auth.getUser(), false);
                if (guild != null) {
                    data.put("guild", guild.getIdLong());
                }
                return gson.toJson(data);
            }
            return "{}";
        } catch (RedirectResponse response) {
            Map<String, String> data = Map.of("action", "redirect", "value", response.getMessage());
            return gson.toJson(data);
        }
    }

    @Command
    @NoForm
    public String logout(WebStore ws, Context context) throws IOException {
        AuthBindings.Auth auth = AuthBindings.getAuth(ws, context, false, false, false);
        Guild guild = auth == null ? null : AuthBindings.guild(context, auth.getNation(), auth.getUser(), false);
        AuthBindings.logout(context, false);
        if (auth != null) {
            Map<String, Object> data = auth.toMap();
            if (guild != null) {
                data.put("guild", guild.getIdLong());
            }
            data.put("success", true);
            data.put("message", "Logged out");
            return gson.toJson(data);
        } else {
            // no account to logout
            return gson.toJson(Map.of("success", false));
        }
    }
}

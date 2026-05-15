package link.locutus.discord.web.commands.page;

import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.web.commands.binding.value_types.WebError;
import link.locutus.discord.web.commands.binding.value_types.WebSuccess;

import java.net.MalformedURLException;
import java.net.URL;

public class PageHelper {
    public static void redirect(WebStore ws, Context context, String url) {
        URL urlObj;
        try {
            urlObj = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        context.header("Referrer-Policy", "no-referrer");
        context.redirect(url);
        context.status(302);
        context.header("cache-control", "no-store");
    }

    public static WebError error(String message) {
        return new WebError(message);
    }

    public static WebSuccess success() {
        return new WebSuccess(true, null);
    }
}

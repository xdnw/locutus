package link.locutus.discord.web.commands.page;

import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.MarkupUtil;
import gg.jte.generated.precompiled.auth.JteredirectGenerated;
import java.net.MalformedURLException;
import java.net.URL;

public class PageHelper {
    public static String redirect(WebStore ws, Context context, String url) {
        context.redirect(url);
        context.status(302);
        context.header("cache-control", "no-store");
        URL urlObj;
        try {
            urlObj = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return WebStore.render(f -> JteredirectGenerated.render(f, null, ws, urlObj));
    }
}

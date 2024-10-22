package link.locutus.discord.web.commands.page;

import io.javalin.http.Context;
import link.locutus.discord.util.MarkupUtil;

public class PageHelper {
    public static String redirect(Context context, String url) {
        context.redirect(url);
        context.status(304);
        context.header("cache-control", "no-store");
        return "Redirecting to " + MarkupUtil.htmlUrl(url, url) + ". If you are not redirected, click the link.";
    }
}

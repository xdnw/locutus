package gg.jte.generated.ondemand.user;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.discord.DiscordUtil;
public final class JteuserlinkidGenerated {
	public static final String JTE_NAME = "user/userlinkid.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,5,5,6,6,6,3,4,4,4,4};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, long userId) {
		gg.jte.generated.ondemand.user.JteuserlinkGenerated.render(jteOutput, jteHtmlInterceptor, ws, userId, DiscordUtil.getUser(userId), DiscordUtil.getNation(userId));
		jteOutput.writeContent("\r\n");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		long userId = (long)params.get("userId");
		render(jteOutput, jteHtmlInterceptor, ws, userId);
	}
}

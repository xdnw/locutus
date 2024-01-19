package gg.jte.generated.ondemand.user;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.db.entities.DBNation;
public final class JteuserlinknatidGenerated {
	public static final String JTE_NAME = "user/userlinknatid.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,4,4,4,6,6,6,6,4,5,5,5,5};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, int nationId) {
		gg.jte.generated.ondemand.user.JteuserlinkGenerated.render(jteOutput, jteHtmlInterceptor, ws, DiscordUtil.getUserIdByNationId(nationId), DiscordUtil.getUserByNationId(nationId), DBNation.getById(nationId));
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		int nationId = (int)params.get("nationId");
		render(jteOutput, jteHtmlInterceptor, ws, nationId);
	}
}

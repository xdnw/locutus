package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.Map;
public final class JtemanageannouncementsGenerated {
	public static final String JTE_NAME = "alliance/manageannouncements.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,11,11,11,11,12,12,13,13,14,14,15,15,15,15,15,7,8,9,9,9,9};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, List<Announcement> announcements) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				for (Announcement announcement : announcements) {
					jteOutput.writeContent("\r\n");
					gg.jte.generated.ondemand.alliance.JteannouncementGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, announcement);
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Manage Announcements", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		List<Announcement> announcements = (List<Announcement>)params.get("announcements");
		render(jteOutput, jteHtmlInterceptor, ws, db, announcements);
	}
}

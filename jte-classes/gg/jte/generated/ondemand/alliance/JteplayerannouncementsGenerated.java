package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
public final class JteplayerannouncementsGenerated {
	public static final String JTE_NAME = "alliance/playerannouncements.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,6,6,6,10,10,10,11,11,12,12,13,13,14,14,14,15,15,15,6,7,8,9,9,9,9};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, DBNation me, List<Announcement.PlayerAnnouncement> announcements) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				for (Announcement.PlayerAnnouncement plrAnn : announcements) {
					jteOutput.writeContent("\r\n    ");
					gg.jte.generated.ondemand.alliance.JteplayerannouncementGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, plrAnn, true, false, true);
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Announcements", null);
		jteOutput.writeContent("\r\n");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		DBNation me = (DBNation)params.get("me");
		List<Announcement.PlayerAnnouncement> announcements = (List<Announcement.PlayerAnnouncement>)params.get("announcements");
		render(jteOutput, jteHtmlInterceptor, ws, db, me, announcements);
	}
}

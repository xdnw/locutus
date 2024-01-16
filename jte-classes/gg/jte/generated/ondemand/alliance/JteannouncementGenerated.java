package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.Date;
import link.locutus.discord.config.Settings;
public final class JteannouncementGenerated {
	public static final String JTE_NAME = "alliance/announcement.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,10,10,10,13,13,13,15,15,17,17,18,18,18,20,20,23,23,26,26,26,26,29,29,29,29,32,32,33,33,35,35,36,36,37,37,38,38,39,39,40,40,40,40,41,41,41,10,11,12,12,12,12};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Announcement announcement) {
		jteOutput.writeContent("<div class=\"alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 ");
		if (announcement.active) {
			jteOutput.writeContent("\r\nbg-white border-danger\r\n");
		} else {
			jteOutput.writeContent("\r\nbg-secondary border-dark\r\n");
		}
		jteOutput.writeContent("\">\r\n    <h4 class=\"bold\">");
		jteOutput.writeUserContent(announcement.title);
		if (!announcement.active) {
			jteOutput.writeContent("\r\n        <span class=\"position-absolute top-0 start-50 translate-middle badge rounded-pill bg-warning\">archived</span>\r\n    ");
		}
		jteOutput.writeContent("</h4>\r\n    <figure>\r\n        <blockquote class=\"blockquote\">\r\n            <p class=\"markup\">");
		jteOutput.writeUserContent(announcement.body);
		jteOutput.writeContent("</p>\r\n        </blockquote>\r\n        <figcaption class=\"blockquote-footer text-dark\">\r\n            By <cite title=\"Author\">");
		gg.jte.generated.ondemand.user.JteuserlinkidGenerated.render(jteOutput, jteHtmlInterceptor, ws, announcement.sender);
		jteOutput.writeContent("</cite> on <span id=\"date-");
		jteOutput.writeUserContent(announcement.id);
		jteOutput.writeContent("\">announcement.date</span>\r\n        </figcaption>\r\n        <script>\r\n            document.getElementById(\"date-");
		jteOutput.writeUserContent(announcement.id);
		jteOutput.writeContent("\").innerHTML = new Date(");
		jteOutput.writeUserContent(announcement.date);
		jteOutput.writeContent(").toString()\r\n        </script>\r\n    </figure>\r\n    Filter: <kbd>");
		jteOutput.writeUserContent(announcement.filter);
		jteOutput.writeContent("</kbd><br>\r\n    Replacements: <kbd>");
		jteOutput.writeUserContent(announcement.replacements);
		jteOutput.writeContent("</kbd><br>\r\n\r\n    ");
		if (announcement.active) {
			jteOutput.writeContent("\r\n        <button cmd=\"");
			jteOutput.writeUserContent(CM.announcement.archive.cmd.create(announcement.id + "", null).toSlashCommand(false));
			jteOutput.writeContent("\" type=\"button\" class=\"btn btn-danger\">Archive</button>\r\n    ");
		} else {
			jteOutput.writeContent("\r\n        <button cmd=\"");
			jteOutput.writeUserContent(CM.announcement.archive.cmd.create(announcement.id + "", "false").toSlashCommand(false));
			jteOutput.writeContent("\" type=\"button\" class=\"btn btn-danger\">Unarchive</button>\r\n    ");
		}
		jteOutput.writeContent("\r\n    <a href=\"");
		jteOutput.writeUserContent(WebRoot.REDIRECT);
		jteOutput.writeContent("/announcementvariations/");
		jteOutput.writeUserContent(announcement.id);
		jteOutput.writeContent("\" type=\"button\" class=\"btn btn-primary\">View Variations</a>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Announcement announcement = (Announcement)params.get("announcement");
		render(jteOutput, jteHtmlInterceptor, ws, db, announcement);
	}
}

package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import link.locutus.discord.config.Settings;
import java.util.Date;
public final class JteannouncementvariationsGenerated {
	public static final String JTE_NAME = "alliance/announcementvariations.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,9,9,9,14,14,14,14,15,15,17,17,19,19,20,20,20,22,22,25,25,28,28,28,28,31,31,31,31,34,34,35,35,37,37,42,42,44,44,46,46,58,58,58,58,58,9,10,11,12,12,12,12};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Announcement announcement, List<Announcement.PlayerAnnouncement> announcements) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 ");
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
				jteOutput.writeContent("</kbd><br>\r\n\r\n    <a href=\"");
				jteOutput.writeUserContent(WebRoot.REDIRECT);
				jteOutput.writeContent("/manageannouncements\" type=\"button\" class=\"btn btn-primary\">View All Announcements</a>\r\n</div>\r\n<div class=\"container-fluid input-group input-group-lg\">\r\n    <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n</div>\r\n");
				for (Announcement.PlayerAnnouncement plrAnn : announcements) {
					jteOutput.writeContent("\r\n    <div class=\"variation-entry\">\r\n        ");
					gg.jte.generated.ondemand.alliance.JteplayerannouncementGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, plrAnn, false, true, false);
					jteOutput.writeContent("\r\n    </div>\r\n");
				}
				jteOutput.writeContent("\r\n\r\n<script>\r\n$(document).ready(function(){\r\n  $(\"#myInput\").on(\"keyup\", function() {\r\n    var value = $(this).val().toLowerCase();\r\n    $(\".variation-entry\").filter(function() {\r\n      $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n    });\r\n  });\r\n});\r\n</script>\r\n");
			}
		}, "Announcement Variations", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Announcement announcement = (Announcement)params.get("announcement");
		List<Announcement.PlayerAnnouncement> announcements = (List<Announcement.PlayerAnnouncement>)params.get("announcements");
		render(jteOutput, jteHtmlInterceptor, ws, db, announcement, announcements);
	}
}

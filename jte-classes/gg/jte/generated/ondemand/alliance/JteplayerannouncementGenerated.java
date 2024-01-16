package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.Date;
public final class JteplayerannouncementGenerated {
	public static final String JTE_NAME = "alliance/playerannouncement.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,8,8,8,15,15,15,17,17,19,19,20,20,20,22,22,24,24,27,27,29,29,31,31,31,31,31,31,31,31,31,31,33,33,35,35,35,35,35,35,37,37,38,38,39,39,41,41,41,41,41,41,41,41,43,43,45,45,45,45,47,47,48,48,49,49,51,51,53,53,54,54,55,55,56,56,57,57,58,58,59,59,60,60,60,8,9,10,11,12,13,13,13,13};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Announcement.PlayerAnnouncement plrAnn, boolean showFooter, boolean showReceiver, boolean includeDate) {
		jteOutput.writeContent("\r\n<div class=\"alert alert-dismissible fade show container-fluid mt-1 shadow border-start border-3 ");
		if (plrAnn.isActive()) {
			jteOutput.writeContent("\r\nbg-white border-danger\r\n");
		} else {
			jteOutput.writeContent("\r\nbg-secondary border-dark\r\n");
		}
		jteOutput.writeContent("\">\r\n    <h4 class=\"bold\">");
		jteOutput.writeUserContent(plrAnn.getParent().title);
		if (plrAnn.isActive()) {
			jteOutput.writeContent("\r\n        <span class=\"position-absolute top-0 start-50 translate-middle badge rounded-pill bg-success\">unread</span>\r\n    ");
		} else if (!plrAnn.getParent().active) {
			jteOutput.writeContent("\r\n        <span class=\"position-absolute top-0 start-50 translate-middle badge rounded-pill bg-warning\">archived</span>\r\n    ");
		}
		jteOutput.writeContent("</h4>\r\n    <figure>\r\n        <blockquote class=\"blockquote\">\r\n            <p class=\"markup\">");
		jteOutput.writeUserContent(plrAnn.getContent());
		jteOutput.writeContent("</p>\r\n        </blockquote>\r\n        ");
		if (showFooter) {
			jteOutput.writeContent("\r\n            <figcaption class=\"blockquote-footer text-dark\">\r\n                By <cite title=\"Author\">");
			gg.jte.generated.ondemand.user.JteuserlinkidGenerated.render(jteOutput, jteHtmlInterceptor, ws, plrAnn.getParent().sender);
			jteOutput.writeContent("</cite>");
			if (includeDate) {
				jteOutput.writeContent(" on <span id=\"date-");
				jteOutput.writeUserContent(plrAnn.receiverNation);
				jteOutput.writeContent("-");
				jteOutput.writeUserContent(plrAnn.getParent().id);
				jteOutput.writeContent("\">plrAnn.getParent().date</span>");
			}
			jteOutput.writeContent("\r\n            </figcaption>\r\n            ");
			if (includeDate) {
				jteOutput.writeContent("\r\n            <script>\r\n                document.getElementById(\"date-");
				jteOutput.writeUserContent(plrAnn.receiverNation);
				jteOutput.writeContent("-");
				jteOutput.writeUserContent(plrAnn.getParent().id);
				jteOutput.writeContent("\").innerHTML = new Date(");
				jteOutput.writeUserContent(plrAnn.getParent().date);
				jteOutput.writeContent(").toString()\r\n            </script>\r\n            ");
			}
			jteOutput.writeContent("\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (showReceiver) {
			jteOutput.writeContent("\r\n            <figcaption class=\"blockquote-footer text-dark\">\r\n                Received By <cite title=\"Author\">");
			gg.jte.generated.ondemand.user.JteuserlinknatidGenerated.render(jteOutput, jteHtmlInterceptor, ws, plrAnn.receiverNation);
			jteOutput.writeContent("</cite>");
			if (includeDate) {
				jteOutput.writeContent(" on <span id=\"date-");
				jteOutput.writeUserContent(plrAnn.receiverNation);
				jteOutput.writeContent("\">plrAnn.receiverNation</span> ");
			}
			jteOutput.writeContent("\r\n            </figcaption>\r\n            ");
			if (includeDate) {
				jteOutput.writeContent("\r\n            <script>\r\n                document.getElementById(\"date-");
				jteOutput.writeUserContent(plrAnn.receiverNation);
				jteOutput.writeContent("\").innerHTML = new Date(");
				jteOutput.writeUserContent(plrAnn.getParent().date);
				jteOutput.writeContent(").toString()\r\n            </script>\r\n            ");
			}
			jteOutput.writeContent("\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (showReceiver || showFooter) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n    </figure>\r\n    ");
		if (showFooter) {
			jteOutput.writeContent("\r\n        ");
			if (plrAnn.isActive()) {
				jteOutput.writeContent("\r\n        <button cmd=\"");
				jteOutput.writeUserContent(CM.announcement.read.cmd.create(plrAnn.getParent().id + "", null).toSlashCommand(false));
				jteOutput.writeContent("\" type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n        ");
			} else {
				jteOutput.writeContent("\r\n        <button cmd=\"");
				jteOutput.writeUserContent(CM.announcement.read.cmd.create(plrAnn.getParent().id + "", "false").toSlashCommand(false));
				jteOutput.writeContent("\" type=\"button\" class=\"btn btn-danger\">Mark Unread</button>\r\n        ");
			}
			jteOutput.writeContent("\r\n    ");
		}
		jteOutput.writeContent("\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Announcement.PlayerAnnouncement plrAnn = (Announcement.PlayerAnnouncement)params.get("plrAnn");
		boolean showFooter = (boolean)params.get("showFooter");
		boolean showReceiver = (boolean)params.get("showReceiver");
		boolean includeDate = (boolean)params.get("includeDate");
		render(jteOutput, jteHtmlInterceptor, ws, db, plrAnn, showFooter, showReceiver, includeDate);
	}
}

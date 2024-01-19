package gg.jte.generated.ondemand.guild;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBAlliance;
public final class JteguildentryGenerated {
	public static final String JTE_NAME = "guild/guildentry.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,5,5,5,11,11,11,12,12,13,13,14,14,16,16,19,19,19,19,27,27,28,28,29,29,30,30,30,30,31,31,32,32,33,33,34,34,35,35,42,42,42,5,6,7,8,9,9,9,9};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Guild guild, GuildDB db, boolean highlight, String note) {
		jteOutput.writeContent("\r\n<div class=\"row p-2 m-3 guild-icon guild-entry bg-");
		jteOutput.writeUserContent(highlight?"secondary":"primary");
		jteOutput.writeContent(" bg-gradient\" style=\"border-radius:10px\">\r\n    ");
		if (highlight) {
			jteOutput.writeContent("\r\n        <h6>Example heading <span class=\"badge bg-secondary\">");
			jteOutput.writeUserContent(note);
			jteOutput.writeContent("</span></h6>\r\n    ");
		}
		jteOutput.writeContent("\r\n    <div class=\"col-md-2\">\r\n        <img alt=\"guild-icon\" class=\"img-fluid guild-icon\" src=\"");
		jteOutput.writeUserContent(guild.getIconUrl());
		jteOutput.writeContent("\">\r\n    </div>\r\n    <div class=\"col-md-10 p-2\">\r\n        <h4 class=\"row text-white\">");
		jteOutput.writeUserContent(guild.getName());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(guild.getIdLong());
		jteOutput.writeContent("</h4>\r\n        <div class=\"row\">\r\n            <div class=\"col\">\r\n                <p class=\"lead\"></p>\r\n            </div>\r\n        </div>\r\n        <div class=\"row\">\r\n            <div class=\"col\">\r\n            <a href=\"/page/setguild/");
		jteOutput.writeUserContent(guild.getIdLong());
		jteOutput.writeContent("\" class=\"m-1 btn btn-primary btn-lg border-2 border-light shadow\">Set Guild</a>\r\n            ");
		if (db.isValidAlliance()) {
			jteOutput.writeContent("\r\n                ");
			for (DBAlliance alliance : db.getAllianceList().getAlliances()) {
				jteOutput.writeContent("\r\n                    <a href=\"https://politicsandwar.com/alliance/id=");
				jteOutput.writeUserContent(alliance.getAlliance_id());
				jteOutput.writeContent("\" class=\"m-1 btn btn-primary btn-lg border-2 border-light shadow\">View ");
				jteOutput.writeUserContent(alliance.getQualifiedName());
				jteOutput.writeContent(" 🡕</a>\r\n                ");
			}
			jteOutput.writeContent("\r\n            ");
		}
		jteOutput.writeContent("\r\n            ");
		if (db.getOffshore() != null) {
			jteOutput.writeContent("\r\n                <a href=\"/");
			jteOutput.writeUserContent(guild.getIdLong());
			jteOutput.writeContent("/bankindex\" class=\"m-1 btn btn-primary btn-lg border-2 border-light shadow\">View Bank</a>\r\n            ");
		}
		jteOutput.writeContent("\r\n            </div>\r\n        </div>\r\n        <div class=\"row\">\r\n            \r\n        </div>\r\n    </div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Guild guild = (Guild)params.get("guild");
		GuildDB db = (GuildDB)params.get("db");
		boolean highlight = (boolean)params.get("highlight");
		String note = (String)params.get("note");
		render(jteOutput, jteHtmlInterceptor, ws, guild, db, highlight, note);
	}
}

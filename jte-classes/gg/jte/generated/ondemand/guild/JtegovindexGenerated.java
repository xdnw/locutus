package gg.jte.generated.ondemand.guild;
import java.util.*;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.Locutus;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.web.jooby.WebRoot;
public final class JtegovindexGenerated {
	public static final String JTE_NAME = "guild/govindex.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,22,22,22,29,29,29,29,31,31,35,35,38,38,40,40,42,42,46,46,48,48,58,58,59,59,59,59,60,60,63,63,64,64,65,65,66,66,69,69,70,70,70,70,70,22,23,24,25,26,27,27,27,27};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Guild guild, GuildDB db, DBNation nation, User author, Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n\r\n");
				if (Roles.MILCOM.has(author, db.getGuild())) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2 class=\"\">Milcom</h2>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n\r\n\r\n");
				if (Roles.FOREIGN_AFFAIRS.has(author, db.getGuild())) {
					jteOutput.writeContent("\r\n\r\n");
				}
				jteOutput.writeContent("\r\n\r\n");
				if (Roles.INTERNAL_AFFAIRS.has(author, db.getGuild())) {
					jteOutput.writeContent("\r\n\r\n\r\n\r\n");
				}
				jteOutput.writeContent("\r\n\r\n");
				if (Roles.ECON.has(author, db.getGuild())) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2 class=\"\">\r\n\r\n\r\n    </h2>\r\n    <hr>\r\n    <p class=\"lead\">\r\n        Grants are funds provided by the alliance to help you grow your nation. Debt from grants can expire after a period of time (typically 60 days, see the note when accepting a grant)<br><br>\r\n        <b>note: </b>This interface can only approve <i>some</i> grants.\r\n        ");
					if (db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL) != null) {
						jteOutput.writeContent("\r\n        If you would like more general assistance or advice, please <a href=\"https://discord.com/channels/");
						jteOutput.writeUserContent(db.getIdLong());
						jteOutput.writeContent("/");
						jteOutput.writeUserContent((db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL)).getIdLong());
						jteOutput.writeContent("\">open a ticket on discord.</a>\r\n        ");
					}
					jteOutput.writeContent("\r\n    </p>\r\n    <div class=\"list-group\">\r\n    <a href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/infragrants\" class=\"link-primary list-group-item bg-light\">&#127959;&#65039; Infra grants</a>\r\n    <a href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/landgrants\" class=\"link-primary list-group-item bg-light\">&#127966;&#65039; Land grants</a>\r\n    <a href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/citygrants\" class=\"link-primary list-group-item bg-light\">&#127961;&#65039; City grants</a>\r\n    <a href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/projectgrants\" class=\"link-primary list-group-item bg-light\">&#128508; Project grants</a>\r\n    </div>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Guild Gov index", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Guild guild = (Guild)params.get("guild");
		GuildDB db = (GuildDB)params.get("db");
		DBNation nation = (DBNation)params.get("nation");
		User author = (User)params.get("author");
		Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup = (Map<IACheckup.AuditType, Map.Entry<Object, String>>)params.get("checkup");
		render(jteOutput, jteHtmlInterceptor, ws, guild, db, nation, author, checkup);
	}
}

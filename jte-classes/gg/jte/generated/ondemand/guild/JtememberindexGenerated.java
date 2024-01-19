package gg.jte.generated.ondemand.guild;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.web.commands.page.IndexPages;
import java.util.*;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.Locutus;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import link.locutus.discord.util.PnwUtil;
import java.util.Collection;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.config.Settings;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.db.entities.announce.Announcement;
public final class JtememberindexGenerated {
	public static final String JTE_NAME = "guild/memberindex.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,30,30,30,44,44,44,45,45,47,47,48,48,49,49,50,50,51,51,52,52,53,53,56,56,58,58,59,59,61,61,63,63,64,64,66,66,67,67,69,69,70,70,71,71,74,74,75,75,85,85,87,87,88,88,90,90,91,91,92,92,93,93,107,107,108,108,109,109,110,110,111,111,117,117,118,118,119,119,120,120,121,121,122,122,123,123,124,124,125,125,128,128,131,131,137,137,139,139,141,141,143,143,145,145,149,149,156,156,157,157,157,157,158,158,161,161,161,161,162,162,162,162,163,163,163,163,164,164,164,164,167,167,168,168,169,169,169,169,169,30,31,32,33,34,35,36,37,38,39,40,41,42,43,43,43,43};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Guild guild, GuildDB db, DBNation nation, User author, double[] deposits, Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup, Collection<JavaCity> cities, boolean isFightingActives, Map<DBWar, DBNation> offensives, Map<DBWar, DBNation> defensives, Map<DBWar, WarCard> warCards, Map<DBWar, AttackType> recommendedAttacks, List<Announcement.PlayerAnnouncement> announcements) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				if (checkup != null) {
					jteOutput.writeContent("\r\n    <div class=\"container\">\r\n        ");
					for (Announcement.PlayerAnnouncement plrAnn : announcements) {
						jteOutput.writeContent("\r\n            ");
						gg.jte.generated.ondemand.alliance.JteplayerannouncementGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, plrAnn, true, false, true);
						jteOutput.writeContent("\r\n        ");
					}
					jteOutput.writeContent("\r\n        <a class=\"btn btn-primary btn-sm\" href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/announcements/-a\">View All Announcements</a>\r\n        ");
					if (Roles.ADMIN.has(author, guild)) {
						jteOutput.writeContent("\r\n            <a class=\"btn btn-danger btn-sm\" href=\"");
						jteOutput.writeUserContent(WebRoot.REDIRECT);
						jteOutput.writeContent("/manageannouncements\">Manage Announcements</a>\r\n        ");
					}
					jteOutput.writeContent("\r\n    </div>\r\n    <div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n        <h2 class=\"\"><i class=\"bi bi-exclamation-diamond-fill text-danger px-1 me-2\"></i>");
					jteOutput.writeUserContent(checkup.size());
					jteOutput.writeContent(" Audits</h2>\r\n        <div>\r\n    ");
					for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkup.entrySet()) {
						jteOutput.writeContent("\r\n        ");
						if (entry.getValue() != null && entry.getValue().getValue() != null) {
							jteOutput.writeContent("\r\n        <div role=\"alert\" class=\"alert p-1 my-1 border alert-dismissible fade show\r\n        ");
							if (entry.getKey().severity == IACheckup.AuditSeverity.INFO) {
								jteOutput.writeContent("\r\n            alert-info border-info\">\r\n        ");
							}
							jteOutput.writeContent("\r\n        ");
							if (entry.getKey().severity == IACheckup.AuditSeverity.WARNING) {
								jteOutput.writeContent("\r\n            alert-warning border-warning\">\r\n        ");
							}
							jteOutput.writeContent("\r\n        ");
							if (entry.getKey().severity == IACheckup.AuditSeverity.DANGER) {
								jteOutput.writeContent("\r\n            alert-danger border-danger\">\r\n        ");
							}
							jteOutput.writeContent("\r\n            <b>");
							jteOutput.writeUserContent(entry.getKey());
							jteOutput.writeContent("</b><br>\r\n            ");
							jteOutput.writeUserContent(entry.getValue().getValue());
							jteOutput.writeContent(";\r\n            <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n        </div>\r\n        ");
						}
						jteOutput.writeContent("\r\n    ");
					}
					jteOutput.writeContent("\r\n        </div>\r\n    </div>\r\n}\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2 class=\"\">&#127975; ATM</h2>\r\n    <hr>\r\n    <p class=\"lead\">It is recommended to safekeep funds you aren't using, as to avoid becoming a target and taking unnecessary losses.</p>\r\n    <p>\r\n        <b>Your deposits: </b>\r\n        ");
					jteOutput.writeUserContent(PnwUtil.resourcesToString(PnwUtil.normalize(deposits)));
					jteOutput.writeContent("\r\n    </p>\r\n    <a href=\"");
					jteOutput.writeUserContent(WebRoot.REDIRECT);
					jteOutput.writeContent("/command/withdraw\"  class=\"btn btn-primary btn\">&#128279; Withdraw Funds</a>\r\n    <a href=\"https://politicsandwar.com/alliance/id=");
					jteOutput.writeUserContent(nation.getAlliance_id());
					jteOutput.writeContent("&display=bank\" class=\"btn btn-primary btn\">Deposit Funds <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n</div>\r\n");
					if (offensives.size() + defensives.size() > 0) {
						jteOutput.writeContent("\r\n    ");
						gg.jte.generated.ondemand.guild.JtemywarsGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, cities, isFightingActives, offensives, defensives, warCards, recommendedAttacks);
						jteOutput.writeContent("\r\n");
					}
					jteOutput.writeContent("\r\n");
					if (!db.getCoalition(Coalition.ENEMIES).isEmpty()) {
						jteOutput.writeContent("\r\n    <div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n\r\n        <h2 class=\"\">&#9876;&#65039; War Finder</h2>\r\n        <hr>\r\n        <p>\r\n            <button class=\"btn btn-primary\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapseEnemies\" aria-expanded=\"false\" aria-controls=\"collapseEnemies\">\r\n                Show enemies list\r\n            </button>\r\n        </p>\r\n        <div class=\"collapse\" id=\"collapseEnemies\">\r\n            <div class=\"card card-body\">\r\n                <ol>\r\n                    <div class=\"list-group\">\r\n                        ");
						for (int enemyId : db.getCoalition(Coalition.ENEMIES)) {
							jteOutput.writeContent("\r\n                            ");
							if (Locutus.imp().getNationDB().getAllianceName(enemyId) != null) {
								jteOutput.writeContent("\r\n                                <li class=\"link-primary list-group-item bg-light\">");
								jteOutput.writeUserContent(Locutus.imp().getNationDB().getAllianceName(enemyId));
								jteOutput.writeContent("</li>\r\n                            ");
							}
							jteOutput.writeContent("\r\n                        ");
						}
						jteOutput.writeContent("\r\n                    </div>\r\n                </ol>\r\n            </div>\r\n        </div>\r\n        <div class=\"list-group\">\r\n        ");
						if (nation.getTankPct() > 0.8 && nation.getAircraftPct() > 0.8) {
							jteOutput.writeContent("\r\n            ");
							if (db.hasAlliance()) {
								jteOutput.writeContent("\r\n                <a href=\"javascript:void(0)\" cmd=\"");
								jteOutput.writeUserContent(CM.war.find.enemy.cmd.create("#off>0,~enemies,#getAttacking(~allies,#active_m<7200)>0", null, null, null, null, "true", null, null, null, null, null).toSlashCommand(false));
								jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Priority War targets</a>\r\n            ");
							}
							jteOutput.writeContent("\r\n            <a href=\"javascript:void(0)\" cmd=\"");
							jteOutput.writeUserContent(CM.war.find.enemy.cmd.create(null, null, null, null, null, "true", null, null, null, null, null).toSlashCommand(false));
							jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Secondary War targets</a>\r\n            <a href=\"javascript:void(0)\" cmd=\"");
							jteOutput.writeUserContent(CM.war.find.enemy.cmd.create(null, null, null, null, null, null, null, null, null, null, null).toSlashCommand(false));
							jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">All enemies</a>\r\n        ");
						}
						jteOutput.writeContent("\r\n        <a href=\"javascript:void(0)\" cmd=\"");
						jteOutput.writeUserContent(CM.war.find.enemy.cmd.create(null, null, null, null, null, null, "true", "true", null, null, null).toSlashCommand(false));
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Weak enemies</a>\r\n        <a href=\"javascript:void(0)\" cmd=\"");
						jteOutput.writeUserContent(CM.war.find.damage.cmd.create("~enemies", null, null, null, null, null, null, null, null, null, null).toSlashCommand(false));
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Find damage targets</a>\r\n        </div>\r\n    </div>\r\n");
					}
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2 class=\"\">&#127919; Raid Finder</h2>\r\n    <a href=\"javascript:void(0)\" cmd=\"");
					jteOutput.writeUserContent(CM.spy.find.intel.cmd.create(null, null, null, null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Intel Op</a>\r\n    <hr>\r\n    <p class=\"lead\">\r\n        This is a tool, you are responsible for your own raids. Ask a gov member if you are unsure about a target\r\n    </p>\r\n    <div class=\"list-group\">\r\n    <a href=\"javascript:void(0)\" replace for=\"raid-out-1\" cmd=\"");
					jteOutput.writeUserContent(CM.war.find.raid.cmd.create(null, "10", null, null, null, null, null, null, null, null, null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Safe inactive nones/apps</a>\r\n        <div id=\"raid-out-1\" class=\"bg-light border border-top-0 mx-1 mb-1 slide\" style=\"display:none\"></div>\r\n    <a href=\"javascript:void(0)\" replace for=\"raid-out-2\" cmd=\"");
					jteOutput.writeUserContent(CM.war.find.raid.cmd.create("(*,#color=beige)|(*,#vm_turns>0)", "25", "2d", null, null, null, null, null, null, null, null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">List nations coming out of beige</a>\r\n        <div id=\"raid-out-2\" class=\"bg-light border border-top-0 mx-1 mb-1 slide\" style=\"display:none\"></div>\r\n    <a href=\"javascript:void(0)\" replace for=\"raid-out-3\" cmd=\"");
					jteOutput.writeUserContent(CM.war.find.raid.cmd.create("#tank%<20,#soldier%<40,*", "25", "2d", "true", null, null, null, null, null, null, null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">List actives with minimal ground (2d inactive)</a>\r\n        <div id=\"raid-out-3\" class=\"bg-light border border-top-0 mx-1 mb-1 slide\" style=\"display:none\"></div>\r\n    <a href=\"javascript:void(0)\" replace for=\"raid-out-4\" cmd=\"");
					jteOutput.writeUserContent(CM.war.find.raid.cmd.create("#def>0,#RelativeStrength<1,*", "25", "0d", "true", null, null, null, null, null, null, null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">Include actives losing their current wars</a>\r\n        <div id=\"raid-out-4\" class=\"bg-light border border-top-0 mx-1 mb-1 slide\" style=\"display:none\"></div>\r\n    <a href=\"javascript:void(0)\" replace for=\"raid-out-5\" cmd=\"");
					jteOutput.writeUserContent(CM.war.find.unprotected.cmd.create("*", "25", null, "true", null, null, null, null, "true", null).toSlashCommand(false));
					jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">List actives possibly unable to counter properly</a>\r\n        <div id=\"raid-out-5\" class=\"bg-light border border-top-0 mx-1 mb-1 slide\" style=\"display:none\"></div>\r\n    </div>\r\n</div>\r\n");
					if (Roles.ECON_GRANT_SELF.toRole(db.getGuild()) != null) {
						jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2 class=\"\">&#127974; Grants</h2>\r\n    <hr>\r\n    <p class=\"lead\">\r\n        Grants are funds provided by the alliance to help you grow your nation. Debt from grants can expire after a period of time (typically 60 days, see the note when accepting a grant)<br><br>\r\n        <b>note: </b>This interface can only approve <i>some</i> grants.\r\n        ");
						if (db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL) != null) {
							jteOutput.writeContent("\r\n        If you would like more general assistance or advice, please <a href=\"https://discord.com/channels/");
							jteOutput.writeUserContent(db.getIdLong());
							jteOutput.writeContent("/");
							jteOutput.writeUserContent(((GuildMessageChannel) db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL)).getIdLong());
							jteOutput.writeContent("\">open a ticket on discord.</a>\r\n        ");
						}
						jteOutput.writeContent("\r\n    </p>\r\n    <div class=\"list-group\">\r\n    <a href=\"");
						jteOutput.writeUserContent(WebRoot.REDIRECT);
						jteOutput.writeContent("/infragrants/");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">&#127959;&#65039; Infra grants</a>\r\n    <a href=\"");
						jteOutput.writeUserContent(WebRoot.REDIRECT);
						jteOutput.writeContent("/landgrants/");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">&#127966;&#65039; Land grants</a>\r\n    <a href=\"");
						jteOutput.writeUserContent(WebRoot.REDIRECT);
						jteOutput.writeContent("/citygrants/");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">&#127961;&#65039; City grants</a>\r\n    <a href=\"");
						jteOutput.writeUserContent(WebRoot.REDIRECT);
						jteOutput.writeContent("/projectgrants/");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\" class=\"link-primary list-group-item bg-light\">&#128508; Project grants</a>\r\n    </div>\r\n</div>\r\n");
					}
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Guild Alliance index", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Guild guild = (Guild)params.get("guild");
		GuildDB db = (GuildDB)params.get("db");
		DBNation nation = (DBNation)params.get("nation");
		User author = (User)params.get("author");
		double[] deposits = (double[])params.get("deposits");
		Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup = (Map<IACheckup.AuditType, Map.Entry<Object, String>>)params.get("checkup");
		Collection<JavaCity> cities = (Collection<JavaCity>)params.get("cities");
		boolean isFightingActives = (boolean)params.get("isFightingActives");
		Map<DBWar, DBNation> offensives = (Map<DBWar, DBNation>)params.get("offensives");
		Map<DBWar, DBNation> defensives = (Map<DBWar, DBNation>)params.get("defensives");
		Map<DBWar, WarCard> warCards = (Map<DBWar, WarCard>)params.get("warCards");
		Map<DBWar, AttackType> recommendedAttacks = (Map<DBWar, AttackType>)params.get("recommendedAttacks");
		List<Announcement.PlayerAnnouncement> announcements = (List<Announcement.PlayerAnnouncement>)params.get("announcements");
		render(jteOutput, jteHtmlInterceptor, ws, guild, db, nation, author, deposits, checkup, cities, isFightingActives, offensives, defensives, warCards, recommendedAttacks, announcements);
	}
}

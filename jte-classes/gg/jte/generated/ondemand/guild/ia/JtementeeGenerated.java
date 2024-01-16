package gg.jte.generated.ondemand.guild.ia;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
public final class JtementeeGenerated {
	public static final String JTE_NAME = "guild/ia/mentee.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,34,34,34,41,41,41,41,41,42,42,43,43,44,44,45,45,47,47,48,48,48,48,49,49,52,52,53,53,54,54,57,57,60,60,63,63,65,65,66,66,66,66,67,67,67,67,67,67,70,70,70,70,70,70,72,72,73,73,75,75,77,77,78,78,80,80,81,81,83,83,84,84,85,85,88,88,89,89,96,96,96,96,34,35,36,37,38,38,38,38};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, DBNation mentee, IAChannel myChan, IACategory.SortedCategory category, Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup) {
		jteOutput.writeContent("\r\n<tr>\r\n    <td><a href=\"");
		jteOutput.writeUserContent(mentee.getNationUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(mentee.getNation());
		jteOutput.writeContent("</a></td>\r\n    <td>");
		jteOutput.writeUserContent(mentee.getCities());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(mentee.getMMR());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(mentee.getMMRBuildingStr());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(mentee.getOff());
		jteOutput.writeContent("</td>\r\n    <td>\r\n        ");
		if (myChan != null && myChan.getChannel() != null) {
			jteOutput.writeContent("\r\n        <a href=\"");
			jteOutput.writeUserContent(DiscordUtil.getChannelUrl(myChan.getChannel()));
			jteOutput.writeContent("\">#");
			jteOutput.writeUserContent(myChan.getChannel().getName());
			jteOutput.writeContent("</a>\r\n        ");
		}
		jteOutput.writeContent("\r\n    </td>\r\n    <td>\r\n        ");
		if (category != null) {
			jteOutput.writeContent("\r\n            ");
			jteOutput.writeUserContent(category.name());
			jteOutput.writeContent("\r\n        ");
		}
		jteOutput.writeContent("\r\n    </td>\r\n    <td>\r\n        <button class=\"btn btn-sm btn-primary\" cmd=\"");
		jteOutput.writeUserContent(CM.interview.unassignMentee.cmd.create(mentee.getNation_id() + "").toSlashCommand(false));
		jteOutput.writeContent("\">Unassign</button>\r\n    </td>\r\n</tr>\r\n");
		if (checkup != null && !checkup.isEmpty()) {
			jteOutput.writeContent("\r\n<tr>\r\n    <td colspan=\"100\">\r\n        <div class=\"accordion\" id=\"auditAccordion");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\">\r\n            <div class=\"accordion-item\">\r\n                <h2 class=\"accordion-header\" id=\"headingaudit");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\">\r\n                    <button class=\"accordion-button collapsed p-1 btn-sm\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapseAudit");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapseBeige");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\">\r\n                        ");
			jteOutput.writeUserContent(checkup.size());
			jteOutput.writeContent(" Audits (");
			jteOutput.writeUserContent(checkup.keySet().stream().filter(f -> f.severity == IACheckup.AuditSeverity.DANGER).count());
			jteOutput.writeContent(" CRITICAL|");
			jteOutput.writeUserContent(checkup.keySet().stream().filter(f -> f.severity == IACheckup.AuditSeverity.WARNING).count());
			jteOutput.writeContent(" WARNING)\r\n                    </button>\r\n                </h2>\r\n                <div id=\"collapseAudit");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"headingAudit");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\" data-bs-parent=\"#auditAccordion");
			jteOutput.writeUserContent(mentee.getNation_id());
			jteOutput.writeContent("\">\r\n                    <div class=\"accordion-body bg-light\">\r\n        ");
			for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkup.entrySet()) {
				jteOutput.writeContent("\r\n            ");
				if (entry.getValue() != null && entry.getValue().getValue() != null) {
					jteOutput.writeContent("\r\n                <div role=\"alert\" class=\"alert p-1 my-1 border alert-dismissible fade show\r\n                ");
					if (entry.getKey().severity == IACheckup.AuditSeverity.INFO) {
						jteOutput.writeContent("\r\n                    alert-info border-info\">\r\n                ");
					}
					jteOutput.writeContent("\r\n                ");
					if (entry.getKey().severity == IACheckup.AuditSeverity.WARNING) {
						jteOutput.writeContent("\r\n                    alert-warning border-warning\">\r\n                ");
					}
					jteOutput.writeContent("\r\n                ");
					if (entry.getKey().severity == IACheckup.AuditSeverity.DANGER) {
						jteOutput.writeContent("\r\n                    alert-danger border-danger\">\r\n                ");
					}
					jteOutput.writeContent("\r\n                    <b>");
					jteOutput.writeUserContent(entry.getKey());
					jteOutput.writeContent("</b><br>\r\n                    ");
					jteOutput.writeUserContent(entry.getValue().getValue());
					jteOutput.writeContent("\r\n                    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n        ");
			}
			jteOutput.writeContent("\r\n                    </div>\r\n                </div>\r\n            </div>\r\n        </div>\r\n    </td>\r\n</tr>\r\n");
		}
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		DBNation mentee = (DBNation)params.get("mentee");
		IAChannel myChan = (IAChannel)params.get("myChan");
		IACategory.SortedCategory category = (IACategory.SortedCategory)params.get("category");
		Map<IACheckup.AuditType, Map.Entry<Object, String>> checkup = (Map<IACheckup.AuditType, Map.Entry<Object, String>>)params.get("checkup");
		render(jteOutput, jteHtmlInterceptor, ws, mentee, myChan, category, checkup);
	}
}

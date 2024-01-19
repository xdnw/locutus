package gg.jte.generated.ondemand.guild.ia;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
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
public final class JteauditentryGenerated {
	public static final String JTE_NAME = "guild/ia/auditentry.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,33,33,33,39,39,39,39,39,40,40,41,41,41,41,42,42,43,43,44,44,47,47,48,48,48,33,34,35,36,36,36,36};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, IACheckup.AuditType type, DBNation nation, String message) {
		jteOutput.writeContent("\r\n<tr class=\"searchable\">\r\n    <td><a href=\"");
		jteOutput.writeUserContent(nation.getNationUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(nation.getNation());
		jteOutput.writeContent("</a></td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getCities());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getOff());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(nation.getDef());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getMMR());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getMMRBuildingStr());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getAvg_infra());
		jteOutput.writeContent("</td>\r\n</tr>\r\n<tr>\r\n    <td colspan=\"100\" style=\"word-break: break-all;\">");
		jteOutput.writeUserContent(message);
		jteOutput.writeContent("</td>\r\n</tr>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		IACheckup.AuditType type = (IACheckup.AuditType)params.get("type");
		DBNation nation = (DBNation)params.get("nation");
		String message = (String)params.get("message");
		render(jteOutput, jteHtmlInterceptor, ws, type, nation, message);
	}
}

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
public final class JteauditlistGenerated {
	public static final String JTE_NAME = "guild/ia/auditlist.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,33,33,33,37,37,37,39,39,40,40,40,40,40,40,41,41,41,41,44,44,44,44,44,44,56,56,57,57,58,58,65,65,65,33,34,35,35,35,35};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, IACheckup.AuditType type, Map<DBNation, String> nationMap) {
		jteOutput.writeContent("\r\n<div class=\"accordion rounded shadow searchable\" id=\"Accordion");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\">\r\n    <div class=\"accordion-item\">\r\n        <h3 class=\"accordion-header\" id=\"heading");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\">\r\n            <button class=\"accordion-button collapsed p-1 btn-lg bg-");
		jteOutput.writeUserContent(type.severity.name().toLowerCase());
		jteOutput.writeContent(" text-white\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapse");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapse");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\">\r\n                ");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent(" - (");
		jteOutput.writeUserContent(nationMap.size());
		jteOutput.writeContent(" nations)\r\n            </button>\r\n        </h3>\r\n        <div id=\"collapse");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"heading");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\" data-bs-parent=\"#Accordion");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\">\r\n            <div class=\"accordion-body bg-light\">\r\n                <table class=\"table\">\r\n                    <thead>\r\n                        <th>Nation</th>\r\n                        <th>Cities</th>\r\n                        <th>Off/Def</th>\r\n                        <th>mmr[unit]</th>\r\n                        <th>mmr[build]</th>\r\n                        <th>avg_infra</th>\r\n                    </thead>\r\n                    <tbody>\r\n                    ");
		for (Map.Entry<DBNation, String> entry : nationMap.entrySet()) {
			jteOutput.writeContent("\r\n                        ");
			gg.jte.generated.ondemand.guild.ia.JteauditentryGenerated.render(jteOutput, jteHtmlInterceptor, ws, type, entry.getKey(), entry.getValue());
			jteOutput.writeContent("\r\n                    ");
		}
		jteOutput.writeContent("\r\n                    </tbody>\r\n                </table>\r\n\r\n            </div>\r\n        </div>\r\n    </div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		IACheckup.AuditType type = (IACheckup.AuditType)params.get("type");
		Map<DBNation, String> nationMap = (Map<DBNation, String>)params.get("nationMap");
		render(jteOutput, jteHtmlInterceptor, ws, type, nationMap);
	}
}

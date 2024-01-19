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
public final class JteauditsGenerated {
	public static final String JTE_NAME = "guild/ia/audits.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,33,33,33,37,37,37,37,57,57,58,58,59,59,61,61,61,61,61,33,34,35,35,35,35};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Map<IACheckup.AuditType, Map<DBNation, String>> nationsByAudit) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<script>\r\n$(document).ready(function(){\r\n  $(\"#myInput\").on(\"keyup\", function() {\r\n    var value = $(this).val().toLowerCase();\r\n    $(\".searchable\").filter(function() {\r\n        var show = $(this).text().toLowerCase().indexOf(value) > -1;\r\n        $(this).toggle(show);\r\n        if (this.nodeName == \"TR\") {\r\n            $(this.nextElementSibling).toggle(show);\r\n        }\r\n    });\r\n  });\r\n});\r\n</script>\r\n<div class=\"container\">\r\n<h2 class=\"text-white\">Search</h2>\r\n<input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"nation..\">\r\n</div>\r\n<div class=\"container\">\r\n");
				for (Map.Entry<IACheckup.AuditType, Map<DBNation, String>> entry : nationsByAudit.entrySet()) {
					jteOutput.writeContent("\r\n    ");
					gg.jte.generated.ondemand.guild.ia.JteauditlistGenerated.render(jteOutput, jteHtmlInterceptor, ws, entry.getKey(), entry.getValue());
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n</div>\r\n");
			}
		}, "Alliance audit results", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Map<IACheckup.AuditType, Map<DBNation, String>> nationsByAudit = (Map<IACheckup.AuditType, Map<DBNation, String>>)params.get("nationsByAudit");
		render(jteOutput, jteHtmlInterceptor, ws, db, nationsByAudit);
	}
}

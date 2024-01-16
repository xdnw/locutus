package gg.jte.generated.ondemand.guild;
import java.util.*;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.Locutus;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import link.locutus.discord.util.PnwUtil;
import java.util.Collection;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.pnw.BeigeReason;
public final class JteoddsGenerated {
	public static final String JTE_NAME = "guild/odds.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,28,28,28,33,33,33,33,33,33,33,35,35,36,36,37,37,39,39,39,28,29,30,31,31,31,31};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, double attStr, double defStr) {
		jteOutput.writeContent("<div>\r\n<h5>Odds ");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent(":");
		jteOutput.writeUserContent(MathMan.format(attStr));
		jteOutput.writeContent(" vs ");
		jteOutput.writeUserContent(MathMan.format(defStr));
		jteOutput.writeContent("</h5>\r\n<div class=\"progress\">\r\n    ");
		for (int success = 0; success <= 3; success++) {
			jteOutput.writeContent("\r\n    ");
			gg.jte.generated.ondemand.guild.JteoddssuccessGenerated.render(jteOutput, jteHtmlInterceptor, ws, (PnwUtil.getOdds(attStr, defStr, success) * 100), success);
			jteOutput.writeContent("\r\n    ");
		}
		jteOutput.writeContent("\r\n</div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		double attStr = (double)params.get("attStr");
		double defStr = (double)params.get("defStr");
		render(jteOutput, jteHtmlInterceptor, ws, title, attStr, defStr);
	}
}

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
import link.locutus.discord.db.entities.InterviewMessage;
import net.dv8tion.jda.api.entities.User;
public final class JtemessageGenerated {
	public static final String JTE_NAME = "guild/ia/message.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,35,35,35,47,47,47,52,52,54,54,58,58,58,58,58,58,61,61,63,63,63,35,36,37,38,39,40,40,40,40};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Guild guild, GuildMessageChannel channel, User interviewUser, InterviewMessage message, boolean isMyChannel) {
		jteOutput.writeContent("\r\n<div class=\"card mb-4\">\r\n    <div class=\"card-body\">\r\n        <div class=\"d-flex justify-content-between\">\r\n            <div class=\"d-flex flex-row align-items-center\">\r\n                <img\r\n                        src=\"");
		jteOutput.writeUserContent(message.getAvatar());
		jteOutput.writeContent("\"\r\n                        alt=\"avatar\"\r\n                        width=\"25\"\r\n                        height=\"25\"\r\n                />\r\n                <p class=\"small mb-0 ms-2 me-2\">");
		jteOutput.writeUserContent(message.getUsername());
		jteOutput.writeContent("</p>\r\n                <i class=\"bi bi-dot\"></i>\r\n                <p class=\"small text-muted mb-0 ms-2 format-date\">");
		jteOutput.writeUserContent(message.date_created);
		jteOutput.writeContent("</p>\r\n\r\n            </div>\r\n            <div class=\"d-flex flex-row align-items-center\">\r\n                <p class=\"small text-muted mb-0\"><a href=\"discord://discord.com/channels/");
		jteOutput.writeUserContent(guild.getIdLong());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(channel.getIdLong());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(message.message_id);
		jteOutput.writeContent("\"><kbd>Jump</kbd></a></p>\r\n            </div>\r\n        </div>\r\n        <p>");
		jteOutput.writeUserContent(message.message);
		jteOutput.writeContent("</p>\r\n    </div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Guild guild = (Guild)params.get("guild");
		GuildMessageChannel channel = (GuildMessageChannel)params.get("channel");
		User interviewUser = (User)params.get("interviewUser");
		InterviewMessage message = (InterviewMessage)params.get("message");
		boolean isMyChannel = (boolean)params.get("isMyChannel");
		render(jteOutput, jteHtmlInterceptor, ws, guild, channel, interviewUser, message, isMyChannel);
	}
}

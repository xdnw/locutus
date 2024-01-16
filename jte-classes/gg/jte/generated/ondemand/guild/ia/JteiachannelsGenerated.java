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
import link.locutus.discord.db.entities.InterviewMessage;
import net.dv8tion.jda.api.entities.User;
import com.google.gson.JsonElement;
import rocker.guild.ia.message;
public final class JteiachannelsGenerated {
	public static final String JTE_NAME = "guild/ia/iachannels.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,38,38,38,53,53,53,53,57,57,58,58,68,68,69,69,71,71,72,72,73,73,89,89,94,94,99,99,101,101,105,105,108,108,110,110,123,123,123,123,123,38,39,40,41,42,43,44,45,46,47,48,49,50,51,51,51,51};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, DBNation me, User author, IACategory iaCat, List<IACategory.SortedCategory> categories, Map<GuildMessageChannel, IACategory.SortedCategory> categoryMap, Map<IACategory.SortedCategory, List<GuildMessageChannel>> channelsByCategory, Map<GuildMessageChannel, DBNation> interviewNation, Map<GuildMessageChannel, User> interviewUsers, JsonElement avatarsJson, JsonElement usersJson, JsonElement messagesJson, Set<GuildMessageChannel> myChannels) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    <div class=\"container\">\r\n    <div class=\"bg-white mt-3 rounded shadow py-1 row\">\r\n        <div class=\"col-auto\">\r\n            <button type=\"button\" cmd=\"");
				jteOutput.writeUserContent(CM.interview.sortInterviews.cmd.create(null).toSlashCommand(false));
				jteOutput.writeContent("\" refresh=\"1\" class=\"btn btn-primary\">Sort channels</button>\r\n            <button type=\"button\" cmd=\"");
				jteOutput.writeUserContent(CM.interview.syncInterviews.cmd.create().toSlashCommand(false));
				jteOutput.writeContent("\" refresh=\"1\" class=\"btn btn-primary\">List old channels</button>\r\n        </div>\r\n        <div class=\"col\">\r\n            <input class=\"form-control form-control-sm d-inline\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n        </div>\r\n    </div>\r\n    </div>\r\n    </div>\r\n    <br>\r\n<script>\r\nvar avatars = ");
				jteOutput.writeUnsafeContent(avatarsJson.toString());
				jteOutput.writeContent(";\r\nvar usernames = ");
				jteOutput.writeUnsafeContent(usersJson.toString());
				jteOutput.writeContent(";\r\n// message id, user id, date, content\r\nvar messages = ");
				jteOutput.writeUnsafeContent(messagesJson.toString());
				jteOutput.writeContent(";\r\nvar guildId = \"");
				jteOutput.writeUserContent(db.getIdLong());
				jteOutput.writeContent("\";\r\nvar avatarUrl = \"");
				jteOutput.writeUserContent(String.format(User.AVATAR_URL, "{0}", "{1}", "{2}"));
				jteOutput.writeContent("\";\r\n\r\nfunction getAvatarUrl(userId, avatarId) {\r\n    return avatarUrl.format(userId, avatarId, avatarId.startsWith(\"a_\") ? \"gif\" : \"png\");\r\n}\r\n\r\nfunction msgTemplate(channelId, messageEntry) {\r\n    var messageId = messageEntry[0];\r\n    var userId = messageEntry[1];\r\n    var dateLong = messageEntry[2];\r\n    var message = messageEntry[3];\r\n    var dateStr = new Date(parseInt(dateLong));\r\n    var avatarId = avatars[userId];\r\n    var avatar = getAvatarUrl(userId, avatarId);\r\n    var username = usernames[userId];\r\n\r\n    return ");
				jteOutput.writeUserContent("&#96;");
				jteOutput.writeContent("<div class=\"card mb-4\">\r\n        <div class=\"card-body\">\r\n            <div class=\"d-flex justify-content-between\">\r\n                <div class=\"d-flex flex-row align-items-center\">\r\n                    <img\r\n                            src=\"");
				jteOutput.writeUserContent("${avatar}");
				jteOutput.writeContent("\"\r\n                            alt=\"avatar\"\r\n                            width=\"25\"\r\n                            height=\"25\"\r\n                    />\r\n                    <p class=\"small mb-0 ms-2 me-2\">");
				jteOutput.writeUserContent("${username}");
				jteOutput.writeContent("</p>\r\n                    <i class=\"bi bi-dot\"></i>\r\n                    <p class=\"small text-muted mb-0 ms-2 format-date\">");
				jteOutput.writeUserContent("${dateStr}");
				jteOutput.writeContent("</p>\r\n\r\n                </div>\r\n                <div class=\"d-flex flex-row align-items-center\">\r\n                    <p class=\"small text-muted mb-0\"><a href=\"discord://discord.com/channels/");
				jteOutput.writeUserContent("${guildId}/${channelId}/${messageId}");
				jteOutput.writeContent("\"><kbd>Jump</kbd></a></p>\r\n                </div>\r\n            </div>\r\n            <p>");
				jteOutput.writeUserContent("${message}");
				jteOutput.writeContent("</p>\r\n        </div>\r\n    </div>");
				jteOutput.writeUserContent("&#96;");
				jteOutput.writeContent("\r\n}\r\n</script>\r\n\r\n<div id=\"test\">\r\n</div>\r\n\r\n<script>\r\nvar channelId = Object.keys(messages)[0];\r\n    var chanMsgs = messages[channelId];\r\n    var msg = chanMsgs[Object.keys(chanMsgs)[1]]\r\n    document.getElementById(\"test\").innerHTML = msgTemplate(channelId, msg);\r\n</script>\r\n");
			}
		}, "Alliance interview channels", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		DBNation me = (DBNation)params.get("me");
		User author = (User)params.get("author");
		IACategory iaCat = (IACategory)params.get("iaCat");
		List<IACategory.SortedCategory> categories = (List<IACategory.SortedCategory>)params.get("categories");
		Map<GuildMessageChannel, IACategory.SortedCategory> categoryMap = (Map<GuildMessageChannel, IACategory.SortedCategory>)params.get("categoryMap");
		Map<IACategory.SortedCategory, List<GuildMessageChannel>> channelsByCategory = (Map<IACategory.SortedCategory, List<GuildMessageChannel>>)params.get("channelsByCategory");
		Map<GuildMessageChannel, DBNation> interviewNation = (Map<GuildMessageChannel, DBNation>)params.get("interviewNation");
		Map<GuildMessageChannel, User> interviewUsers = (Map<GuildMessageChannel, User>)params.get("interviewUsers");
		JsonElement avatarsJson = (JsonElement)params.get("avatarsJson");
		JsonElement usersJson = (JsonElement)params.get("usersJson");
		JsonElement messagesJson = (JsonElement)params.get("messagesJson");
		Set<GuildMessageChannel> myChannels = (Set<GuildMessageChannel>)params.get("myChannels");
		render(jteOutput, jteHtmlInterceptor, ws, db, me, author, iaCat, categories, categoryMap, channelsByCategory, interviewNation, interviewUsers, avatarsJson, usersJson, messagesJson, myChannels);
	}
}

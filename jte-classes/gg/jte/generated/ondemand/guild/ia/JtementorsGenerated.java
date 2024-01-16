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
public final class JtementorsGenerated {
	public static final String JTE_NAME = "guild/ia/mentors.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,34,34,34,51,51,51,51,55,55,56,56,58,61,63,63,64,64,65,65,76,76,78,78,78,78,79,79,79,79,80,80,81,81,83,83,87,87,88,88,101,101,103,103,103,103,104,104,105,105,106,106,107,107,107,107,108,108,110,110,113,113,116,116,120,120,121,121,133,133,135,135,135,135,136,136,137,137,138,138,139,139,139,139,140,140,142,142,145,145,149,149,150,150,162,162,164,164,164,164,165,165,166,166,167,167,168,168,168,168,169,169,171,171,174,174,178,178,179,179,189,189,191,191,191,191,192,192,193,193,195,195,199,199,200,200,200,202,202,202,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,49,49,49};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, IACategory iaCat, GuildDB db, List<Map.Entry<DBNation, List<DBNation>>> mentorsSorted, Map<DBNation, IACategory.AssignedMentor> menteeMentorMap, Map<DBNation, IACategory.SortedCategory> categoryMap, Map<DBNation, Boolean> passedMap, Map<Integer, Long> lastMentorTxByNationId, List<DBNation> mentorsWithRole, Map<DBNation, Integer> numPassedMap, List<DBNation> membersUnverified, List<DBNation> membersNotOnDiscord, List<DBNation> nationsNoIAChan, List<DBNation> noMentor, List<DBNation> idleMentors, IACheckup checkup) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n        <div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n            <div class=\"\"></div>\r\n        </div>\r\n");
				for (Map.Entry<DBNation, List<DBNation>> entry : mentorsSorted) {
					jteOutput.writeContent("\r\n    ");
					if (!entry.getValue().isEmpty()) {
						jteOutput.writeContent("\r\n        <div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n            ");
						gg.jte.generated.ondemand.guild.ia.JtementorGenerated.render(jteOutput, jteHtmlInterceptor, ws, entry.getKey(), entry.getValue(), iaCat, db, categoryMap, passedMap, lastMentorTxByNationId, mentorsWithRole, numPassedMap, checkup);
						jteOutput.writeContent("\r\n        </div>\r\n    ");
					}
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (!idleMentors.isEmpty()) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2>Idle Mentors</h2>\r\n    <table>\r\n        <thead>\r\n        <th>Nation</th>\r\n        <th>User</th>\r\n        <th>City</th>\r\n        <th>Active</th>\r\n        </thead>\r\n        <tbody>\r\n        ");
					for (DBNation nation : idleMentors) {
						jteOutput.writeContent("\r\n        <tr>\r\n            <td><a href=\"");
						jteOutput.writeUserContent(nation.getNationUrl());
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(nation.getNation());
						jteOutput.writeContent("</a></td>\r\n            <td><a href=\"discord://discordapp.com/users/");
						jteOutput.writeUserContent(nation.getUserId());
						jteOutput.writeContent("\">@");
						jteOutput.writeUserContent(nation.getUserDiscriminator());
						jteOutput.writeContent("</a></td>\r\n            <td>");
						jteOutput.writeUserContent(nation.getCities());
						jteOutput.writeContent("</td>\r\n            <td>");
						jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
						jteOutput.writeContent("</td>\r\n        </tr>\r\n        ");
					}
					jteOutput.writeContent("\r\n        </tbody>\r\n    </table>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (!noMentor.isEmpty()) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2>Members Lacking Mentor</h2>\r\n    <table>\r\n        <thead>\r\n        <th>Nation</th>\r\n        <th>City</th>\r\n        <th>Active</th>\r\n        <th>Channel</th>\r\n        <th>Category</th>\r\n        <th>Action</th>\r\n        </thead>\r\n        <tbody>\r\n        ");
					for (DBNation nation : noMentor) {
						jteOutput.writeContent("\r\n        <tr>\r\n            <td><a href=\"");
						jteOutput.writeUserContent(nation.getNationUrl());
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(nation.getNation());
						jteOutput.writeContent("</a></td>\r\n            <td>");
						jteOutput.writeUserContent(nation.getCities());
						jteOutput.writeContent("</td>\r\n            <td>");
						jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
						jteOutput.writeContent("</td>\r\n            <td>");
						if (iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null) {
							jteOutput.writeContent("\r\n                <a href=\"");
							jteOutput.writeUserContent(DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel()));
							jteOutput.writeContent("\">#");
							jteOutput.writeUserContent(iaCat.get(nation).getChannel().getName());
							jteOutput.writeContent("</a>\r\n                ");
						}
						jteOutput.writeContent("</td>\r\n            <td>\r\n                ");
						jteOutput.writeUserContent(categoryMap.get(nation));
						jteOutput.writeContent("\r\n            </td>\r\n            <td>\r\n                <button class=\"btn btn-sm btn-primary\" cmd=\"");
						jteOutput.writeUserContent(CM.interview.mentee.cmd.create(nation.getNation_id() + "", "true").toSlashCommand(false));
						jteOutput.writeContent("\">Assign Self</button>\r\n            </td>\r\n        </tr>\r\n        ");
					}
					jteOutput.writeContent("\r\n        </tbody>\r\n    </table>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (!membersNotOnDiscord.isEmpty()) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2>Members not in Guild</h2>\r\n    <table>\r\n        <thead>\r\n        <th>Nation</th>\r\n        <th>City</th>\r\n        <th>Active</th>\r\n        <th>Channel</th>\r\n        <th>Category</th>\r\n        </thead>\r\n        <tbody>\r\n        ");
					for (DBNation nation : membersNotOnDiscord) {
						jteOutput.writeContent("\r\n        <tr>\r\n            <td><a href=\"");
						jteOutput.writeUserContent(nation.getNationUrl());
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(nation.getNation());
						jteOutput.writeContent("</a></td>\r\n            <td>");
						jteOutput.writeUserContent(nation.getCities());
						jteOutput.writeContent("</td>\r\n            <td>");
						jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
						jteOutput.writeContent("</td>\r\n            <td>");
						if (iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null) {
							jteOutput.writeContent("\r\n                <a href=\"");
							jteOutput.writeUserContent(DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel()));
							jteOutput.writeContent("\">#");
							jteOutput.writeUserContent(iaCat.get(nation).getChannel().getName());
							jteOutput.writeContent("</a>\r\n                ");
						}
						jteOutput.writeContent("</td>\r\n            <td>\r\n                ");
						jteOutput.writeUserContent(categoryMap.get(nation));
						jteOutput.writeContent("\r\n            </td>\r\n        </tr>\r\n        ");
					}
					jteOutput.writeContent("\r\n        </tbody>\r\n    </table>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (!membersUnverified.isEmpty()) {
					jteOutput.writeContent("\r\n    <div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n        <h2>Unverified Members</h2>\r\n        <table>\r\n            <thead>\r\n                <th>Nation</th>\r\n                <th>City</th>\r\n                <th>Active</th>\r\n                <th>Channel</th>\r\n                <th>Category</th>\r\n            </thead>\r\n            <tbody>\r\n                ");
					for (DBNation nation : membersUnverified) {
						jteOutput.writeContent("\r\n                    <tr>\r\n                        <td><a href=\"");
						jteOutput.writeUserContent(nation.getNationUrl());
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(nation.getNation());
						jteOutput.writeContent("</a></td>\r\n                        <td>");
						jteOutput.writeUserContent(nation.getCities());
						jteOutput.writeContent("</td>\r\n                        <td>");
						jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
						jteOutput.writeContent("</td>\r\n                        <td>");
						if (iaCat.get(nation) != null && iaCat.get(nation).getChannel() != null) {
							jteOutput.writeContent("\r\n                            <a href=\"");
							jteOutput.writeUserContent(DiscordUtil.getChannelUrl(iaCat.get(nation).getChannel()));
							jteOutput.writeContent("\">#");
							jteOutput.writeUserContent(iaCat.get(nation).getChannel().getName());
							jteOutput.writeContent("</a>\r\n                        ");
						}
						jteOutput.writeContent("</td>\r\n                        <td>\r\n                            ");
						jteOutput.writeUserContent(categoryMap.get(nation));
						jteOutput.writeContent("\r\n                        </td>\r\n                    </tr>\r\n                ");
					}
					jteOutput.writeContent("\r\n            </tbody>\r\n        </table>\r\n    </div>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (!nationsNoIAChan.isEmpty()) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2>Members without channel</h2>\r\n    <table>\r\n        <thead>\r\n        <th>Nation</th>\r\n        <th>City</th>\r\n        <th>Active</th>\r\n        </thead>\r\n        <tbody>\r\n        ");
					for (DBNation nation : nationsNoIAChan) {
						jteOutput.writeContent("\r\n        <tr>\r\n            <td><a href=\"");
						jteOutput.writeUserContent(nation.getNationUrl());
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(nation.getNation());
						jteOutput.writeContent("</a></td>\r\n            <td>");
						jteOutput.writeUserContent(nation.getCities());
						jteOutput.writeContent("</td>\r\n            <td>");
						jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
						jteOutput.writeContent("</td>\r\n        </tr>\r\n        ");
					}
					jteOutput.writeContent("\r\n        </tbody>\r\n    </table>\r\n</div>\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Mentors", null);
		jteOutput.writeContent("\r\n\r\n");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		IACategory iaCat = (IACategory)params.get("iaCat");
		GuildDB db = (GuildDB)params.get("db");
		List<Map.Entry<DBNation, List<DBNation>>> mentorsSorted = (List<Map.Entry<DBNation, List<DBNation>>>)params.get("mentorsSorted");
		Map<DBNation, IACategory.AssignedMentor> menteeMentorMap = (Map<DBNation, IACategory.AssignedMentor>)params.get("menteeMentorMap");
		Map<DBNation, IACategory.SortedCategory> categoryMap = (Map<DBNation, IACategory.SortedCategory>)params.get("categoryMap");
		Map<DBNation, Boolean> passedMap = (Map<DBNation, Boolean>)params.get("passedMap");
		Map<Integer, Long> lastMentorTxByNationId = (Map<Integer, Long>)params.get("lastMentorTxByNationId");
		List<DBNation> mentorsWithRole = (List<DBNation>)params.get("mentorsWithRole");
		Map<DBNation, Integer> numPassedMap = (Map<DBNation, Integer>)params.get("numPassedMap");
		List<DBNation> membersUnverified = (List<DBNation>)params.get("membersUnverified");
		List<DBNation> membersNotOnDiscord = (List<DBNation>)params.get("membersNotOnDiscord");
		List<DBNation> nationsNoIAChan = (List<DBNation>)params.get("nationsNoIAChan");
		List<DBNation> noMentor = (List<DBNation>)params.get("noMentor");
		List<DBNation> idleMentors = (List<DBNation>)params.get("idleMentors");
		IACheckup checkup = (IACheckup)params.get("checkup");
		render(jteOutput, jteHtmlInterceptor, ws, iaCat, db, mentorsSorted, menteeMentorMap, categoryMap, passedMap, lastMentorTxByNationId, mentorsWithRole, numPassedMap, membersUnverified, membersNotOnDiscord, nationsNoIAChan, noMentor, idleMentors, checkup);
	}
}

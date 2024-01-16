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
public final class JtementorGenerated {
	public static final String JTE_NAME = "guild/ia/mentor.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,33,33,33,45,45,45,45,45,45,45,45,45,46,46,47,47,48,48,49,49,50,50,51,51,52,52,54,54,56,56,58,58,59,59,61,61,62,62,63,63,65,65,82,82,83,83,84,84,85,85,87,87,87,33,34,35,36,37,38,39,40,41,42,43,43,43,43};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, DBNation mentor, List<DBNation> myMentees, IACategory iaCat, GuildDB db, Map<DBNation, IACategory.SortedCategory> categoryMap, Map<DBNation, Boolean> passedMap, Map<Integer, Long> lastMentorTxByNationId, List<DBNation> mentorsWithRole, Map<DBNation, Integer> numPassedMap, IACheckup checkup2) {
		jteOutput.writeContent("\r\n<h2>Mentor: <a href=\"");
		jteOutput.writeUserContent(mentor.getNationUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(mentor.getNation());
		jteOutput.writeContent("</a> | <a href=\"discord://discordapp.com/users/");
		jteOutput.writeUserContent(mentor.getUserId());
		jteOutput.writeContent("\">@");
		jteOutput.writeUserContent(mentor.getUserDiscriminator());
		jteOutput.writeContent("</a></h2>\r\n");
		if (mentor.getActive_m() > 4880) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-danger p-1 m-1\"><b>Mentor is inactive: </b> ");
			jteOutput.writeUserContent(TimeUtil.minutesToTime(mentor.getActive_m()));
			jteOutput.writeContent("</div>\r\n");
		}
		jteOutput.writeContent("\r\n");
		if (mentor.getVm_turns() > 0) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-danger p-1 m-1\"><b>Mentor is VM: </b> ");
			jteOutput.writeUserContent(TimeUtil.turnsToTime(mentor.getVm_turns()));
			jteOutput.writeContent("</div>\r\n");
		}
		jteOutput.writeContent("\r\n");
		if (mentor.getUser() == null) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-danger p-1 m-1\"><b>Mentor is NOT verified</b></div>\r\n");
		} else if (!Roles.MEMBER.has(mentor.getUser(), db.getGuild())) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-danger p-1 m-1\"><b>Mentor is NOT a member</b></div>\r\n");
		} else if (!Roles.hasAny(mentor.getUser(), db.getGuild(), Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERVIEWER, Roles.MENTOR)) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-warning p-1 m-1\"><b>Mentor is NOT gov or staff</b>(see roles on discord)</div>\r\n");
		}
		jteOutput.writeContent("\r\n");
		if (lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L) == 0) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-danger p-1 m-1\"><b>Mentor has not mentored</b></div>\r\n");
		} else if (lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L) < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)) {
			jteOutput.writeContent("\r\n<div class=\"alert alert-warning p-1 m-1\"><b>Mentor has not mentored in: </b>");
			jteOutput.writeUserContent(TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lastMentorTxByNationId.getOrDefault(mentor.getNation_id(), 0L)));
			jteOutput.writeContent("</div>\r\n");
		}
		jteOutput.writeContent("\r\n<div class=\"alert alert-info p-1 m-1\">\r\n    <b>Graduated Mentees:</b> ");
		jteOutput.writeUserContent(numPassedMap.getOrDefault(mentor, 0));
		jteOutput.writeContent("\r\n</div>\r\n<table class=\"table\">\r\n    <thead>\r\n        <tr>\r\n            <th>Nation</th>\r\n            <th>City</th>\r\n            <th>MMR[unit]</th>\r\n            <th>MMR[build]</th>\r\n            <th>Off</th>\r\n            <th>Channel</th>\r\n            <th>Category</th>\r\n            <th>Action</th>\r\n<!--            <th>Audit</th>-->\r\n        </tr>\r\n    </thead>\r\n    <tbody>\r\n    ");
		for (DBNation myMentee : myMentees) {
			jteOutput.writeContent("\r\n        ");
			var checkupResult = checkup2 == null ? null : checkup2.checkupSafe(myMentee, true, true);
			jteOutput.writeContent("\r\n        ");
			gg.jte.generated.ondemand.guild.ia.JtementeeGenerated.render(jteOutput, jteHtmlInterceptor, ws, myMentee, iaCat.get(myMentee), categoryMap.get(myMentee), checkupResult);
			jteOutput.writeContent("\r\n    ");
		}
		jteOutput.writeContent("\r\n    </tbody>\r\n</table>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		DBNation mentor = (DBNation)params.get("mentor");
		List<DBNation> myMentees = (List<DBNation>)params.get("myMentees");
		IACategory iaCat = (IACategory)params.get("iaCat");
		GuildDB db = (GuildDB)params.get("db");
		Map<DBNation, IACategory.SortedCategory> categoryMap = (Map<DBNation, IACategory.SortedCategory>)params.get("categoryMap");
		Map<DBNation, Boolean> passedMap = (Map<DBNation, Boolean>)params.get("passedMap");
		Map<Integer, Long> lastMentorTxByNationId = (Map<Integer, Long>)params.get("lastMentorTxByNationId");
		List<DBNation> mentorsWithRole = (List<DBNation>)params.get("mentorsWithRole");
		Map<DBNation, Integer> numPassedMap = (Map<DBNation, Integer>)params.get("numPassedMap");
		IACheckup checkup2 = (IACheckup)params.get("checkup2");
		render(jteOutput, jteHtmlInterceptor, ws, mentor, myMentees, iaCat, db, categoryMap, passedMap, lastMentorTxByNationId, mentorsWithRole, numPassedMap, checkup2);
	}
}

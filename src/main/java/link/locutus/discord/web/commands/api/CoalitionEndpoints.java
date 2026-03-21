package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebCoalitions;
import link.locutus.discord.web.commands.binding.value_types.WebCoalitions.WebCoalition;
import link.locutus.discord.web.commands.binding.value_types.WebCoalitions.WebCoalitionMember;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CoalitionEndpoints {
	@Command(desc = "List the bot coalitions", viewable = true)
	@RolePermission(Roles.MEMBER)
	@ReturnType(WebCoalitions.class)
	public WebCoalitions list_coalitions(@Me GuildDB db, @Me @Default User user,
										 @Default String filter,
										 @Switch("d") boolean ignoreDeleted) {
		String normalizedFilter = normalizeFilter(filter);
		boolean canViewOffshore = user != null && Roles.FOREIGN_AFFAIRS.has(user, db.getGuild());

		List<String> coalitionNames = new ArrayList<>(db.getCoalitionNames());
		Collections.sort(coalitionNames);

		List<WebCoalition> coalitions = new ObjectArrayList<>();
		for (String coalitionName : coalitionNames) {
			if (Coalition.OFFSHORE.getNameLower().equalsIgnoreCase(coalitionName) && !canViewOffshore) {
				continue;
			}

			List<WebCoalitionMember> members = new ObjectArrayList<>();
			for (long allianceOrGuildId : db.getCoalitionRaw(coalitionName)) {
				WebCoalitionMember member = resolveMember(allianceOrGuildId, ignoreDeleted);
				if (member != null) {
					members.add(member);
				}
			}

			if (normalizedFilter != null && !coalitionName.toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
				members.removeIf(member -> !member.name.toLowerCase(Locale.ROOT).contains(normalizedFilter));
				if (members.isEmpty()) {
					continue;
				}
			}

			coalitions.add(new WebCoalition(coalitionName, members));
		}

		return new WebCoalitions(coalitions);
	}

	private static String normalizeFilter(String filter) {
		if (filter == null) {
			return null;
		}
		String normalized = filter.trim().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? null : normalized;
	}

	private static WebCoalitionMember resolveMember(long allianceOrGuildId, boolean ignoreDeleted) {
		if (allianceOrGuildId > Integer.MAX_VALUE) {
			GuildDB guildDb = Locutus.imp().getGuildDB(allianceOrGuildId);
			if (guildDb == null) {
				return ignoreDeleted ? null : new WebCoalitionMember(allianceOrGuildId, "guild:" + allianceOrGuildId, true);
			}
			return new WebCoalitionMember(allianceOrGuildId, guildDb.getGuild().toString(), false);
		}

		int allianceId = (int) allianceOrGuildId;
		String allianceName = Locutus.imp().getNationDB().getAllianceName(allianceId);
		boolean deleted = allianceName == null || DBAlliance.get(allianceId) == null;
		if (deleted) {
			return ignoreDeleted ? null : new WebCoalitionMember(allianceOrGuildId, "AA:" + allianceId, true);
		}

		return new WebCoalitionMember(allianceOrGuildId, allianceName, false);
	}
}
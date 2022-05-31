package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;

public class ClearRoles extends Command {
    public ClearRoles() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + "<ALLIANCE|UNREGISTERED>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) return usage();

        if (args.get(0).equalsIgnoreCase("UNUSED")) {
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(event.getGuild().getRoles());
            Guild guild = event.getGuild();
            for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                if (guild.getMembersWithRoles(entry.getValue()).isEmpty()) {
                    entry.getValue().delete().complete();
                }
            }
            return "Cleared unused AA roles!";
        } if (args.get(0).equalsIgnoreCase("ALLIANCE")) {
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(event.getGuild().getRoles());
            for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                entry.getValue().delete().complete();
            }
            return "Cleared all AA roles!";
        } else if (args.get(0).equalsIgnoreCase("UNREGISTERED")) {
            GuildDB db = Locutus.imp().getGuildDB(event);
            int aaId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

            Role memberRole = Roles.MEMBER.toRole(event.getGuild());

            StringBuilder response = new StringBuilder();

            for (Member member : event.getGuild().getMembers()) {
                DBNation nation = DiscordUtil.getNation(member.getIdLong());
                List<Role> roles = member.getRoles();
                if (roles.contains(memberRole)) {
                    if (nation == null || nation.getAlliance_id() != aaId) {
                        response.append("\nRemove member from " + member.getEffectiveName());
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, memberRole));
                    }
                }
            }
            response.append("\nDone!");
            return response.toString();
        } else {
            return usage();
        }
    }
}
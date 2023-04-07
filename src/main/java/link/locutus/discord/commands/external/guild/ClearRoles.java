package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        }
        if (args.get(0).equalsIgnoreCase("ALLIANCE")) {
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(event.getGuild().getRoles());
            for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                entry.getValue().delete().complete();
            }
            return "Cleared all AA roles!";
        } else if (args.get(0).equalsIgnoreCase("UNREGISTERED")) {
            GuildDB db = Locutus.imp().getGuildDB(event);
            Set<Integer> aaIds = db.getAllianceIds();

            Role memberRole = Roles.MEMBER.toRole(event.getGuild());

            StringBuilder response = new StringBuilder();

            for (Member member : event.getGuild().getMembers()) {
                DBNation nation = DiscordUtil.getNation(member.getIdLong());
                List<Role> roles = member.getRoles();
                if (roles.contains(memberRole)) {
<<<<<<< HEAD
                    if (nation == null || !aaIds.contains(nation.getAlliance_id())) {
                        response.append("\nRemove member from " + member.getEffectiveName());
=======
                    if (nation == null || nation.getAlliance_id() != aaId) {
                        response.append("\nRemove member from ").append(member.getEffectiveName());
>>>>>>> pr/15
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
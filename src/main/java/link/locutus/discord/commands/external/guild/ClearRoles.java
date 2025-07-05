package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.Future;

public class ClearRoles extends Command {
    public ClearRoles() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.role.clearAllianceRoles.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();

        List<Future<?>> tasks = new ArrayList<>();
        if (args.get(0).equalsIgnoreCase("UNUSED")) {
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            
            for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                if (guild.getMembersWithRoles(entry.getValue()).isEmpty()) {
                    tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
                }
            }
            // complete tasks
            for (Future<?> task : tasks) {
                task.get();
            }
            return "Cleared unused AA roles!";
        }
        if (args.get(0).equalsIgnoreCase("ALLIANCE")) {
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            return "Cleared all AA roles!";
        } else if (args.get(0).equalsIgnoreCase("UNREGISTERED")) {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            Set<Integer> aaIds = db.getAllianceIds();

            Map<Long, Role> memberRoles = Roles.MEMBER.toRoleMap(db);
            if (memberRoles.isEmpty()) {
                return "No member role found!";
            }

            StringBuilder response = new StringBuilder();

            for (Member member : guild.getMembers()) {
                DBNation nation = DiscordUtil.getNation(member.getIdLong());
                Set<Role> roles = member.getUnsortedRoles();
                if (nation == null || !aaIds.contains(nation.getAlliance_id())) {
                    for (Role role : memberRoles.values()) {
                        if (roles.contains(role)) {
                            response.append("\nRemove member from " + member.getEffectiveName());
                            RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        }
                    }

                } else {
                    Map<Role, Set<Long>> inverse = new HashMap<>();
                    for (Map.Entry<Long, Role> entry : memberRoles.entrySet()) {
                        inverse.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
                    }
                    for (Role role : roles) {
                        Set<Long> allowedAAIds = inverse.get(role);
                        if (allowedAAIds != null && !allowedAAIds.contains((long) 0L) && !allowedAAIds.contains((long) nation.getAlliance_id())) {
                            response.append("\nRemove member from " + member.getEffectiveName());
                            RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        }
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
package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
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
import java.util.Set;

public class Mask extends Command {
    public Mask() {
        super(CommandCategory.ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <nations> <role> <true|false>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(event);

        Member authMem = guild.getMember(author);
        Role role = DiscordUtil.getRole(guild, args.get(1));
        boolean value = args.get(2).toLowerCase().startsWith("t") || args.get(2).equals("1");

        StringBuilder response = new StringBuilder();
        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations.isEmpty()) return "Unknown nation: `" + args.get(0) + "`";
        for (DBNation nation : nations) {
            User user = nation == null ? null : nation.getUser();
//            if (user == null) {
//                List<Member> mem1 = guild.getMembersByEffectiveName(args.get(0), true);
//                if (mem1.isEmpty()) mem1 = guild.getMembersByName(args.get(0), true);
//                if (mem1.size() == 1) user = mem1.get(0).getUser();
//                else {
//                    response.append("\nInvalid user: `" + args.get(0) + "`");
//                    continue;
//                }
//            }
            if (user == null) {
                response.append("\nInvalid user: " + nation.getNation());
                continue;
            }
            Member member = guild.getMember(user);
            if (member == null) {
                response.append("\nInvalid member: " + nation.getNation());
                continue;
            }
            if (role == null) {
                response.append("\nInvalid role: `" + args.get(1) + "`");
                continue;
            }

            List<Role> roles = member.getRoles();
            if (value && roles.contains(role)) {
                response.append("\nYou already have the role: `" + args.get(1) + "`");
                continue;
            } else if (!value && !roles.contains(role)) {
                response.append("\nYou do not have the role: `" + args.get(1) + "`");
                continue;
            }
            if (value) {
                RateLimitUtil.queue(guild.addRoleToMember(member, role));
                response.append("\nAdded role to member");
            } else {
                RateLimitUtil.queue(guild.removeRoleFromMember(member, role));
                response.append("\nRemoved role from member");
            }
        }
        response.append("\nDone!");
        return response.toString().trim();
    }
}

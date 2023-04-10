package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HasRole extends Command {
    public HasRole() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.USER_INFO);
    }

    @Override
    public String usage() {
        return super.usage() + " @user <role>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        List<User> users = event.getMessage().getMentionedUsers();
        if (args.size() != 2 || users.size() != 1) return usage(event);
        User user = users.get(0);
        Roles role = Roles.valueOf(args.get(1).toUpperCase());

        List<Long> guildIds = new ArrayList<>();
        GuildShardManager api = Locutus.imp().getDiscordApi();
        for (Guild other : api.getGuilds()) {
            Member member = other.getMember(user);
            if (member == null) continue;

            List<Role> roles = member.getRoles();
            if (roles.contains(role)) {
                guildIds.add(other.getIdLong());
            }
        }

        if (guildIds.isEmpty()) return "User does not have that role in any server.";

        return user.getName() + " has " + role.name() + " on " + StringMan.getString(guild);
    }
}

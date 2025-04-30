package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.role.hasRole.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(args.size(), 2, channel);
        User user = DiscordBindings.user(author, args.get(0));
        Roles role = Roles.valueOf(args.get(1).toUpperCase());

        List<Long> guildIds = new ArrayList<>();
        GuildShardManager api = Locutus.imp().getDiscordApi();
        for (Guild other : api.getGuilds()) {
            Member member = other.getMember(user);
            if (member == null) continue;
            if (role.has(user, guild)) {
                guildIds.add(other.getIdLong());
            }
        }

        if (guildIds.isEmpty()) return "User does not have that role in any server.";

        return user.getName() + " has " + role.name() + " on " + StringMan.getString(guild);
    }
}

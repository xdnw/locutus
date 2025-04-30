package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class GuildInfo extends Command {
    public GuildInfo() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.debug.guild.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String usage() {
        return super.usage() + " <guild-id>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) args = List.of(guild.getId());
        long id = Long.parseLong(args.get(0));

        guild = Locutus.imp().getDiscordApi().getGuildById(id);
        if (guild == null) return "Guild not found.";

        return guild.getName() + "/" + guild.getIdLong() + "\n" +
                "Owner: " + guild.getOwner() + "\n" +
                "Members: " + StringMan.getString(guild.getMembers());
    }
}

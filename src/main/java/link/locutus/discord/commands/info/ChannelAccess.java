package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class ChannelAccess extends Command {

    @Override
    public String help() {
        return super.help() + " <user>";
    }

    @Override
    public String desc() {
        return "Get a list of channels a user has access to.";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        User user = DiscordUtil.getUser(args.get(0));
        if (user == null) return "Invalid usage `" + args.get(0) + "`";
        return super.onCommand(event, guild, author, me, args, flags);
    }
}

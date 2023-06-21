package link.locutus.discord.commands.account;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class Say extends Command {
    public Say() {
        super(CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return null;
        }
        String msg = DiscordUtil.trimContent(fullCommandRaw);
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");
        return DiscordUtil.format(guild, channel, author, me, msg.substring(5) + "\n\n- " + author.getAsMention());
    }
}

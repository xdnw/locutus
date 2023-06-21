package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class PendingCommand extends Command {
    public PendingCommand() {
        super("pending", CommandCategory.DEBUG);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "pending <command>";
    }

    @Override
    public String desc() {
        return "Pend a command.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);

        String title = args.get(0);
        String raw = DiscordUtil.trimContent(fullCommandRaw);
        String cmd = raw.substring(raw.indexOf(' ', raw.indexOf(title) + title.length()) + 1);

        String emoji = "Approve";
        DiscordUtil.createEmbedCommand(channel, embed -> {
            embed.setTitle(title);
            embed.setDescription(cmd + "\n\nPlease Click " + emoji + " after you have fullfilled this request.");
        }, emoji, cmd);

        return null;
    }
}

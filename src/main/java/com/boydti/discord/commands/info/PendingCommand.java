package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.StringMan;
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
        return "!pending <command>";
    }

    @Override
    public String desc() {
        return "Pend a command";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) return usage(event);

        String title = args.get(0);
        String raw = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        String cmd = raw.substring(raw.indexOf(' ', raw.indexOf(title) + title.length()) + 1);

        DiscordUtil.createEmbedCommand(event.getChannel(), embed -> {
            embed.setTitle(title);
            embed.setDescription(cmd + "\n\nPlease Click \u2705 after you have fullfilled this request.");
        }, "\u2705", cmd);

        return null;
    }
}
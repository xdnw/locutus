package com.boydti.discord.commands.account;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.CommandManager;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return null;
        }
        String msg = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");
        return DiscordUtil.format( guild, event.getChannel(), author, me, msg.substring(5) + "\n\n - " + author.getAsMention());
    }
}

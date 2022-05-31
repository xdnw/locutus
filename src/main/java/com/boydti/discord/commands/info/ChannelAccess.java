package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
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
        return "Get a list of channels a user has access to";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        User user = DiscordUtil.getUser(args.get(0));
        if (user == null) return "Invalid usage `" + args.get(0) + "`";
        for (GuildChannel channel : guild.getChannels()) {

        }

        return super.onCommand(event, guild, author, me, args, flags);
    }
}

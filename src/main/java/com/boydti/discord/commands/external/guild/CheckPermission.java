package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class CheckPermission extends Command {
    public CheckPermission() {
        super(CommandCategory.GUILD_MANAGEMENT, CommandCategory.USER_INFO);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "!checkpermission <command> <user>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 2) return usage();
        List<User> mentions = event.getMessage().getMentionedUsers();
        boolean result = Locutus.imp().getCommandManager().getCommandMap().get(args.get(0)).checkPermission(DiscordUtil.getDefaultGuild(event), mentions.get(0));
        return "" + result;
    }
}

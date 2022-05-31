package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.Noformat;
import com.boydti.discord.commands.manager.dummy.DelegateMessage;
import com.boydti.discord.commands.manager.dummy.DelegateMessageEvent;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import java.util.List;

public class Runall extends Command implements Noformat {

    public Runall() {
        super(CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + "<command>";
    }

    @Override
    public String desc() {
        return "Run multiple commands at a time (put each new command on a new line)";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String msg = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        msg = msg.replaceAll("!runall !", "");
        String[] split = msg.split("\\r?\\n!");
        for (int i = 0; i < split.length; i++) {
            String cmd = "!" + split[i];
            if (cmd.toLowerCase().startsWith("!runall")) continue;
            Message message = new DelegateMessage(event.getMessage()) {
                @Nonnull
                @Override
                public String getContentRaw() {
                    return cmd;
                }
            };
            MessageReceivedEvent finalEvent = new DelegateMessageEvent(event.isFromGuild() ? event.getGuild() : null, event.getResponseNumber(), message);
            Locutus.imp().getCommandManager().run(finalEvent, false, true);
        }
        return "Done!";
    }
}
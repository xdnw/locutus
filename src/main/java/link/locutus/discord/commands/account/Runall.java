package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Message;
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
        msg = msg.replaceAll(Settings.commandPrefix(true) + "runall " + Settings.commandPrefix(true), "");
        String[] split = msg.split("\\r?\\n" + Settings.commandPrefix(true));
        for (int i = 0; i < split.length; i++) {
            String cmd = Settings.commandPrefix(true) + split[i];
            if (cmd.toLowerCase().startsWith(Settings.commandPrefix(true) + "runall")) continue;
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
package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class SyncTrade extends Command {
    public SyncTrade() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        Locutus.imp().getTradeManager().updateTradeList(true);
        return "Done!";
    }
}
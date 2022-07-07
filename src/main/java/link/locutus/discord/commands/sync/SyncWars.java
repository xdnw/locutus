package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.event.Event;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class SyncWars extends Command {

    public SyncWars() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        Locutus.imp().getWarDb().updateAllWars(Event::post);
        return "Done!";
    }
}
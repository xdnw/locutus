package com.boydti.discord.commands.sync;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class SyncWars extends Command {

    public SyncWars() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        Locutus.imp().getWarDb().updateWars();
        return "Done!";
    }
}
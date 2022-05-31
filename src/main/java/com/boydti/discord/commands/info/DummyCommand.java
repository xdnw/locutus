package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class DummyCommand extends Command {
    @Override
    public String help() {
        return "Does nothing";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }
}

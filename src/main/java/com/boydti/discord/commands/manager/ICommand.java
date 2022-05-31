package com.boydti.discord.commands.manager;

import com.boydti.discord.pnw.DBNation;
import com.google.gson.JsonElement;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public interface ICommand {
    String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception;
}

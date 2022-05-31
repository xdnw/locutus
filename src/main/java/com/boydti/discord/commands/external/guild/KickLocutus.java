package com.boydti.discord.commands.external.guild;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class KickLocutus extends Command {
    public KickLocutus() {
        super(CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        event.getChannel().sendMessage("Goodbye.").complete();
        event.getGuild().leave().complete();
        return null;
    }
}

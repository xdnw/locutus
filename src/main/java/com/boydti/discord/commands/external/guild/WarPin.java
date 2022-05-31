package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.war.WarCategory;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class WarPin extends Command {
    public WarPin() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }
    @Override
    public String desc() {
        return "Update the pin in the war room";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(event);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled";

        WarCategory.WarRoom waRoom = warChannels.getWarRoom(event.getGuildChannel());
        if (waRoom == null) return "This command must be run in a war room";

        Message message = waRoom.updatePin(true);
        return "Updated: " + message.getJumpUrl();
    }
}

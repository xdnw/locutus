package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class GuildInfo extends Command {
    public GuildInfo() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GUILD_MANAGEMENT);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String usage() {
        return super.usage() + " <guild-id>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        long id = Long.parseLong(args.get(0));

        guild = Locutus.imp().getDiscordApi().getGuildById(id);
        if (guild == null) return "Guild not found";

        String title = guild.getName() + "/" + guild.getIdLong();

        return title;
    }
}

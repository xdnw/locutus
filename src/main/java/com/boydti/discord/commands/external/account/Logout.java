package com.boydti.discord.commands.external.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class Logout extends Command {

    public Logout() {
        super(CommandCategory.USER_SETTINGS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (Locutus.imp().getDiscordDB().getUserPass(user.getIdLong()) != null);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (Locutus.imp().getDiscordDB().getUserPass(author.getIdLong()) != null) {
            Locutus.imp().getDiscordDB().logout(author.getIdLong());
            Auth cached = me.auth;
            if (cached != null) {
                cached.setValid(false);
            }
            me.auth = null;
            return "Logged out";
        }
        return "You are not logged in";
    }
}
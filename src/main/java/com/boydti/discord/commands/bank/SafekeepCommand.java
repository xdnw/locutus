package com.boydti.discord.commands.bank;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.task.war.GetActiveWars;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SafekeepCommand extends Command {
    public SafekeepCommand() {
        super("safekeep", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String help() {
        return super.help() + " <resources>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();
        return me.getAuth(null).safekeep(PnwUtil.parseResources(args.get(0))) + "\nDone!";
    }
}

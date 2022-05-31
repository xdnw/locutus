package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.war.SpyCommand;
import com.boydti.discord.db.DiscordDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;

public class MeCommand extends Command {
    private final Who who;

    public MeCommand(DiscordDB db, SpyCommand cmd) {
        super("me", CommandCategory.GAME_INFO_AND_TOOLS);
        this.who = new Who(cmd);
    }

    @Override
    public String help() {
        return "!me";
    }

    @Override
    public String desc() {
        return "Get pnw info about yourself";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Please use !validate";
        }
        return who.onCommand(event, Collections.singletonList(me.getNation_id() + ""));
    }
}

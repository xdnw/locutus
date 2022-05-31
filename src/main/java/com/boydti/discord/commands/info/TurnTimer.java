package com.boydti.discord.commands.info;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.apiv1.domains.Nation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class TurnTimer extends Command {
    public TurnTimer() {
        super("TurnTimer", "Timer", "CityTimer", "ProjectTimer", CommandCategory.ECON);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public String desc() {
        return "Check how many turns are left in the city/project timer";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";

        Nation pnwNation = Locutus.imp().getGuildDB(event).getApi().getNation(nation.getNation_id());
        nation.update(pnwNation);

        long cityTimer = nation.cityTimerTurns();
        long projectTimer = nation.projectTimerTurns();
        return "City: " + cityTimer + ", Project: " + projectTimer;
    }
}

package com.boydti.discord.commands.info;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class BeigeTurns extends Command {
    public BeigeTurns() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return "!" + getClass().getSimpleName() + " <nation>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }
        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "Invalid nation: " + nationId;
        }
        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        return nation.getBeigeTurns() + " turns";
    }
}

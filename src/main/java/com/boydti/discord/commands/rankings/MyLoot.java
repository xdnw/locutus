package com.boydti.discord.commands.rankings;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MyLoot extends Command {
    private final WarCostAB parent;

    public MyLoot(WarCostAB parent) {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
        this.parent = parent;
    }

    @Override
    public String help() {
        return super.help() + " <days>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1 || !MathMan.isInteger(args.get(0))) return usage(event);
        if (me == null) return "Please use `!validate`";
        return parent.onCommand(event, guild, author, me, new ArrayList<>(Arrays.asList(me.getNationUrl(), "*", args.get(0))), flags);
    }
}

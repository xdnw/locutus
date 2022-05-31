package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class LandCost extends Command {
    public LandCost() {
        super("LandCost", "land", "landcosts", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <current-land> <max-land> [rapid-expansion=false] [ALA=false] [AEC=false]";
    }

    @Override
    public String desc() {
        return "Calculate the costs of purchasing land (from current to max) e.g.\n" +
                "`!LandCost 250 1000`\n" +
                "Add e.g. `cities=5` to specify city count";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        int cities = 1;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next().toLowerCase();
            if (next.startsWith("cities=")) {
                cities = Integer.parseInt(next.split("=")[1]);
                iter.remove();
            }
        }
        if (args.size() < 2 || args.size() > 4) return usage(event);

        int current = checkNotNull(MathMan.parseInt(args.get(0)), "invalid amount: `" + args.get(0) + "`");
        int max = checkNotNull(MathMan.parseInt(args.get(1)), "invalid amount: `" + args.get(1) + "`");
        if (max > 20000) throw new IllegalArgumentException("Max land 20,000");

        double total = PnwUtil.calculateLand(current, max) * cities;

        boolean ra = false;
        boolean ala = false;
        boolean aec = false;

        if (args.size() >= 3) ra = Boolean.parseBoolean(args.get(2));
        if (args.size() >= 4) ala = Boolean.parseBoolean(args.get(3));
        if (args.size() >= 5) aec = Boolean.parseBoolean(args.get(4));

        double discountFactor = 1;
        if (ra) discountFactor -= 0.05;
        if (ala) discountFactor -= 0.05;
        if (aec) discountFactor -= 0.05;

        total = total * discountFactor;

        return "$" + MathMan.format(total);
    }
}
package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class CityCost extends Command {
    public CityCost() {
        super("citycost", "citycosts", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return super.help() + " <current-city> <max-city> [manifest-destiny=false] [city-planning=false] [advanced-city-planning=false]";
    }

    @Override
    public String desc() {
        return "Calculate the costs of purchasing cities (from current to max) e.g.\n" +
                "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "CityCost 5 10 true false false";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        int current = Integer.parseInt(args.get(0));
        int max = Integer.parseInt(args.get(1));
        if (max > 1000) throw new IllegalArgumentException("Max cities 1000");

        boolean manifest = false;
        boolean cp = false;
        boolean acp = false;

        if (args.size() >= 3) manifest = Boolean.parseBoolean(args.get(2));
        if (args.size() >= 4) cp = Boolean.parseBoolean(args.get(3));
        if (args.size() >= 5) acp = Boolean.parseBoolean(args.get(4));

        double total = 0;

        for (int i = Math.max(1, current); i < max; i++) {
            total += PnwUtil.nextCityCost(i, manifest, cp && i >= 11, acp && i >= 16);
        }

        return "$" + MathMan.format(total);
    }
}

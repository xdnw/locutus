package link.locutus.discord.commands.info;

import com.google.common.base.Preconditions;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class LandCost extends Command {
    public LandCost() {
        super("LandCost", "land", "landcosts", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.land.cost.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <current-land> <max-land> [rapid-expansion=false] [ALA=false] [AEC=false] [GSA=false]";
    }

    @Override
    public String desc() {
        return "Calculate the costs of purchasing land (from current to max) e.g.\n" +
                "`" + Settings.commandPrefix(true) + "LandCost 250 1000`\n" +
                "Add e.g. `cities=5` to specify city count";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        int cities = 1;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next().toLowerCase();
            if (next.startsWith("cities=")) {
                cities = Integer.parseInt(next.split("=")[1]);
                iter.remove();
            }
        }
        if (args.size() < 2 || args.size() > 6) return usage(args.size(), 2, 6, channel);

        int current = Preconditions.checkNotNull(MathMan.parseInt(args.get(0)), "invalid amount: `" + args.get(0) + "`");
        int max = checkNotNull(MathMan.parseInt(args.get(1)), "invalid amount: `" + args.get(1) + "`");
        if (max > 20000) throw new IllegalArgumentException("Max land 20,000.");

        double total = PW.City.Land.calculateLand(current, max) * cities;

        boolean ra = false;
        boolean ala = false;
        boolean aec = false;
        boolean gsa = false;

        if (args.size() >= 3) ra = Boolean.parseBoolean(args.get(2));
        if (args.size() >= 4) ala = Boolean.parseBoolean(args.get(3));
        if (args.size() >= 5) aec = Boolean.parseBoolean(args.get(4));
        if (args.size() >= 6) gsa = Boolean.parseBoolean(args.get(5));

        double discountFactor = 1;
        if (ra) {
            discountFactor -= 0.05;
            if (gsa) discountFactor -= 0.025;
        }
        if (ala) discountFactor -= 0.05;
        if (aec) discountFactor -= 0.05;



        total = total * discountFactor;

        return "$" + MathMan.format(total);
    }
}

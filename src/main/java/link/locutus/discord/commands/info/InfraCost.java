package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import com.google.common.base.Preconditions;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class InfraCost extends Command {
    public InfraCost() {
        super("InfraCost", "infrastructurecost", "infra", "infrastructure", "infracosts", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <current-infra> <max-infra> [urbanization=false] [CCE=false] [AEC=false]";
    }

    @Override
    public String desc() {
        return "Calculate the costs of purchasing infra (from current to max) e.g.\n" +
                "`!InfraCost 250 1000 true false`\n" +
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

        if (args.size() < 2) return usage(event);

        int current = Preconditions.checkNotNull(MathMan.parseInt(args.get(0)), "invalid amount: `" + args.get(0) + "`");
        int max = checkNotNull(MathMan.parseInt(args.get(1)), "invalid amount: `" + args.get(1) + "`");
        if (max > 20000) throw new IllegalArgumentException("Max infra 20,000");

        boolean urban = false;
        boolean cce = false;
        boolean aec = false;

        if (args.size() >= 3) urban = Boolean.parseBoolean(args.get(2));
        if (args.size() >= 4) cce = Boolean.parseBoolean(args.get(3));
        if (args.size() >= 5) aec = Boolean.parseBoolean(args.get(4));

        double total = 0;

        total = PnwUtil.calculateInfra(current, max);

        double discountFactor = 1;
        if (urban) discountFactor -= 0.05;
        if (cce) discountFactor -= 0.05;
        if (aec) discountFactor -= 0.05;

        total = total * discountFactor * cities;

        return "$" + MathMan.format(total);
    }
}

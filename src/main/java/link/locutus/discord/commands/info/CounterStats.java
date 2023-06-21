package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

public class CounterStats extends Command {
    public CounterStats() {
        super(CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <alliance-id>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        Integer id = PnwUtil.parseAllianceId(args.get(0));
        if (id == null) return "Invalid id: `" + id + "`";

        Set<Integer> ids = new HashSet<>(Collections.singleton(id));
        if (flags.contains('a')) {
            for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(id).entrySet()) {
                Treaty treaty = entry.getValue();
            }

        }
        List<Map.Entry<DBWar, CounterStat>> counters = Locutus.imp().getWarDb().getCounters(Collections.singleton(id));

        if (counters.isEmpty()) return "No data (to include treatied alliances, append `-a`)";

        int[] uncontested = new int[2];
        int[] countered = new int[2];
        int[] counter = new int[2];
        for (Map.Entry<DBWar, CounterStat> entry : counters) {
            CounterStat stat = entry.getValue();
            DBWar war = entry.getKey();
            switch (stat.type) {
                case ESCALATION, IS_COUNTER -> countered[stat.isActive ? 1 : 0]++;
                case UNCONTESTED -> {
                    if (war.status == WarStatus.ATTACKER_VICTORY) {
                        uncontested[stat.isActive ? 1 : 0]++;
                    } else {
                        counter[stat.isActive ? 1 : 0]++;
                    }
                }
                case GETS_COUNTERED -> counter[stat.isActive ? 1 : 0]++;
            }
        }

        int totalActive = counter[1] + uncontested[1];
        int totalInactive = counter[0] + uncontested[0];

        double chanceActive = ((double) counter[1] + 1) / (totalActive + 1);
        double chanceInactive = ((double) counter[0] + 1) / (totalInactive + 1);

        if (!Double.isFinite(chanceActive)) chanceActive = 0.5;
        if (!Double.isFinite(chanceInactive)) chanceInactive = 0.5;

        String title = "% of wars that are countered (" + PnwUtil.getName(id, true) + ")";
        String response = MathMan.format(chanceActive * 100) + "% for actives (" + totalActive + " wars)" + '\n' +
                MathMan.format(chanceInactive * 100) + "% for inactives (" + totalInactive + " wars)";

        DiscordUtil.createEmbedCommand(channel, title, response);
        return null;
    }
}

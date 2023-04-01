package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopAABeigeLoot extends Command {
    public TopAABeigeLoot() {
        super("TopAABeigeLoot", "bankbyloot", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <time>";
    }

    @Override
    public String desc() {
        return "Get the largest alliance bank loot per score\n" +
                "Add -t to just list total bank worth.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        String title = "AA loot/score";

        boolean total = flags.contains('t');
        if (total) title = "AA bank total";

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        Map<Integer, Double> lootPerScore = new HashMap<>();
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            LootEntry loot = alliance.getLoot();
            if (loot != null && loot.getDate() >= cutOff) {
                double perScore = loot.convertedTotal() / alliance.getScore();
                lootPerScore.put(alliance.getAlliance_id(), perScore);
            }
        }


        SummedMapRankBuilder<Integer, ? extends Number> sorted = new SummedMapRankBuilder<>(lootPerScore).sort();
        sorted.nameKeys(i -> PnwUtil.getName(i, true)).build(event, title);

        for (Integer integer : sorted.get().keySet()) {
            System.out.println(PnwUtil.getBBUrl(integer, true));
        }


        return null;
    }
}

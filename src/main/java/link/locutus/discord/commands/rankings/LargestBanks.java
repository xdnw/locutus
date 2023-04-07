package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LargestBanks extends Command {
    public LargestBanks() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <time>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) return usage(event);

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        Map<Integer, Double> total = new HashMap<>();

        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            LootEntry loot = alliance.getLoot();
            if (loot != null) {
                total.put(alliance.getAlliance_id(), loot.convertedTotal());
            }
        }

        SummedMapRankBuilder<Integer, ? extends Number> sorted = new SummedMapRankBuilder<>(total).sort();
        sorted.nameKeys(i -> PnwUtil.getName(i, true)).build(event, "AA bank");

        for (Integer integer : sorted.get().keySet()) {
            System.out.println(PnwUtil.getBBUrl(integer, true));
        }


        return null;
    }
}

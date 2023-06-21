package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GlobalTradeAverage extends Command {
    public GlobalTradeAverage() {
        super("GlobalTradeAverage", "gta", "tradeaverage", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return super.help() + " <time>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        long seconds = TimeUtil.timeToSec(args.get(0));
        if (seconds <= 0 || MathMan.isInteger(args.get(0))) return "Invalid amount of time: `" + args.get(0) + "`";

        long cutOff = System.currentTimeMillis() - seconds * 1000L;
        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(cutOff);

        Map<ResourceType, Double> lowMap = averages.getKey();
        Map<ResourceType, Double> highMap = averages.getValue();


        DiscordUtil.createEmbedCommand(channel, b -> {
            List<String> resourceNames = new ArrayList<>();
            List<String> low = new ArrayList<>();
            List<String> high = new ArrayList<>();

            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.MONEY) continue;

                resourceNames.add(MarkupUtil.markdownUrl(type.name().toLowerCase(), type.url(true, true)));

                int i = type.ordinal();

                double avgLow = lowMap.getOrDefault(type, 0d);
                low.add(MathMan.format(avgLow));

                double avgHigh = highMap.getOrDefault(type, 0d);
                high.add(MathMan.format(avgHigh));
            }

            b.addField("Resource", StringMan.join(resourceNames, "\n"), true);
            b.addField("Low", StringMan.join(low, "\n"), true);
            b.addField("High", StringMan.join(high, "\n"), true);


        }, "Refresh", DiscordUtil.trimContent(fullCommandRaw));

        return null;
    }
}

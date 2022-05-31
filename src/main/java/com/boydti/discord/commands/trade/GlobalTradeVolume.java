package com.boydti.discord.commands.trade;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.trade.TradeManager;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class GlobalTradeVolume extends Command {
    public GlobalTradeVolume() {
        super("GlobalTradeVolume", "gtv", "tradevolume", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        TradeManager trader = Locutus.imp().getTradeManager();
        String refreshEmoji = "\uD83D\uDD04";

        DiscordUtil.createEmbedCommand(event.getChannel(), b -> {
            List<String> resourceNames = new ArrayList<>();
            List<String> daily = new ArrayList<>();
            List<String> weekly = new ArrayList<>();

            for (ResourceType type : ResourceType.values()) {
                if (type.getGraphId() <= 0) continue;
                long[] volume = trader.getVolumeHistory(type);

                int i = volume.length - 1;
                double dailyChangePct = 100 * (volume[i] - volume[i - 2]) / (double) volume[i];

                long weeklyTotalChange = 0;
                for (int j = 0; j < 7; j++) {
                    weeklyTotalChange += volume[i - j] - volume[i - j - 1];
                }
                long averageWeeklyChange = weeklyTotalChange / 7;
                double weeklyChangePct = 100 * (averageWeeklyChange / (double) volume[i]);

                String name = type.name().toLowerCase();
                if (type == ResourceType.MUNITIONS) name = "\n" + name;
                resourceNames.add("[" + name + "](" + type.url(weeklyChangePct <= 0, true) + ")\n");

                String dayPrefix = (int) (dailyChangePct * 100) > 0 ? "+" : "";
                String weekPrefix = (int) (weeklyChangePct * 100) > 0 ? "+" : "";
                daily.add("```diff\n" + dayPrefix + MathMan.format(dailyChangePct) + "%```");
                weekly.add("```diff\n" + weekPrefix + MathMan.format(weeklyChangePct) + "%```");
            }

            b.addField("Resource", "\u200B\n" + StringMan.join(resourceNames, "\n"), true);
            b.addField("Daily", StringMan.join(daily, " "), true);
            b.addField("Weekly", StringMan.join(weekly, " "), true);
        }, refreshEmoji, DiscordUtil.trimContent(event.getMessage().getContentRaw()));

        return null;
    }
}
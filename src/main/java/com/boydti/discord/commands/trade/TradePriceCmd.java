package com.boydti.discord.commands.trade;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.commands.rankings.builder.SummedMapRankBuilder;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.NationDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.FileUtil;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.trade.Offer;
import com.boydti.discord.util.trade.TradeManager;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TradePriceCmd extends Command {
    public TradePriceCmd() {
        super("tradeprice", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " [days] [min-diff] [max-diff]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        TradeManager trader = Locutus.imp().getTradeManager();
        String refreshEmoji = "\uD83D\uDD04";

        Map<ResourceType, Double> low = trader.getLow().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        Map<ResourceType, Double> high = trader.getHigh().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));

        String lowKey;
        String highKey;

        if (!args.isEmpty()) {
            lowKey = "Buying";
            highKey = "Selling";

            long seconds = TimeUtil.timeToSec(args.get(0));
            if (seconds <= 0) return "Invalid amount of time: `" + args.get(0) + "`";

            long cutOff = System.currentTimeMillis() - seconds * 1000L;

            Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avg = trader.getAverage(cutOff);

            long weekCutoff = System.currentTimeMillis() - Math.max(TimeUnit.DAYS.toMillis(7), seconds * 1000L);
            Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avgWeek = trader.getAverage(weekCutoff);

            Double minDiff = 0d;
            Double maxDiff = 0d;
            if (args.size() == 3) {
                minDiff = MathMan.parseDouble(args.get(1));
                maxDiff = MathMan.parseDouble(args.get(2));
                if (minDiff == null || minDiff <= 0 || minDiff >= 100) return "Invalid percent: `" + args.get(1) + "`";
                if (maxDiff == null || maxDiff <= 0 || maxDiff >= 100) return "Invalid percent: `" + args.get(2) + "`";
            } else if (args.size() != 1) return usage(event);
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.MONEY || type == ResourceType.CREDITS) {
                    low.put(type, Double.NaN);
                    high.put(type, Double.NaN);
                    continue;
                }
                double lowAvgVal = avg.getKey().getOrDefault(type, 0d).doubleValue();
                double highAvgVal = avg.getValue().getOrDefault(type, 0d).doubleValue();
                double lowVal = low.getOrDefault(type, 0d);
                double highVal = high.getOrDefault(type, 0d);

                double avgDiff = highAvgVal - lowAvgVal;
                double newAvgLowVal = lowAvgVal + (avgDiff * minDiff / 100);
                double newAvgHighVal = highAvgVal - (avgDiff * maxDiff / 100);

                double currDiff = highVal - lowVal;
                double newLowVal = lowVal + (currDiff * minDiff / 100);
                double newHighVal = highVal - (currDiff * maxDiff / 100);

                newLowVal = Math.min(newLowVal, newHighVal);
                newHighVal = Math.min(newHighVal, highVal);

                if ((newLowVal + 1) * 1.02 >= (newHighVal - 1)) {
                    if (lowAvgVal < highAvgVal) {
                        newLowVal = Math.min(avgWeek.getKey().get(type), lowAvgVal);
                        if (type.isManufactured()) {
                            newHighVal = Math.max(avgWeek.getValue().get(type), highAvgVal);
                        } else {
                            newHighVal = Double.NaN;//Math.max(avgWeek.getValue().get(type), highAvgVal);
                        }
                    } else {
                        newLowVal = Double.NaN;
                        newHighVal = Double.NaN;
                    }
                }

                low.put(type, newLowVal);
                high.put(type, newHighVal);
            }
        } else {
            lowKey = "Low";
            highKey = "High";
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), b -> {
            List<ResourceType> resources = new ArrayList<>(ResourceType.valuesList);
            resources.remove(ResourceType.MONEY);

            List<String> resourceNames = resources.stream().map(r -> r.name().toLowerCase()).collect(Collectors.toList());

            ArrayList<String> lowList = new ArrayList<>();
            ArrayList<String> highList = new ArrayList<>();

            for (ResourceType type : resources) {
                Double o1 = low.get(type);
                Double o2 = high.get(type);

                lowList.add(o1 == null ? "" : MathMan.format(o1));
                highList.add(o2 == null ? "" : MathMan.format(o2));
            }

            b.addField("Resource", StringMan.join(resourceNames, "\n"), true);
            b.addField(lowKey, StringMan.join(lowList, "\n"), true);
            b.addField(highKey, StringMan.join(highList, "\n"), true);
        }, refreshEmoji, DiscordUtil.trimContent(event.getMessage().getContentRaw()));
        return null;
    }

    public void addFields(Map<ResourceType, Offer> map, EmbedBuilder b) {
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.MONEY) continue;

            StringBuilder body = new StringBuilder();
            Offer offer = map.get(type);
            if (offer != null) {
                body.append(toString(offer));
            }

            b.addField(type.name().toLowerCase(), body.toString(), true);
        }
    }

    public String toString(Offer offer) {
        int id = offer.getBuyer() == null ? offer.getSeller() : offer.getBuyer();
        String name = PnwUtil.getName(id, false);
        String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + id;
        return "$" + MathMan.format(offer.getPpu()) + "\n" + MathMan.format(offer.getAmount()) + "\n" + MarkupUtil.markdownUrl(name, url);
    }
}

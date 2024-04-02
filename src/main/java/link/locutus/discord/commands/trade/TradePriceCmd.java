package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        TradeManager trader = Locutus.imp().getTradeManager();
        String refreshEmoji = "Refresh";

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
            } else if (args.size() != 1) return usage(args.size(), 1, channel);
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

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Price")
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
                .addField(lowKey, StringMan.join(lowList, "\n"), true)
                .addField(highKey, StringMan.join(highList, "\n"), true)
                .build()
        ).commandButton(DiscordUtil.trimContent(fullCommandRaw), "Refresh").send();
        return null;
    }

    public void addFields(Map<ResourceType, DBTrade> map, EmbedBuilder b) {
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.MONEY) continue;

            StringBuilder body = new StringBuilder();
            DBTrade offer = map.get(type);
            if (offer != null) {
                body.append(toString(offer));
            }

            b.addField(type.name().toLowerCase(), body.toString(), true);
        }
    }

    public String toString(DBTrade offer) {
        int id = offer.getBuyer() == 0 ? offer.getSeller() : offer.getBuyer();
        String name = PW.getName(id, false);
        String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + id;
        return "$" + MathMan.format(offer.getPpu()) + "\n" + MathMan.format(offer.getQuantity()) + "\n" + MarkupUtil.markdownUrl(name, url);
    }
}

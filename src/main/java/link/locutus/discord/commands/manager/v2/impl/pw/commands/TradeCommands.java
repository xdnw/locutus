package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.rankings.table.TimeDualNumericTable;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.commands.trade.TradeRanking;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.Transfer;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.trade.TradeManager;
import com.google.common.collect.Maps;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.json.JSONObject;
import rocker.guild.ia.message;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TradeCommands {
    @Command(aliases = {"GlobalTradeAverage", "gta", "tradeaverage"})
    public String GlobalTradeAverage(@Me JSONObject command, @Me IMessageIO channel, TradeManager manager, @Timestamp long time) {
        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(time);

        Map<ResourceType, Double> lowMap = averages.getKey();
        Map<ResourceType, Double> highMap = averages.getValue();

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

        String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time);
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Global Trade Average " + timeStr)
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
                .addField("Low", StringMan.join(low, "\n"), true)
                .addField("High", StringMan.join(high, "\n"), true).build();
        channel.create()
                        .embed(embed)
                        .commandButton(command, "Refresh")
                                .send();
        return null;
    }

    @Command(aliases = {"GlobalTradeVolume", "gtv", "tradevolume"})
    public String GlobalTradeVolume(@Me JSONObject command, @Me IMessageIO channel, TradeManager manager) {
        TradeManager trader = Locutus.imp().getTradeManager();
        String refreshEmoji = "Refresh";

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

        channel.create().embed(new EmbedBuilder()
                .setTitle("Global Trade Volume")
                .addField("Resource", "\u200B\n" + StringMan.join(resourceNames, "\n"), true)
        .addField("Daily", StringMan.join(daily, " "), true)
        .addField("Weekly", StringMan.join(weekly, " "), true)
        .build()
        ).commandButton(command, "Refresh").send();

        return null;
    }

    @Command(desc = "Show resource value", aliases = {"resourcevalue", "convertedtotal"})
    public String convertedTotal(Map<ResourceType, Double> resources, @Switch("n") boolean normalize, @Switch("b") boolean useBuyPrice, @Switch("s") boolean useSellPrice, @Switch("t") ResourceType convertType) {
        if (normalize) {
            double total = PnwUtil.convertedTotal(resources);
            if (total <= 0) {
                return "Total is negative";
            }

            double negativeTotal = 0;

            Iterator<Map.Entry<ResourceType, Double>> iter = resources.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iter.next();
                if (entry.getValue() < 0) {
                    negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue().doubleValue() * -1;
                    iter.remove();
                }
            }
            double postiveTotal = PnwUtil.convertedTotal(resources);


            double factor = total / postiveTotal;
//            factor = Math.min(factor, postiveTotal / (negativeTotal + postiveTotal));

            for (ResourceType type : ResourceType.values()) {
                Double value = resources.get(type);
                if (value == null || value == 0) continue;

                resources.put(type, value * factor);
            }
        }

        StringBuilder result = new StringBuilder("```" + PnwUtil.resourcesToString(resources) + "```");

        double value = PnwUtil.convertedTotal(resources);
        if (useBuyPrice || useSellPrice) {
            value = 0;
            boolean buy = useBuyPrice;
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                int price = Locutus.imp().getTradeManager().getPrice(entry.getKey(), buy);
                value += price * entry.getValue();
            }
        }
        result.append("\n" + "Worth: $" + MathMan.format(value));
        if (convertType != null && convertType != ResourceType.MONEY) {
            double convertTypeValue = PnwUtil.convertedTotal(convertType, 1);
            double amtConvertType = value / convertTypeValue;
            result.append(" OR " + MathMan.format(amtConvertType) + "x " + convertType);
        }

        return result.toString();
    }

    @Command(desc = "Get the buy/sell margin for each resource")
    public String tradeMargin(@Me JSONObject command, @Me IMessageIO channel, TradeManager manager, @Switch("p") boolean usePercent) {
        TradeManager trader = Locutus.imp().getTradeManager();
        String refreshEmoji = "Refresh";

        Map<ResourceType, Double> low = trader.getLow().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        Map<ResourceType, Double> high = trader.getHigh().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        List<ResourceType> resources = new ArrayList<>(ResourceType.valuesList);
        resources.remove(ResourceType.MONEY);

        List<String> resourceNames = resources.stream().map(r -> r.name().toLowerCase()).collect(Collectors.toList());

        ArrayList<String> diffList = new ArrayList<>();

        for (ResourceType type : resources) {
            Double o1 = low.get(type);
            Double o2 = high.get(type);

            double diff = o1 == null || o2 == null || !Double.isFinite(o1) || !Double.isFinite(o2) ? Double.NaN : (o2 - o1);

            if (usePercent) {
                diff = 100 * diff / o2;
            }

            diffList.add(o1 == null ? "" : (MathMan.format(diff) + (usePercent ? "%" : "")));
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Margin")
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
        .addField("margin", StringMan.join(diffList, "\n"), true)
                .build()
        ).commandButton(command, "Refresh").send();

        return null;
    }

    @Command
    public String tradePrice(@Me JSONObject command, @Me IMessageIO channel, TradeManager manager) {
        String refreshEmoji = "Refresh";

        Map<ResourceType, Double> low = manager.getLow().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        Map<ResourceType, Double> high = manager.getHigh().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));

        String lowKey = "Low";
        String highKey = "High";

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
        ).commandButton(command, "Refresh").send();
        return null;
    }

    @Command(desc = "View an accumulation of all the net trades a nation made, grouped by nation.", aliases = {"TradeRanking", "TradeProfitRanking"})
    public String tradeRanking(@Me IMessageIO channel, @Me JSONObject command, Set<DBNation> nations, @Timestamp long time, @Switch("a") boolean groupByAlliance, @Switch("u") boolean uploadFile) {
        Function<DBNation, Integer> groupBy = groupByAlliance ? groupBy = f -> f.getAlliance_id() : f -> f.getNation_id();
        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        Map<Integer, TradeRanking.TradeProfitContainer> tradeContainers = new HashMap<>();

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(time);

        for (DBTrade trade : trades) {
            Integer buyer = trade.getBuyer();
            Integer seller = trade.getSeller();

            if (!nationIds.contains(buyer) && !nationIds.contains(seller)) {
                continue;
            }

            double per = trade.getPpu();
            ResourceType type = trade.getResource();

            if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000))) {
                continue;
            }

            for (int nationId : new int[]{buyer, seller}) {
                if (!nationIds.contains(nationId)) continue;
                DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                if (nation == null) continue;
                if (groupByAlliance && nation.getAlliance_id() == 0) continue;
                int groupId = groupBy.apply(nation);

                int sign = (nationId == seller ^ trade.isBuy()) ? 1 : -1;
                long total = trade.getQuantity() * (long) trade.getPpu();

                TradeRanking.TradeProfitContainer container = tradeContainers.computeIfAbsent(groupId, f -> new TradeRanking.TradeProfitContainer());

                if (sign > 0) {
                    container.inflows.put(type, trade.getQuantity() + container.inflows.getOrDefault(type, 0L));
                    container.sales.put(type, trade.getQuantity() + container.sales.getOrDefault(type, 0L));
                    container.salesPrice.put(type, total + container.salesPrice.getOrDefault(type, 0L));
                } else {
                    container.outflow.put(type, trade.getQuantity() + container.inflows.getOrDefault(type, 0L));
                    container.purchases.put(type, trade.getQuantity() + container.purchases.getOrDefault(type, 0L));
                    container.purchasesPrice.put(type, total + container.purchasesPrice.getOrDefault(type, 0L));
                }

                container.netOutflows.put(type, ((-1) * sign * trade.getQuantity()) + container.netOutflows.getOrDefault(type, 0L));
                container.netOutflows.put(ResourceType.MONEY, (sign * total) + container.netOutflows.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        Map<Integer, Double> profitByGroup = new HashMap<>();
        for (Map.Entry<Integer, TradeRanking.TradeProfitContainer> containerEntry : tradeContainers.entrySet()) {
            TradeRanking.TradeProfitContainer container = containerEntry.getValue();
            Map<ResourceType, Double> ppuBuy = new HashMap<>();
            Map<ResourceType, Double> ppuSell = new HashMap<>();

            for (Map.Entry<ResourceType, Long> entry : container.purchases.entrySet()) {
                ResourceType type = entry.getKey();
                ppuBuy.put(type, (double) container.purchasesPrice.get(type) / entry.getValue());
            }

            for (Map.Entry<ResourceType, Long> entry : container.sales.entrySet()) {
                ResourceType type = entry.getKey();
                ppuSell.put(type, (double) container.salesPrice.get(type) / entry.getValue());
            }

            double profitTotal = PnwUtil.convertedTotal(container.netOutflows);
            double profitMin = 0;
            for (Map.Entry<ResourceType, Long> entry : container.netOutflows.entrySet()) {
                profitMin += -PnwUtil.convertedTotal(entry.getKey(), -entry.getValue());
            }
            profitTotal = Math.min(profitTotal, profitMin);
            profitByGroup.put(containerEntry.getKey(), profitTotal);
        }


        String title = (groupByAlliance ? "Alliance" : "") + "trade profit (" + profitByGroup.size() + ")";
        new SummedMapRankBuilder<>(profitByGroup).sort().nameKeys(id -> PnwUtil.getName(id, groupByAlliance)).build(channel, command, title, uploadFile);
        return null;
    }

    public static class TradeProfitContainer {
        public Map<ResourceType, Long> netOutflows = new HashMap<>();
        public Map<ResourceType, Long> inflows = new HashMap<>();
        public Map<ResourceType, Long> outflow = new HashMap<>();
        public Map<ResourceType, Long> purchases = new HashMap<>();
        public Map<ResourceType, Long> purchasesPrice = new HashMap<>();
        public Map<ResourceType, Long> sales = new HashMap<>();
        public Map<ResourceType, Long> salesPrice = new HashMap<>();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String trending(@Me GuildDB db, @Timestamp long time) throws GeneralSecurityException, IOException {
        Map<ResourceType, Map<Integer, LongAdder>> sold = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Map<Integer, LongAdder>> bought = new EnumMap<>(ResourceType.class);

        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(time);

        Map<ResourceType, Double> lowMap = averages.getKey();
        Map<ResourceType, Double> highMap = averages.getValue();

        link.locutus.discord.db.TradeDB tradeDB = Locutus.imp().getTradeManager().getTradeDb();
        for (DBTrade offer : tradeDB.getTrades(time)) {
            // Ignore outliers
            int ppu = offer.getPpu();


            double lowCutoff = averages.getKey().get(offer.getResource()) * 0.5;
            double highCutoff = averages.getValue().get(offer.getResource()) * 2;

            if (ppu < lowCutoff || ppu > highCutoff) continue;

            if (offer.getResource() == ResourceType.CREDITS) {
                ppu /= 10000;
            } else if (offer.getResource() != ResourceType.FOOD) {
                ppu /= 10;
            }

            Map<ResourceType, Map<Integer, LongAdder>> map = offer.isBuy() ? sold : bought;
            Map<Integer, LongAdder> rssMap = map.get(offer.getResource());
            if (rssMap == null) {
                rssMap = new HashMap<>();
                map.put(offer.getResource(), rssMap);
            }
            LongAdder cumulative = rssMap.get(ppu);
            if (cumulative == null) {
                cumulative = new LongAdder();
                rssMap.put(ppu, cumulative);
            }
            cumulative.add(offer.getQuantity());
        }

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TRADE_VOLUME_SHEET);

        List<Object> header = new ArrayList<>();
        header.add("PPU (adjusted)");
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.MONEY) {
                header.add(value.name() + " BUY");
                header.add(value.name() + " SELL");
            }
        }
        sheet.setHeader(header);

        Map<ResourceType, Long> soldPrevious = new HashMap<>();
        Map<ResourceType, Long> boughtPrevious = new HashMap<>();

        for (int i = 30; i < 10000; i += 5) {
            header.set(0, Integer.toString(i));
            for (int j = 1; j < ResourceType.values.length; j++) {
                ResourceType value = ResourceType.values[j];
                {
                    int headerIndex = (j - 1) * 2 + 1;
                    Map<Integer, LongAdder> soldByType = sold.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = soldPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        soldPrevious.put(value, previous + amt.longValue());
                    }
                }
                {
                    int headerIndex = (j - 1) * 2 + 2;
                    Map<Integer, LongAdder> soldByType = bought.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = boughtPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        boughtPrevious.put(value, previous + amt.longValue());
                    }
                }
            }

            sheet.addRow(header);
        }

        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "View an accumulation of all the net trades a nation made, grouped by nation.")
    public String tradeProfit(@Me GuildDB db, Set<DBNation> nations, @Timestamp long time) throws GeneralSecurityException, IOException {
        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(time);

        Map<ResourceType, Long> netOutflows = new HashMap<>();

        Map<ResourceType, Long> inflows = new HashMap<>();
        Map<ResourceType, Long> outflow = new HashMap<>();

        Map<ResourceType, Long> purchases = new HashMap<>();
        Map<ResourceType, Long> purchasesPrice = new HashMap<>();

        Map<ResourceType, Long> sales = new HashMap<>();

        Map<ResourceType, Long> salesPrice = new HashMap<>();

        for (DBTrade trade : trades) {
            Integer buyer = trade.getBuyer();
            Integer seller = trade.getSeller();

            if (!nationIds.contains(buyer) && !nationIds.contains(seller)) {
                continue;
            }

            double per = trade.getPpu();
            ResourceType type = trade.getResource();

            if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000))) {
                continue;
            }

            int sign = (nationIds.contains(seller) ^ trade.isBuy()) ? 1 : -1;
            long total = trade.getQuantity() * (long) trade.getPpu();

            if (sign > 0) {
                inflows.put(type, trade.getQuantity() + inflows.getOrDefault(type, 0L));
                sales.put(type, trade.getQuantity() + sales.getOrDefault(type, 0L));
                salesPrice.put(type, total + salesPrice.getOrDefault(type, 0L));
            } else {
                outflow.put(type, trade.getQuantity() + outflow.getOrDefault(type, 0L));
                purchases.put(type, trade.getQuantity() + purchases.getOrDefault(type, 0L));
                purchasesPrice.put(type, total + purchasesPrice.getOrDefault(type, 0L));
            }

            netOutflows.put(type, ((-1) * sign * trade.getQuantity()) + netOutflows.getOrDefault(type, 0L));
            netOutflows.put(ResourceType.MONEY, (sign * total) + netOutflows.getOrDefault(ResourceType.MONEY, 0L));
        }

        Map<ResourceType, Double> ppuBuy = new HashMap<>();
        Map<ResourceType, Double> ppuSell = new HashMap<>();

        for (Map.Entry<ResourceType, Long> entry : purchases.entrySet()) {
            ResourceType type = entry.getKey();
            ppuBuy.put(type, (double) purchasesPrice.get(type) / entry.getValue());
        }

        for (Map.Entry<ResourceType, Long> entry : sales.entrySet()) {
            ResourceType type = entry.getKey();
            ppuSell.put(type, (double) salesPrice.get(type) / entry.getValue());
        }

        double profitTotal = PnwUtil.convertedTotal(netOutflows);
        double profitMin = 0;
        for (Map.Entry<ResourceType, Long> entry : netOutflows.entrySet()) {
            profitMin += -PnwUtil.convertedTotal(entry.getKey(), -entry.getValue());
        }
        profitTotal = Math.min(profitTotal, profitMin);

        HashMap<ResourceType, Long> totalVolume = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values()) {
            long in  = inflows.getOrDefault(type, 0L);
            long out  = outflow.getOrDefault(type, 0L);
            long total = Math.abs(in) + Math.abs(out);
            if (total != 0) totalVolume.put(type, total);
        }
//
//        if (createSpreadsheet) {
//            SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.NATION_SHEET);
//        }

        StringBuilder response = new StringBuilder();
        response
                .append('\n').append("Buy (PPU):```")
                .append(String.format("%16s", PnwUtil.resourcesToString(ppuBuy)))
                .append("```")
                .append(' ').append("Sell (PPU):```")
                .append(String.format("%16s", PnwUtil.resourcesToString(ppuSell)))
                .append("```")
                .append(' ').append("Net inflows:```")
                .append(String.format("%16s", PnwUtil.resourcesToString(netOutflows)))
                .append("```")
                .append(' ').append("Total Volume:```")
                .append(String.format("%16s", PnwUtil.resourcesToString(totalVolume)))
                .append("```");
        response.append("Profit total: $").append(MathMan.format(profitTotal));
        return response.toString().trim();
    }


    @Command(desc = "View an accumulation of all the net money trades a nation made, grouped by nation.")
    public String moneyTrades(TradeManager manager, DBNation nation, @Timestamp long time, @Switch("f") boolean forceUpdate, @Switch("a") boolean addBalance) throws IOException {
        if (forceUpdate) {
            manager.updateTradeList(Event::post);
        }

        Map<Integer, Map<ResourceType, Long>> netInflows = new HashMap<>();

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nation.getNation_id(), time);
        for (DBTrade offer : trades) {
            if (offer.getResource() == ResourceType.CREDITS) continue;
            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;

            int sign = offer.isBuy() ? -1 : 1;
            int per = offer.getPpu();

            Integer client = (offer.getSeller() == (nation.getNation_id())) ? offer.getBuyer() : offer.getSeller();

            Map<ResourceType, Long> existing = netInflows.computeIfAbsent(client,  integer -> Maps.newLinkedHashMap());

            if (per <= 1) {
                existing.put(offer.getResource(), (long) (offer.getQuantity() * sign + existing.getOrDefault(offer.getResource(), 0L)));
            } else {
                existing.put(ResourceType.MONEY, (long) (sign * offer.getTotal()) + existing.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        if (netInflows.isEmpty()) return "No trades found for " + nation.getNation() + " in the past " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time);

        StringBuilder response = new StringBuilder("Your net inflows from:");
        for (Map.Entry<Integer, Map<ResourceType, Long>> entry : netInflows.entrySet()) {
            Integer clientId = entry.getKey();
            DBNation client = Locutus.imp().getNationDB().getNation(clientId);
            String name = PnwUtil.getName(clientId, false);
            if (addBalance) {
                response.append("\n**" + name);
                if (client != null) response.append(" | " + client.getAllianceName());
                response.append(":**\n");
                String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + clientId;
                response.append("```" + Settings.commandPrefix(true) + "addbalance " + url + " " + PnwUtil.resourcesToString(entry.getValue()) + " #deposit```");
            } else {
                response.append('\n').append("```").append(name).append(" | ");
                if (client != null && client.getAlliance_id() != 0) {
                    response.append(String.format("%16s", client.getAllianceName()));
                }
                response.append(String.format("%16s", PnwUtil.resourcesToString(entry.getValue())))
                        .append("```");
            }

        }
        return response.toString().trim();
    }

    @Command
    @RolePermission(Roles.ECON)
    public String compareOffshoreStockpile(@Me IMessageIO channel, @Me GuildDB db) throws IOException {
        GuildDB offshoreDb = db.getOffshoreDB();
        if (offshoreDb != db) throw new IllegalArgumentException("This command must be run in the offshore server");

        channel.send("Please wait...");

        OffshoreInstance offshore = db.getOffshore();
        Map<ResourceType, Double> stockpile = db.getAlliance().getStockpile();
        Set<Long> coalitions = new LinkedHashSet<>(db.getCoalitionRaw(Coalition.OFFSHORING));
        coalitions.remove(db.getIdLong());
        coalitions.remove((long) db.getAlliance_id());
        coalitions.removeIf(f -> Locutus.imp().getGuildDB(f) == null && Locutus.imp().getGuildDBByAA(f.intValue()) == null);

        String title = "Compare Stockpile to Deposits (" + coalitions.size() + ")";
        StringBuilder body = new StringBuilder();
        Set<Long> aaIds = coalitions.stream().filter(f -> f.intValue() == f).collect(Collectors.toSet());
        Set<Long> corpIds = coalitions.stream().filter(f -> f.intValue() != f).collect(Collectors.toSet());
        body.append("Alliances: " + StringMan.join(aaIds, ",")).append("\n");
        body.append("Corporations: " + StringMan.join(corpIds, ",")).append("\n");
        body.append("Stockpile: `" + PnwUtil.resourcesToString(stockpile) + "`\n");
        body.append(" - worth: ~$" + MathMan.format(PnwUtil.convertedTotal(stockpile))).append("\n");

        Map<ResourceType, Double> allDeposits = new HashMap<>();
        for (Long coalition : coalitions) {
            Map<ResourceType, Double> deposits;
            GuildDB otherDb;
            if (coalition > Integer.MAX_VALUE) {
                otherDb = Locutus.imp().getGuildDB(coalition);
            } else {
                otherDb = Locutus.imp().getGuildDBByAA(coalition.intValue());
            }
            if (otherDb != null) {
                deposits = PnwUtil.resourcesToMap(offshore.getDeposits(otherDb));
                allDeposits = PnwUtil.add(allDeposits, deposits);
            }
        }
        Map<ResourceType, Double> diff = PnwUtil.subResourcesToA(new HashMap<>(allDeposits), stockpile);

        body.append("Offshored Deposits: `" + PnwUtil.resourcesToString(allDeposits) + "`\n");
        body.append(" - worth: ~$" + MathMan.format(PnwUtil.convertedTotal(allDeposits))).append("\n");

        body.append("Diff: `" + PnwUtil.resourcesToString(diff) + "`\n");
        body.append(" - worth: ~$" + MathMan.format(PnwUtil.convertedTotal(diff))).append("\n");

        String emoji = "Show Day Graph";

        body.append("\nPress `" + emoji + "` to compare by day (200 days)");


//        String cmd = "_" + Settings.commandPrefix(false) + "compareStockpileValueByDay " + PnwUtil.resourcesToString(stockpile) + " " + PnwUtil.resourcesToString(allDeposits) + " 200";

        CommandRef cmd = CM.trade.compareStockpileValueByDay.cmd.create(PnwUtil.resourcesToString(stockpile), PnwUtil.resourcesToString(allDeposits), "200");

        channel.create().embed(title, body.toString())
                        .commandButton(cmd, "Show Graph (200d)")
                .append("Done!")
                                .send();
        return null;
    }

    @Command(desc = "Generate a graph comparing two resource stockpiles by day")
    @RolePermission(value = Roles.MEMBER)
    public String compareStockpileValueByDay(@Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, Map<ResourceType, Double> stockpile1, Map<ResourceType, Double> stockpile2, @Range(min=1, max=3000) int days) throws IOException, GeneralSecurityException {
        Map<ResourceType, Map<Long, Double>> avgByRss = new HashMap<>();
        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;
        List<ResourceType> resources = new ArrayList<>(Arrays.asList(ResourceType.values()));
        resources.remove(ResourceType.CREDITS);
        resources.remove(ResourceType.MONEY);

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

        for (ResourceType type : resources) {
            double curAvg = manager.getHighAvg(type);
            int min = (int) (curAvg * 0.2);
            int max = (int) (curAvg * 5);

            Map<Long, Double> averages = tradeDB.getAverage(start, type, 15, min, max);

            avgByRss.put(type, averages);

            minDay = Math.min(minDay, Collections.min(averages.keySet()));
            maxDay = Collections.max(averages.keySet());
        }

        Map<Long, Double> valueByDay1 = new HashMap<>();
        Map<Long, Double> valueByDay2 = new HashMap<>();

        double minDiff = Double.MAX_VALUE;
        double totalValue1 = 0;
        double totalValue2 = 0;
        double num = 0;

        outer:
        for (long day = minDay; day <= maxDay; day++) {
            double val1 = 0;
            double val2 = 0;

            for (ResourceType resource : resources) {
                Double rssPrice = avgByRss.getOrDefault(resource, Collections.emptyMap()).get(day);
                if (rssPrice == null) {
                    continue outer;
                }

                val1 += rssPrice * stockpile1.getOrDefault(resource, 0d);
                val2 += rssPrice * stockpile2.getOrDefault(resource, 0d);
            }
            val1 += 1 * stockpile1.getOrDefault(ResourceType.MONEY, 0d);
            val2 += 1 * stockpile2.getOrDefault(ResourceType.MONEY, 0d);

            valueByDay1.put(day, val1);
            valueByDay2.put(day, val2);

            double diff = val2 - val1;
            if (diff < minDiff) minDiff = diff;
        }

        long finalMinDay = minDay;
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Stockpile value by day", "day", "stockpile value", "stockpile 1", "stockpile 2") {

            @Override
            public void add(long day, Void ignore) {
                long offset = day - finalMinDay;
                add(offset, valueByDay1.getOrDefault(day, 0d), valueByDay2.getOrDefault(day, 0d));
            }
        };

        for (long day = minDay; day <= maxDay; day++) {
            table.add(day, (Void) null);
        }

        table.write(channel);
        return "Done!";
    }

    @Command(desc = "Generate a graph of average trade price (buy/sell) by day")
    @RolePermission(value = Roles.MEMBER)
    public String tradepricebyday(@Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, List<ResourceType> resources, int days) throws IOException, GeneralSecurityException {
        if (days <= 1) return "Invalid number of days";
        resources.remove(ResourceType.MONEY);
        resources.remove(ResourceType.CREDITS);
        if (resources.isEmpty()) return "Invalid resources";

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

        Map<ResourceType, Map<Long, Double>> avgByRss = new HashMap<>();
        long minDay = Long.MAX_VALUE;
        long maxDay = Long.MIN_VALUE;

        for (ResourceType type : resources) {
            // long minDate, ResourceType type, int minQuantity, int min, int max
            double curAvg = manager.getHighAvg(type);
            int min = (int) (curAvg * 0.2);
            int max = (int) (curAvg * 5);

            Map<Long, Double> averages = tradeDB.getAverage(start, type, 15, min, max);

            avgByRss.put(type, averages);

            minDay = Math.min(minDay, Collections.min(averages.keySet()));
            maxDay = Collections.max(averages.keySet());
        }

        String title = "Trade average by day";

        double[] buffer = new double[resources.size()];
        long finalMinDay = minDay;
        String[] labels = resources.stream().map(f -> f.getName()).toArray(String[]::new);
        TimeNumericTable<Map<ResourceType, Map<Long, Double>>> table = new TimeNumericTable<>(title,"day", "ppu", labels) {
            @Override
            public void add(long day, Map<ResourceType, Map<Long, Double>> cost) {
                for (int i = 0; i < resources.size(); i++) {
                    ResourceType type = resources.get(i);
                    Double value = cost.getOrDefault(type, Collections.emptyMap()).get(day);
                    if (value != null) buffer[i] = value;
                }
                add(day - finalMinDay, buffer);
            }
        };

        for (long day = minDay; day <= maxDay; day++) {
            table.add(day, avgByRss);
        }


        table.write(channel);

        return "Done!";
    }

    @Command(desc = "Generate a graph of average trade margin (buy/sell) by day")
    @RolePermission(value = Roles.MEMBER)
    public String trademarginbyday(@Me IMessageIO channel, TradeManager manager, @Range(min=1, max=300) int days, @Default("true") boolean percent) throws IOException, GeneralSecurityException {
        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(days + 1);

        List<DBTrade> allOffers = manager.getTradeDb().getTrades(cutoff);
        Map<Long, List<DBTrade>> offersByDay = new LinkedHashMap<>();

        long minDay = Long.MAX_VALUE;
        long maxDay = TimeUtil.getDay();
        for (DBTrade offer : allOffers) {
            long turn = TimeUtil.getTurn(offer.getDate());
            long day = turn / 12;
            minDay = Math.min(minDay, day);
            offersByDay.computeIfAbsent(day, f -> new ArrayList<>()).add(offer);
        }
        offersByDay.remove(minDay);
        minDay++;

        Map<Long, Map<ResourceType, Double>> marginsByDay = new HashMap<>();

        for (Map.Entry<Long, List<DBTrade>> entry : offersByDay.entrySet()) {
            long day = entry.getKey();
            List<DBTrade> offers = entry.getValue();
            Map<ResourceType, Double> dayMargins = new HashMap<>();

            Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avg = manager.getAverage(offers);
            Map<ResourceType, Double> lows = avg.getKey();
            Map<ResourceType, Double> highs = avg.getValue();
            for (ResourceType type : ResourceType.values) {
                Double low = lows.get(type);
                Double high = highs.get(type);
                if (low != null && high != null) {
                    double margin = high - low;
                    if (percent) margin = 100 * margin / high;
                    dayMargins.put(type, margin);
                }
            }

            marginsByDay.put(day, dayMargins);
        }

        String title = "Resource margin " + (percent ? " % " : "") + "by day";

        List<ResourceType[]> tableTypes = new ArrayList<>();

//        tableTypes.add(new ResourceType[]{CREDITS});
        tableTypes.add(new ResourceType[]{ResourceType.FOOD});
        tableTypes.add(new ResourceType[]{
                ResourceType.COAL,
                ResourceType.OIL,
                ResourceType.URANIUM,
                ResourceType.LEAD,
                ResourceType.IRON,
                ResourceType.BAUXITE,
        });
        tableTypes.add(new ResourceType[]{
                ResourceType.GASOLINE,
                ResourceType.MUNITIONS,
                ResourceType.STEEL,
                ResourceType.ALUMINUM
        });

        for (ResourceType[] types : tableTypes) {
            double[] buffer = new double[types.length];
            String[] labels = Arrays.asList(types).stream().map(f -> f.getName()).toArray(String[]::new);
            TimeNumericTable<Map<ResourceType, Double>> table = new TimeNumericTable<>(title,"day", "ppu", labels) {
                @Override
                public void add(long day, Map<ResourceType, Double> cost) {
                    for (int i = 0; i < types.length; i++) {
                        buffer[i] = cost.getOrDefault(types[i], buffer[i]);
                    }
                    add(day, buffer);
                }
            };

            for (long day = minDay; day <= maxDay; day++) {
                long dayOffset = day - minDay;
                Map<ResourceType, Double> margins = marginsByDay.getOrDefault(day, Collections.emptyMap());
                table.add(dayOffset, margins);
            }


            table.write(channel);
        }


        return null;
    }

    @Command(desc = "Generate a graph of average trade volume (buy/sell) by day")
    @RolePermission(value = Roles.MEMBER)
    public String tradevolumebyday(@Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, @Range(min=1, max=300) int days) throws IOException, GeneralSecurityException {
        String title = "volume by day";
        rssTradeByDay(title, channel, days, offers -> manager.volumeByResource(offers));
        return null;
    }

    @Command(desc = "Generate a graph of average trade total (buy/sell) by day")
    @RolePermission(value = Roles.MEMBER)
    public String tradetotalbyday(@Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, @Range(min=1, max=300) int days) throws IOException, GeneralSecurityException {
        String title = "total by day";
        rssTradeByDay(title, channel, days, offers -> manager.totalByResource(offers));
        return null;
    }

    public void rssTradeByDay(String title, IMessageIO channel, int days, Function<Collection<DBTrade>, long[]> rssFunction) throws IOException {
        TradeManager manager = Locutus.imp().getTradeManager();
        link.locutus.discord.db.TradeDB tradeDb = manager.getTradeDb();

        Map<Long, List<DBTrade>> tradesByDay = getOffersByDay(days);
        long minDay = Collections.min(tradesByDay.keySet());
        long maxDay = Collections.max(tradesByDay.keySet());

        Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> volumeByDay = new HashMap<>();

        for (Map.Entry<Long, List<DBTrade>> entry : tradesByDay.entrySet()) {
            Long day = entry.getKey();
            Collection<DBTrade> offers = entry.getValue();
            offers = manager.filterOutliers(offers);
            Collection<DBTrade> lows = manager.getLow(offers);
            Collection<DBTrade> highs = manager.getHigh(offers);

            long[] volumesLow = rssFunction.apply(lows);
            long[] volumesHigh = rssFunction.apply(highs);

            Map<ResourceType, Map.Entry<Long, Long>> volumeMap = volumeByDay.computeIfAbsent(day, f -> new HashMap<>());
            for (ResourceType type : ResourceType.values) {
                long low = volumesLow[type.ordinal()];
                long high = volumesHigh[type.ordinal()];
                Map.Entry<Long, Long> lowHigh = new AbstractMap.SimpleEntry<>(low, high);
                volumeMap.put(type, lowHigh);
            }
        }

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS || type == ResourceType.MONEY) continue;
            String finalTital = type + " " + title;
            TimeNumericTable<Map.Entry<Long, Long>> table = new TimeNumericTable<>(finalTital, "day", "volume", "low", "high") {
                @Override
                public void add(long day, Map.Entry<Long, Long> volume) {
                    add(day, volume.getKey(), volume.getValue());
                }
            };

            for (long day = minDay; day <= maxDay; day++) {
                long dayOffset = day - minDay;
                Map<ResourceType, Map.Entry<Long, Long>> volume = volumeByDay.get(day);
                if (volume == null) volume = Collections.emptyMap();

                Map.Entry<Long, Long> rssVolume = volume.get(type);
                if (rssVolume != null) {
                    table.add(dayOffset, rssVolume);
                }
            }

            table.write(channel);
        }
    }

    private Map<Long, List<DBTrade>> getOffersByDay(int days) {
        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(days + 1);

        List<DBTrade> allOffers = Locutus.imp().getTradeManager().getTradeDb().getTrades(cutoff);
        Map<Long, List<DBTrade>> offersByDay = new LinkedHashMap<>();

        long minDay = Long.MAX_VALUE;
        long maxDay = TimeUtil.getDay();
        for (DBTrade offer : allOffers) {
            if (offer.getDate() > now) continue;
            long turn = TimeUtil.getTurn(offer.getDate());
            long day = turn / 12;
            minDay = Math.min(minDay, day);
            offersByDay.computeIfAbsent(day, f -> new ArrayList<>()).add(offer);
        }
        offersByDay.remove(minDay);
        return offersByDay;
    }

    @Command(desc = "List nations who have bought/sold the most of a resource over a period")
    @RolePermission(value = Roles.MEMBER)
    public String findTrader(@Me IMessageIO channel, @Me JSONObject command, TradeManager manager, link.locutus.discord.db.TradeDB db, ResourceType type, boolean isBuy, @Timestamp long cutoff, @Switch("a") boolean groupByAlliance) {
        if (type == ResourceType.MONEY || type == ResourceType.CREDITS) return "Invalid resource";
        List<DBTrade> offers = db.getTrades(cutoff);
        int findsign = isBuy ? 1 : -1;

        Collection<Transfer> transfers = manager.toTransfers(offers, false);
        Map<Integer, double[]> inflows = manager.inflows(transfers, groupByAlliance);
        Map<Integer, double[]> ppu = manager.ppuByNation(offers, groupByAlliance);

//        for (ResourceType type : ResourceType.values()) {

        Map<Integer, Double> newMap = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : inflows.entrySet()) {
            double value = entry.getValue()[type.ordinal()];
            if (value != 0 && Math.signum(value) == findsign) {
                newMap.put(entry.getKey(), value);
            }
        }
        SummedMapRankBuilder<Integer, Double> builder = new SummedMapRankBuilder<>(newMap);
        Map<Integer, Double> sorted = (findsign == 1 ? builder.sort() : builder.sortAsc()).get();

        List<String> nationName = new ArrayList<>();
        List<String> amtList = new ArrayList<>();
        List<String> ppuList = new ArrayList<>();

        int i = 0;
        for (Map.Entry<Integer, Double> entry : sorted.entrySet()) {
            if (i++ >= 25) break;
            int nationId = entry.getKey();
            double amount = entry.getValue();
            double myPpu = ppu.get(nationId)[type.ordinal()];
//                nationName.add(MarkupUtil.markdownUrl(PnwUtil.getName(nationId, false), PnwUtil.getUrl(nationId, false)));
            nationName.add(PnwUtil.getName(nationId, groupByAlliance));
            amtList.add(MathMan.format(amount));
            ppuList.add("$" + MathMan.format(myPpu));
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Price")
                .addField("Nation", StringMan.join(nationName, "\n"), true)
        .addField("Amt", StringMan.join(amtList, "\n"), true)
        .addField("Ppu", StringMan.join(ppuList, "\n"), true)
                .build()
        ).commandButton(command, "Refresh").send();
        return null;
    }
}

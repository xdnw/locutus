package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TradeProfit extends Command {
    public TradeProfit() {
        super("tradeprofit", "tp", "Traderev", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "tradeprofit <nations> <days>";
    }

    @Override
    public String desc() {
        return "View an accumulation of all the net trades a nation made, grouped by nation.\n" +
                "Add `-s` to view the result in sheet form";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations.isEmpty()) {
            return "invalid user `" + args.get(0) + "`";
        }

        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }

        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(cutoffMs);

        Map<ResourceType, Long> netOutflows = new HashMap<>();

        Map<ResourceType, Long> inflows = new HashMap<>();
        Map<ResourceType, Long> outflow = new HashMap<>();

        Map<ResourceType, Long> purchases = new HashMap<>();
        Map<ResourceType, Long> purchasesPrice = new HashMap<>();

        Map<ResourceType, Long> sales = new HashMap<>();

        Map<ResourceType, Long> salesPrice = new HashMap<>();

        int numTrades = 0;
        for (DBTrade trade : trades) {
            Integer buyer = trade.getBuyer();
            Integer seller = trade.getSeller();

            if (!nationIds.contains(buyer) && !nationIds.contains(seller)) {
                continue;
            }

            double per = trade.getPpu();
            if (!Double.isFinite(per)) continue;
            ResourceType type = trade.getResource();

            if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000))) {
                continue;
            }

            numTrades++;

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

        if (flags.contains('s')) {
            SpreadSheet sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), SheetKeys.NATION_SHEET);
        }

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
        response.append("Profit total: $").append(MathMan.format(profitTotal) + " (" + numTrades + " trades)");
        return response.toString().trim();
    }
}

package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TradeRanking extends Command {
    public TradeRanking() {
        super("TradeRanking", "TradeProfitRanking", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "TradeRanking <nations> <days>";
    }

    @Override
    public String desc() {
        return "View an accumulation of all the net trades a nation made, grouped by nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
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

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        boolean isAA = flags.contains('a');
        Function<DBNation, Integer> groupBy = isAA ? groupBy = f -> f.getAlliance_id() : f -> f.getNation_id();
        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        if (nations.isEmpty()) {
            return "invalid user `" + args.get(0) + "`";
        }

        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }

        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        Map<Integer, TradeProfitContainer> tradeContainers = new HashMap<>();

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        List<DBTrade> trades = nationIds.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(cutoffMs) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, cutoffMs);

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
                if (isAA && nation.getAlliance_id() == 0) continue;
                int groupId = groupBy.apply(nation);

                int sign = (nationId == seller ^ trade.isBuy()) ? 1 : -1;
                long total = trade.getQuantity() * (long) trade.getPpu();

                TradeProfitContainer container = tradeContainers.computeIfAbsent(groupId, f -> new TradeProfitContainer());

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
        for (Map.Entry<Integer, TradeProfitContainer> containerEntry : tradeContainers.entrySet()) {
            TradeProfitContainer container = containerEntry.getValue();
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

            double profitTotal = ResourceType.convertedTotal(container.netOutflows);
            double profitMin = 0;
            for (Map.Entry<ResourceType, Long> entry : container.netOutflows.entrySet()) {
                profitMin += -ResourceType.convertedTotal(entry.getKey(), -entry.getValue());
            }
            profitTotal = Math.min(profitTotal, profitMin);
            profitByGroup.put(containerEntry.getKey(), profitTotal);
        }


        String title = (isAA ? "Alliance" : "") + "trade profit (" + profitByGroup.size() + ")";
        new SummedMapRankBuilder<>(profitByGroup).sort().nameKeys(id -> PW.getName(id, isAA)).build(author, channel, fullCommandRaw, title);
        return null;
    }
}

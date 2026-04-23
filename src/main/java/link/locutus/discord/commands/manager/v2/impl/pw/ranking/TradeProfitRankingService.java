package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.trade.TradeRanking;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public final class TradeProfitRankingService {
    private TradeProfitRankingService() {
    }

    public record Request(Set<Integer> nationIds, long timeStartMs, boolean groupByAlliance) {
        public Request {
            nationIds = nationIds == null ? Set.of() : Set.copyOf(nationIds);
            if (nationIds.isEmpty()) {
                throw new IllegalArgumentException("nationIds cannot be empty");
            }
            if (timeStartMs < 0L) {
                throw new IllegalArgumentException("timeStartMs cannot be negative");
            }
        }
    }

    public static RankingResult ranking(Request request) {
        Objects.requireNonNull(request, "request");
        Collection<DBTrade> trades = request.nationIds().size() > 1000
                ? Locutus.imp().getTradeManager().getTradeDb().getTrades(request.timeStartMs())
                : Locutus.imp().getTradeManager().getTradeDb().getTrades(request.nationIds(), request.timeStartMs());
        return ranking(request, trades, TradeProfitRankingService::allianceIdForNation);
    }

    static RankingResult ranking(Request request, Collection<DBTrade> trades, IntUnaryOperator allianceResolver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(allianceResolver, "allianceResolver");

        Map<Integer, TradeRanking.TradeProfitContainer> tradeContainers = new HashMap<>();
        long asOfMs = 0L;
        for (DBTrade trade : trades) {
            if (trade == null) {
                continue;
            }
            int buyer = trade.getBuyer();
            int seller = trade.getSeller();
            if (!request.nationIds().contains(buyer) && !request.nationIds().contains(seller)) {
                continue;
            }

            double ppu = trade.getPpu();
            ResourceType type = trade.getResource();
            if (ppu <= 1 || ppu > 10000 || (type == ResourceType.FOOD && ppu > 1000)) {
                continue;
            }

            for (int nationId : new int[]{buyer, seller}) {
                if (!request.nationIds().contains(nationId)) {
                    continue;
                }

                int groupId = request.groupByAlliance() ? allianceResolver.applyAsInt(nationId) : nationId;
                if (groupId == 0) {
                    continue;
                }

                int sign = (nationId == seller ^ trade.isBuy()) ? 1 : -1;
                long total = trade.getQuantity() * (long) trade.getPpu();
                TradeRanking.TradeProfitContainer container = tradeContainers.computeIfAbsent(groupId,
                        ignored -> new TradeRanking.TradeProfitContainer());

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
                asOfMs = Math.max(asOfMs, trade.getDate());
            }
        }

        Int2DoubleOpenHashMap profitByGroup = new Int2DoubleOpenHashMap();
        for (Map.Entry<Integer, TradeRanking.TradeProfitContainer> entry : tradeContainers.entrySet()) {
            TradeRanking.TradeProfitContainer container = entry.getValue();
            double profitTotal = ResourceType.convertedTotal(container.netOutflows);
            double profitMin = 0d;
            for (Map.Entry<ResourceType, Long> outflow : container.netOutflows.entrySet()) {
                profitMin += -ResourceType.convertedTotal(outflow.getKey(), -outflow.getValue());
            }
            profitByGroup.put(entry.getKey().intValue(), Math.min(profitTotal, profitMin));
        }

        RankingEntityType entityType = request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        return RankingBuilders.singleMetricRanking(
                RankingKind.TRADE_PROFIT,
                entityType,
                RankingValueFormat.MONEY,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        RankingSortDirection.DESC,
                        profitByGroup
                )),
                null,
                asOfMs == 0L ? System.currentTimeMillis() : asOfMs
        );
    }

    private static int allianceIdForNation(int nationId) {
        DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
        return nation == null ? 0 : nation.getAlliance_id();
    }
}

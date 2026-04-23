package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.TradeFlowDirection;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.TradeProfitRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.TradeRankingService;
import link.locutus.discord.db.entities.DBNation;

import java.util.Set;

public final class TradeRankingRequests {
    private TradeRankingRequests() {
    }

    public static TradeRankingService.Request findTrader(
            ResourceType resource,
            long cutoffMs,
            String buyOrSell,
            boolean groupByAlliance,
            boolean includeOutsideMarketPrices,
            boolean absoluteTransfersOnly,
            Set<DBNation> nations
    ) {
        return new TradeRankingService.Request(
                resource,
                cutoffMs,
                TradeFlowDirection.valueOf(buyOrSell.toUpperCase()),
                groupByAlliance,
                includeOutsideMarketPrices,
                absoluteTransfersOnly,
                RankingRequestSupport.nationIds(nations)
        );
    }

    public static TradeProfitRankingService.Request tradeProfit(Set<DBNation> nations, long timeStartMs, boolean groupByAlliance) {
        return new TradeProfitRankingService.Request(
                RankingRequestSupport.nationIds(nations),
                timeStartMs,
                groupByAlliance
        );
    }
}

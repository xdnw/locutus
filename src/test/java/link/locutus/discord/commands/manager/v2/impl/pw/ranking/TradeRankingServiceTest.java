package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.TradeRankingRequests;
import link.locutus.discord.db.entities.DBTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeRankingServiceTest {
    @Test
    void rankingBuildsAmountAndPpuColumnsForBoughtResourceFlow() {
        RankingResult result = TradeRankingService.ranking(
                List.of(
                        trade(1, 1_000L, 10, 20, ResourceType.COAL, false, 100, 50),
                        trade(2, 2_000L, 10, 30, ResourceType.COAL, false, 40, 70),
                        trade(3, 3_000L, 20, 10, ResourceType.COAL, false, 25, 80)
                ),
                TradeRankingRequests.findTrader(ResourceType.COAL, 0L, "BOUGHT", false, true, false, null),
                nationId -> 0,
                (ppu, resource) -> false
        );

        assertEquals(RankingKind.TRADE_FLOW, result.kind());
        assertEquals(List.of(20L, 30L), result.keyIds());
        assertEquals(RankingValueKind.AMOUNT, result.valueColumns().get(0).kind());
        assertEquals(List.of(new BigDecimal("75"), new BigDecimal("40")), result.valueColumns().get(0).values());
        assertEquals(RankingValueKind.PRICE_PER_UNIT, result.valueColumns().get(1).kind());
        assertEquals(List.of(new BigDecimal("56"), new BigDecimal("70")), result.valueColumns().get(1).values());
    }

    @Test
    void rankingCanGroupByAllianceAndFilterOutsideMarketTrades() {
        RankingResult result = TradeRankingService.ranking(
                List.of(
                        trade(1, 1_000L, 10, 20, ResourceType.OIL, false, 50, 40),
                        trade(2, 2_000L, 30, 20, ResourceType.OIL, false, 10, 99999)
                ),
                TradeRankingRequests.findTrader(ResourceType.OIL, 0L, "SOLD", true, false, true, Set.of()),
                nationId -> switch (nationId) {
                    case 10, 20 -> 100;
                    case 30 -> 200;
                    default -> 0;
                },
                (ppu, resource) -> ppu > 1000
        );

        assertEquals(RankingEntityType.ALLIANCE, result.keyType());
        assertEquals(List.of(100L), result.keyIds());
        assertEquals(List.of(new BigDecimal("50")), result.valueColumns().get(0).values());
        assertEquals(List.of(new BigDecimal("40")), result.valueColumns().get(1).values());
    }

    private static DBTrade trade(int tradeId, long date, int seller, int buyer, ResourceType resource, boolean isBuy, int quantity, int ppu) {
        return new DBTrade(tradeId, date, seller, buyer, resource, isBuy, quantity, ppu, TradeType.GLOBAL, date, -1);
    }
}

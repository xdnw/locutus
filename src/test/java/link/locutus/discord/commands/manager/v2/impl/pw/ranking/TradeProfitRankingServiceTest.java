package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeProfitRankingServiceTest {
    @Test
    void rankingAggregatesTradeProfitByAlliance() {
        RankingResult result = TradeProfitRankingService.ranking(
                new TradeProfitRankingService.Request(Set.of(10, 20, 30), 0L, true),
                List.of(
                        trade(1, 1_000L, 10, 20, ResourceType.COAL, false, 100, 50),
                        trade(2, 2_000L, 30, 10, ResourceType.OIL, false, 10, 200)
                ),
                nationId -> switch (nationId) {
                    case 10, 20 -> 100;
                    case 30 -> 200;
                    default -> 0;
                }
        );

        Map<ResourceType, Long> alliance100Net = new EnumMap<>(ResourceType.class);
        alliance100Net.put(ResourceType.OIL, 10L);
        alliance100Net.put(ResourceType.MONEY, -2000L);

        Map<ResourceType, Long> alliance200Net = new EnumMap<>(ResourceType.class);
        alliance200Net.put(ResourceType.OIL, -10L);
        alliance200Net.put(ResourceType.MONEY, 2000L);

        assertEquals(RankingKind.TRADE_PROFIT, result.kind());
        assertEquals(RankingEntityType.ALLIANCE, result.keyType());
        assertEquals(List.of(100L, 200L), result.keyIds());
        assertEquals(List.of(expectedProfit(alliance100Net), expectedProfit(alliance200Net)), result.valueColumns().get(0).values());
    }

    private static DBTrade trade(int tradeId, long date, int seller, int buyer, ResourceType resource, boolean isBuy, int quantity, int ppu) {
        return new DBTrade(tradeId, date, seller, buyer, resource, isBuy, quantity, ppu, TradeType.GLOBAL, date, -1);
    }

    private static BigDecimal expectedProfit(Map<ResourceType, Long> netOutflows) {
        double profitTotal = ResourceType.convertedTotal(netOutflows);
        double profitMin = 0d;
        for (Map.Entry<ResourceType, Long> entry : netOutflows.entrySet()) {
            profitMin += -ResourceType.convertedTotal(entry.getKey(), -entry.getValue());
        }
        return new BigDecimal(Double.toString(Math.min(profitTotal, profitMin)));
    }
}
package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffshoreRankingServiceTest {
    @Test
    void potentialOffshoreRankingAggregatesSenderAllianceValue() {
        RankingResult result = OffshoreRankingService.potentialOffshoreRanking(
                new OffshoreRankingService.PotentialRequest(9, 0L, false),
                List.of(
                        tx(1001, 1_000L, 88, 77, map(ResourceType.MONEY, 250d)),
                        tx(1002, 2_000L, 88, 77, map(ResourceType.MONEY, 150d)),
                        tx(1003, 3_000L, 66, 77, map(ResourceType.MONEY, 300d))
                ),
                nationId -> nationId == 77 ? 9 : 0
        );

        assertEquals(RankingKind.POTENTIAL_OFFSHORES, result.kind());
        assertEquals(List.of(88L, 66L), result.keyIds());
        assertEquals(List.of(new BigDecimal("400"), new BigDecimal("300")), result.valueColumns().get(0).values());
    }

    private static Transaction2 tx(int txId, long date, int senderAllianceId, int receiverNationId, Map<ResourceType, Double> resources) {
        return Transaction2.constructLegacy(
                txId,
                date,
                senderAllianceId,
                2,
                receiverNationId,
                1,
                0,
                null,
                ResourceType.resourcesToArray(resources)
        );
    }

    private static Map<ResourceType, Double> map(ResourceType type, double amount) {
        return Map.of(type, amount);
    }
}

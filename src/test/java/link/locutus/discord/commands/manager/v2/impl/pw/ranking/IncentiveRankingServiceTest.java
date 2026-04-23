package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.IncentiveRankingRequests;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncentiveRankingServiceTest {
    @Test
    void incentiveRankingBuildsOrderedSectionsPerIncentiveType() {
        RankingResult result = IncentiveRankingService.ranking(
                IncentiveRankingRequests.ranking(0L),
                List.of(
                        incentiveTx(5, NationMeta.INCENTIVE_REFERRER),
                        incentiveTx(3, NationMeta.INCENTIVE_REFERRER),
                        incentiveTx(5, NationMeta.INCENTIVE_REFERRER),
                        incentiveTx(4, NationMeta.INCENTIVE_INTERVIEWER),
                        incentiveTx(9, NationMeta.INCENTIVE_MENTOR),
                        incentiveTx(9, NationMeta.INCENTIVE_MENTOR),
                        ignoredAllianceSenderTx(88, NationMeta.INCENTIVE_MENTOR)
                )
        );

        assertEquals(RankingKind.INCENTIVE, result.kind());
        assertEquals(RankingEntityType.NATION, result.keyType());
        assertEquals(List.of(5L, 3L, 4L, 9L), result.keyIds());
        assertEquals(List.of(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("2")), result.valueColumns().get(0).values());
        assertEquals(List.of(
                new RankingSectionRange(RankingSectionKind.INCENTIVE_REFERRERS, 0, 2),
                new RankingSectionRange(RankingSectionKind.INCENTIVE_INTERVIEWERS, 2, 1),
                new RankingSectionRange(RankingSectionKind.INCENTIVE_MENTORS, 3, 1)
        ), result.sectionRanges());
    }

    private static Transaction2 incentiveTx(int senderNationId, NationMeta meta) {
        return Transaction2.construct(
                1,
                1_000L,
                senderNationId,
                1,
                0L,
                2,
                0,
                Map.of(DepositType.INCENTIVE, meta),
                false,
                false,
                new double[ResourceType.values.length]
        );
    }

    private static Transaction2 ignoredAllianceSenderTx(int senderAllianceId, NationMeta meta) {
        return Transaction2.construct(
                2,
                1_000L,
                senderAllianceId,
                2,
                0L,
                2,
                0,
                Map.of(DepositType.INCENTIVE, meta),
                false,
                false,
                new double[ResourceType.values.length]
        );
    }
}

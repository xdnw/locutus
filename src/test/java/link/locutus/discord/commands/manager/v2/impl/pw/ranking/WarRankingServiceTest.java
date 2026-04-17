package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.pnw.NationOrAlliance;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarRankingServiceTest {
    @Test
    void warCostRequestNormalizeAppliesDefaults() {
        long before = System.currentTimeMillis();
        WarRankingService.WarCostRequest request = WarRankingService.WarCostRequest.normalize(
                100L,
                null,
                Set.<NationOrAlliance>of(),
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                null
        );
        long after = System.currentTimeMillis();

        assertEquals(100L, request.timeStartMs());
        assertTrue(request.timeEndMs() >= before && request.timeEndMs() <= after);
        assertEquals(WarCostMode.DEALT, request.type());
        assertEquals(WarCostStat.WAR_VALUE, request.stat());
        assertEquals(Set.of(), request.coalition1());
        assertEquals(Set.of(), request.coalition2());
    }

    @Test
    void warCountRequestNormalizeDisablesPerMemberForNationRows() {
        WarRankingService.WarCountRequest request = WarRankingService.WarCountRequest.normalize(
                100L,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                false,
                false,
                true,
                true,
                true,
                null,
                null
        );

        assertTrue(request.rankByNation());
        assertFalse(request.normalizePerMember());
        assertTrue(request.ignoreInactiveNations());
    }

    @Test
    void conflictingWarFiltersAreRejectedAtNormalization() {
        assertThrows(IllegalArgumentException.class, () -> WarRankingService.WarCostRequest.normalize(
                0L,
                null,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                WarCostMode.DEALT,
                WarCostStat.WAR_VALUE,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                true,
                true,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> WarRankingService.WarCountRequest.normalize(
                0L,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                true,
                true,
                false,
                false,
                false,
                false,
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> WarRankingService.AttackTypeRequest.normalize(
                0L,
                AttackType.GROUND,
                Set.of(),
                null,
                false,
                true,
                true
        ));
    }

    @Test
    void applyNormalizationScalesByRequestedFactors() {
        assertEquals(90d, WarRankingService.applyNormalization(90d, 3, 9, false, false), 0.000001d);
        assertEquals(30d, WarRankingService.applyNormalization(90d, 3, 9, true, false), 0.000001d);
        assertEquals(10d, WarRankingService.applyNormalization(90d, 3, 9, false, true), 0.000001d);
        assertEquals(10d, WarRankingService.applyNormalization(180d, 2, 9, true, true), 0.000001d);
    }

    @Test
    void normalizationModeMapsToTypedSemantics() {
        assertEquals(RankingNormalizationMode.NONE, WarRankingService.normalizationMode(false, false));
        assertEquals(RankingNormalizationMode.PER_WAR, WarRankingService.normalizationMode(true, false));
        assertEquals(RankingNormalizationMode.PER_CITY, WarRankingService.normalizationMode(false, true));
        assertEquals(RankingNormalizationMode.PER_WAR_PER_CITY, WarRankingService.normalizationMode(true, true));
    }

    @Test
    void typedDescriptorsCarryWarFrontendSemantics() {
        RankingValueDescriptor warCost = RankingValueDescriptor.warCost(
                WarCostMode.DEALT,
                WarCostStat.GROUND,
                RankingValueFormat.NUMBER,
                RankingNumericType.DECIMAL,
                RankingNormalizationMode.PER_WAR_PER_CITY
        );
        assertEquals(RankingValueKind.WAR_COST, warCost.kind());
        assertEquals(WarCostMode.DEALT, warCost.warCostMode());
        assertEquals(WarCostStat.GROUND, warCost.warCostStat());
        assertEquals(RankingNormalizationMode.PER_WAR_PER_CITY, warCost.normalizationMode());

        RankingValueDescriptor warCount = RankingValueDescriptor.warCount(
                RankingValueFormat.NUMBER,
                RankingNumericType.DECIMAL,
                RankingNormalizationMode.PER_MEMBER
        );
        assertEquals(RankingValueKind.WAR_COUNT, warCount.kind());
        assertEquals(RankingNormalizationMode.PER_MEMBER, warCount.normalizationMode());

        RankingValueDescriptor attackType = RankingValueDescriptor.attackType(
                AttackType.GROUND,
                RankingValueFormat.PERCENT,
                RankingNumericType.DECIMAL
        );
        assertEquals(RankingValueKind.ATTACK_TYPE, attackType.kind());
        assertEquals(AttackType.GROUND, attackType.attackType());
        assertEquals(RankingValueFormat.PERCENT, attackType.format());
    }
}

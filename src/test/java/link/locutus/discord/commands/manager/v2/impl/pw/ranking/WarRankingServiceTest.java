package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.pnw.NationOrAlliance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarRankingServiceTest {
    @Test
    void warCostRequestNormalizeAppliesDefaults() {
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
                false,
                false,
                null
        );

        assertEquals(100L, request.timeStartMs());
        assertEquals(Long.MAX_VALUE, request.timeEndMs());
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
    void warCostSectionMetadataCarriesNormalizationAndScope() {
        WarRankingService.WarCostRequest request = new WarRankingService.WarCostRequest(
                0L,
                1L,
                Set.of(),
                Set.of(),
                true,
                WarCostMode.DEALT,
                WarCostStat.GROUND,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                null,
                null,
                null,
                false,
                false,
                Set.of()
        );

        Map<String, String> metadata = metadataByKey(WarRankingService.warCostSectionMetadata(request));

        assertEquals("WAR_ATTACK", metadata.get("source_type"));
        assertEquals("SUM", metadata.get("aggregation_mode"));
        assertEquals("ALLIANCE", metadata.get("result_entity_type"));
        assertEquals("GROUND", metadata.get("stat"));
        assertEquals("DEALT", metadata.get("value_mode"));
        assertEquals("per_war_per_city", metadata.get("normalization"));
        assertEquals("coalition_1_only", metadata.get("coalition_scope"));
        assertEquals("all", metadata.get("war_side_scope"));
    }

    @Test
    void warCountAndAttackTypeMetadataExposeFrontendSemantics() {
        WarRankingService.WarCountRequest countRequest = new WarRankingService.WarCountRequest(
                0L,
                Set.of(),
                Set.of(),
                true,
                false,
                true,
                true,
                true,
                false,
                null,
                null
        );
        Map<String, String> countMetadata = metadataByKey(WarRankingService.warCountSectionMetadata(countRequest));
        assertEquals("ALLIANCE", countMetadata.get("result_entity_type"));
        assertEquals("per_member", countMetadata.get("normalization"));
        assertEquals("offensive", countMetadata.get("war_side_scope"));
        assertEquals("attackers_only", countMetadata.get("coalition_scope"));
        assertEquals("active_only", countMetadata.get("member_scope"));

        WarRankingService.AttackTypeRequest attackTypeRequest = new WarRankingService.AttackTypeRequest(
                0L,
                AttackType.GROUND,
                Set.of(),
                80,
                true,
                false,
                true
        );
        Map<String, String> attackMetadata = metadataByKey(WarRankingService.attackTypeSectionMetadata(attackTypeRequest));
        assertEquals("WAR_ATTACK", attackMetadata.get("source_type"));
        assertEquals("COUNT", attackMetadata.get("aggregation_mode"));
        assertEquals("GROUND", attackMetadata.get("attack_type"));
        assertEquals("percent", attackMetadata.get("value_mode"));
        assertEquals("defensive", attackMetadata.get("war_side_scope"));
    }

    private static Map<String, String> metadataByKey(List<RankingQueryField> fields) {
        return fields.stream().collect(Collectors.toMap(RankingQueryField::key, RankingQueryField::value));
    }
}

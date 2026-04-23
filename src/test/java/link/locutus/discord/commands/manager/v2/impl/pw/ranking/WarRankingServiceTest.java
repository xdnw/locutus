package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.WarRankingRequests;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarRankingServiceTest {
    @Test
    void warCostRequestBuilderAppliesDefaults() {
        long before = System.currentTimeMillis();
        WarRankingService.WarCostRequest request = WarRankingRequests.cost(
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
    void warCountRequestBuilderDisablesPerMemberForNationRows() {
        WarRankingService.WarCountRequest request = WarRankingRequests.count(
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
    void conflictingWarFiltersAreRejectedAtBuilderBoundary() {
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.cost(
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
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.count(
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
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.attackType(
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
    void allowedAllianceFilterAppliesToRankedWarSide() {
        DBWar war = war(1, 101, 202, 11, 22);
        IntOpenHashSet allowedAllianceIds = new IntOpenHashSet(new int[]{11});

        assertTrue(WarRankingService.matchesAllowedAllianceSide(war, true, allowedAllianceIds));
        assertFalse(WarRankingService.matchesAllowedAllianceSide(war, false, allowedAllianceIds));
        assertEquals(11, WarRankingService.rankedEntityId(war, true, true, allowedAllianceIds));
        assertEquals(101, WarRankingService.rankedEntityId(war, false, true, allowedAllianceIds));
        assertEquals(0, WarRankingService.rankedEntityId(war, true, false, allowedAllianceIds));
        assertEquals(0, WarRankingService.rankedEntityId(war, false, false, allowedAllianceIds));
    }

    @Test
    void perWarScalingCountsOnlyAllowedRankedSides() {
        List<DBWar> wars = List.of(
                war(1, 101, 202, 11, 22),
                war(2, 303, 104, 33, 11),
                war(3, 205, 306, 22, 33)
        );
        IntOpenHashSet allowedAllianceIds = new IntOpenHashSet(new int[]{11});

        assertEquals(Map.of(11, 2), WarRankingService.countWarsByGroup(wars, true, allowedAllianceIds));
        assertEquals(Map.of(101, 1, 104, 1), WarRankingService.countWarsByGroup(wars, false, allowedAllianceIds));
    }

    private static DBWar war(int warId, int attackerId, int defenderId, int attackerAllianceId, int defenderAllianceId) {
        return new DBWar(
                warId,
                attackerId,
                defenderId,
                attackerAllianceId,
                defenderAllianceId,
                false,
                false,
                WarType.RAID,
                WarStatus.ACTIVE,
                1000L,
                10,
                10,
                0
        );
    }
}

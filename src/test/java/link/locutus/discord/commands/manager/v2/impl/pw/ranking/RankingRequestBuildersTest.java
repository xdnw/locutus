package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.AllianceRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.NationRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.RecruitmentRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.WarStatusRankingRequests;
import link.locutus.discord.pnw.SimpleNationList;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingRequestBuildersTest {
    @Test
    void allianceLootBuilderRejectsInvertedScoreRange() {
        assertThrows(IllegalArgumentException.class, () -> AllianceRankingRequests.loot(1L, false, 10d, 5d, Set.of()));
    }

    @Test
    void nationAttributeBuilderResolvesTotalOnlyForAllianceGrouping() {
        NationValueRankingService.AttributeRequest request = NationRankingRequests.attribute(
                null,
                new SimpleNationList(Set.of()),
                TypedFunction.create(Double.class, nation -> 1d, "test"),
                false,
                false,
                null,
                true
        );

        assertFalse(request.groupByAlliance());
        assertFalse(request.total());
    }

    @Test
    void warStatusBuilderKeepsEmptyCoalitionsAsNull() {
        WarStatusRankingService.Request request = WarStatusRankingRequests.status(true, Set.of(), Set.of(), 1L);

        assertTrue(request.byAlliance());
        assertNull(request.attackers());
        assertNull(request.defenders());
    }

    @Test
    void recruitmentBuilderRejectsNonPositiveTopX() {
        assertThrows(IllegalArgumentException.class, () -> RecruitmentRankingRequests.ranking(1L, 0));
    }
}

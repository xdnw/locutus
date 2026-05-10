package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.pnw.SimpleNationList;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingPresentationSupportTest {
    @Test
    void warCostTitleIncludesModeStatEntityAndNormalization() {
        WarRankingService.WarCostRequest request = new WarRankingService.WarCostRequest(
                1L,
                2L,
                Set.of(),
                Set.of(),
                false,
                WarCostMode.ATTACKER_DEALT,
                WarCostStat.AIRCRAFT,
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
                Set.of(),
                false,
                false,
                Set.of()
        );

        assertEquals("War cost: Attacker Dealt / Aircraft by Alliance per war per city", RankingPresentationSupport.title(request));
    }

    @Test
    void nationAttributeTitleReflectsAllianceAggregation() {
        NationValueRankingService.AttributeRequest request = new NationValueRankingService.AttributeRequest(
                null,
                new SimpleNationList(Set.of()),
                TypedFunction.create(Double.class, nation -> 1d, "score"),
                true,
                false,
                null,
                false
        );

        assertEquals("Average score by Alliance", RankingPresentationSupport.title(request));
    }

    @Test
    void productionTitleReflectsSelectedResourcesAndAggregation() {
        NationValueRankingService.ProductionRequest request = new NationValueRankingService.ProductionRequest(
                null,
                Set.of(ResourceType.COAL, ResourceType.OIL),
                new SimpleNationList(Set.of()),
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                null,
                new NationValueRankingService.HighlightSelection(Set.of(), Set.of())
        );

        assertEquals("Average net production value for Coal, Oil by Alliance", RankingPresentationSupport.title(request));
    }

    @Test
    void tradeFlowTitleReflectsDirectionAndGrouping() {
        TradeRankingService.Request request = new TradeRankingService.Request(
                ResourceType.COAL,
                0L,
                TradeFlowDirection.SOLD,
                true,
                true,
                false,
                Set.of()
        );

        assertEquals("Net Coal sold by Alliance", RankingPresentationSupport.title(request));
    }

    @Test
    void attackTypeTitleReflectsPercentAndWarScope() {
        WarRankingService.AttackTypeRequest request = new WarRankingService.AttackTypeRequest(
                0L,
                AttackType.GROUND,
                Set.of(),
                null,
                true,
                true,
                false
        );

        assertEquals("Share of Ground attacks in offensive wars by Alliance", RankingPresentationSupport.title(request));
    }
}
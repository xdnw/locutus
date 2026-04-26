package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.NationValueRankingService;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import net.dv8tion.jda.api.entities.Guild;

import java.util.EnumSet;
import java.util.Set;

public final class NationRankingRequests {
    private NationRankingRequests() {
    }

    public static NationValueRankingService.AttributeRequest attribute(
            Guild snapshotGuild,
            NationList nations,
            TypedFunction<DBNation, Double> attribute,
            boolean groupByAlliance,
            boolean ascending,
            Long snapshotDate,
            boolean total
    ) {
        return new NationValueRankingService.AttributeRequest(
                snapshotGuild,
                nations,
                attribute,
                groupByAlliance,
                ascending,
                snapshotDate,
                groupByAlliance && total
        );
    }

    public static NationValueRankingService.ProductionRequest production(
            Guild snapshotGuild,
            Set<ResourceType> resources,
            NationList nationList,
            boolean ignoreMilitaryUpkeep,
            boolean ignoreTradeBonus,
            boolean ignoreNationBonus,
            boolean includeNegative,
            boolean includeInactive,
            boolean listByNation,
            boolean listAverage,
            Long snapshotDate,
            Set<NationOrAlliance> highlight
    ) {
        Set<ResourceType> resolvedResources = resources == null || resources.isEmpty()
                ? EnumSet.allOf(ResourceType.class)
                : EnumSet.copyOf(resources);

        Set<Integer> nationIds = Set.of();
        Set<Integer> allianceIds = Set.of();
        if (highlight != null && !highlight.isEmpty()) {
            nationIds = RankingRequestSupport.nationIds(highlight.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::asNation).toList());
            allianceIds = RankingRequestSupport.allianceIds(highlight.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::asAlliance).toList());
        }

        return new NationValueRankingService.ProductionRequest(
                snapshotGuild,
                Set.copyOf(resolvedResources),
                RankingRequestSupport.nationListOrAll(nationList),
                ignoreMilitaryUpkeep,
                ignoreTradeBonus,
                ignoreNationBonus,
                includeNegative,
                includeInactive,
                listByNation,
                !listByNation && listAverage,
                snapshotDate,
                new NationValueRankingService.HighlightSelection(nationIds, allianceIds)
        );
    }
}

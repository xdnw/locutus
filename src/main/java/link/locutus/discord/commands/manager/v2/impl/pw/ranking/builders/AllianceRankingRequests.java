package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.AllianceRankingService;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;

import java.util.Set;

public final class AllianceRankingRequests {
    private AllianceRankingRequests() {
    }

    public static AllianceRankingService.MetricRequest metric(
            Set<DBAlliance> alliances,
            AllianceMetric metric,
            boolean ascending,
            Set<DBAlliance> highlight
    ) {
        return new AllianceRankingService.MetricRequest(
                RankingRequestSupport.alliancesOrAll(alliances),
                metric,
                ascending,
                RankingRequestSupport.allianceIds(highlight)
        );
    }

    public static AllianceRankingService.AttributeRequest attribute(
            Set<DBAlliance> alliances,
            TypedFunction<DBAlliance, Double> attribute,
            boolean ascending,
            Set<DBAlliance> highlight
    ) {
        return new AllianceRankingService.AttributeRequest(
                RankingRequestSupport.alliancesOrAll(alliances),
                attribute,
                ascending,
                RankingRequestSupport.allianceIds(highlight)
        );
    }

    public static AllianceRankingService.DeltaRequest delta(
            Set<DBAlliance> alliances,
            AllianceMetric metric,
            long timeStart,
            long timeEnd,
            boolean ascending,
            Set<DBAlliance> highlight
    ) {
        return new AllianceRankingService.DeltaRequest(
                RankingRequestSupport.alliancesOrAll(alliances),
                metric,
                timeStart,
                timeEnd,
                ascending,
                RankingRequestSupport.allianceIds(highlight)
        );
    }

    public static AllianceRankingService.LootRequest loot(
            long timeMs,
            boolean showTotal,
            Double minScore,
            Double maxScore,
            Set<DBAlliance> highlight
    ) {
        return new AllianceRankingService.LootRequest(
                timeMs,
                showTotal,
                minScore,
                maxScore,
                RankingRequestSupport.allianceIds(highlight)
        );
    }
}

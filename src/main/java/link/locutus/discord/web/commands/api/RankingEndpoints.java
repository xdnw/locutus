package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.NationValueRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RecruitmentRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.AllianceRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarStatusRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WebRankingAdapter;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;
import java.util.Set;

public class RankingEndpoints {
    @Command(desc = "Rank alliances by a metric", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult allianceMetricRanking(
            AllianceMetric metric,
            @Default Set<DBAlliance> alliances,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(AllianceRankingService.metricRanking(
                AllianceRankingService.MetricRequest.normalize(alliances, metric, reverseOrder, highlight)
        ));
    }

    @Command(desc = "Rank alliances by an alliance attribute", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult allianceAttributeRanking(
            TypedFunction<DBAlliance, Double> attribute,
            @Default Set<DBAlliance> alliances,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(AllianceRankingService.attributeRanking(
                AllianceRankingService.AttributeRequest.normalize(alliances, attribute, reverseOrder, highlight)
        ));
    }

    @Command(desc = "Rank alliances by a metric over a specified time period", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult allianceMetricDeltaRanking(
            Set<DBAlliance> alliances,
            AllianceMetric metric,
            @Timestamp long timeStart,
            @Timestamp long timeEnd,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(AllianceRankingService.deltaRanking(
                AllianceRankingService.DeltaRequest.normalize(alliances, metric, timeStart, timeEnd, reverseOrder, highlight)
        ));
    }

    @Command(desc = "Get the largest alliance bank loot per score", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult allianceLootRanking(
            @Timestamp long time,
            @Arg("Display the estimated bank size instead of per score") @Switch("t") boolean showTotal,
            @Arg("Ignore alliances without nations above a certain score") @Switch("min") Double minScore,
            @Arg("Ignore alliances without nations below a certain score") @Switch("max") Double maxScore,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(AllianceRankingService.lootRanking(
                AllianceRankingService.LootRequest.normalize(time, showTotal, minScore, maxScore, highlight)
        ));
    }

    @Command(desc = "Rank nations by an attribute", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult nationAttributeRanking(
            @Me @Default GuildDB db,
            NationList nations,
            TypedFunction<DBNation, Double> attribute,
            @Switch("a") boolean groupByAlliance,
            @Switch("r") boolean reverseOrder,
            @Switch("s") @Timestamp Long snapshotDate,
            @Arg("Total value instead of average per nation") @Switch("t") boolean total
    ) {
        return WebRankingAdapter.toWeb(NationValueRankingService.attributeRanking(
                NationValueRankingService.AttributeRequest.normalize(
                        db == null ? null : db.getGuild(),
                        nations,
                        attribute,
                        groupByAlliance,
                        reverseOrder,
                        snapshotDate,
                        total
                )
        ));
    }

    @Command(desc = "Get a ranking of alliances or nations by their net resource production", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult producerRanking(
            @Me @Default GuildDB db,
            @Arg("The resources to rank production of") Set<ResourceType> resources,
            @Arg("Nations to include in the ranking") @Default NationList nationList,
            @Arg("Exclude military unit upkeep") @Switch("m") boolean ignoreMilitaryUpkeep,
            @Arg("Exclude color trade bloc bonus") @Switch("t") boolean ignoreTradeBonus,
            @Arg("Exclude the new nation bonus") @Switch("b") boolean ignoreNationBonus,
            @Arg("Include negative resource revenue") @Switch("n") boolean includeNegative,
            @Arg("Include inactive nations (2 days)") @Switch("i") boolean includeInactive,
            @Arg("Rank by nation instead of alliances") @Switch("a") boolean listByNation,
            @Arg("Rank by average per nation instead of total") @Switch("s") boolean listAverage,
            @Arg("The date to use for the snapshot") @Switch("d") @Timestamp Long snapshotDate,
            @Arg("Highlight specific entries in the result") @Switch("h") @AllowDeleted Set<NationOrAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(NationValueRankingService.productionRanking(
                NationValueRankingService.ProductionRequest.normalize(
                        db == null ? null : db.getGuild(),
                        resources,
                        nationList,
                        ignoreMilitaryUpkeep,
                        ignoreTradeBonus,
                        ignoreNationBonus,
                        includeNegative,
                        includeInactive,
                        listByNation,
                        listAverage,
                        snapshotDate,
                        highlight
                )
        ));
    }

    @Command(desc = "Rank alliances by their new members over a timeframe", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult recruitmentRanking(
            @Arg("Date to start from") @Timestamp long cutoff,
            @Arg("Top X alliances to show in the ranking") @Range(min = 1, max = 150) @Default("80") int topX
    ) {
        return WebRankingAdapter.toWeb(RecruitmentRankingService.ranking(new RecruitmentRankingService.Request(cutoff, topX)));
    }

    @Command(desc = "Generate ranking of war status by Alliance", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult warStatusRankingByAlliance(Set<DBNation> attackers, Set<DBNation> defenders,
            @Arg("Date to start from") @Timestamp long time) {
        return WebRankingAdapter.toWeb(WarStatusRankingService.ranking(
                WarStatusRankingService.Request.normalize(true, attackers, defenders, time)
        ));
    }

    @Command(desc = "Generate ranking of war status by Nation", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult warStatusRankingByNation(Set<DBNation> attackers, Set<DBNation> defenders,
            @Arg("Date to start from") @Timestamp long time) {
        return WebRankingAdapter.toWeb(WarStatusRankingService.ranking(
                WarStatusRankingService.Request.normalize(false, attackers, defenders, time)
        ));
    }

    @Command(desc = "Rank war costs between two parties", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult warCostRanking(
            @Timestamp long timeStart,
            @Timestamp Long timeEnd,
            @AllowDeleted @Default("*") Set<NationOrAlliance> coalition1,
            @AllowDeleted @Default Set<NationOrAlliance> coalition2,
            @Switch("a") boolean onlyRankCoalition1,
            WarCostMode type,
            WarCostStat stat,
            @Switch("i") boolean excludeInfra,
            @Switch("c") boolean excludeConsumption,
            @Switch("l") boolean excludeLoot,
            @Switch("b") boolean excludeBuildings,
            @Switch("u") boolean excludeUnits,
            @Switch("g") boolean groupByAlliance,
            @Switch("w") boolean scalePerWar,
            @Switch("p") boolean scalePerCity,
            @Switch("wartype") Set<WarType> allowedWarTypes,
            @Switch("status") Set<WarStatus> allowedWarStatuses,
            @Switch("attacks") Set<AttackType> allowedAttacks,
            @Switch("aa") Set<DBAlliance> allowed_alliances,
            @Switch("off") boolean onlyOffensiveWars,
            @Switch("def") boolean onlyDefensiveWars,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return WebRankingAdapter.toWeb(WarRankingService.warCostRanking(
                WarRankingService.WarCostRequest.normalize(
                        timeStart,
                        timeEnd,
                        coalition1,
                        coalition2,
                        onlyRankCoalition1,
                        type,
                        stat,
                        excludeInfra,
                        excludeConsumption,
                        excludeLoot,
                        excludeBuildings,
                        excludeUnits,
                        groupByAlliance,
                        scalePerWar,
                        scalePerCity,
                        allowedWarTypes,
                        allowedWarStatuses,
                        allowedAttacks,
                        allowed_alliances,
                        onlyOffensiveWars,
                        onlyDefensiveWars,
                        highlight
                )
        ));
    }

    @Command(desc = "Rank the number of wars between two coalitions by nation or alliance", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult warRanking(
            @AllowDeleted Set<NationOrAlliance> attackers,
            @AllowDeleted Set<NationOrAlliance> defenders,
            @Arg("Date to start from") @Timestamp long time,
            @Switch("o") boolean onlyOffensives,
            @Switch("d") boolean onlyDefensives,
            @Switch("c") boolean only_rank_attackers,
            @Switch("n") boolean normalizePerMember,
            @Switch("i") boolean ignore2dInactives,
            @Switch("a") boolean rankByNation,
            @Switch("t") WarType warType,
            @Switch("s") Set<WarStatus> statuses
    ) {
        return WebRankingAdapter.toWeb(WarRankingService.warRanking(
                WarRankingService.WarCountRequest.normalize(
                        time,
                        attackers,
                        defenders,
                        onlyOffensives,
                        onlyDefensives,
                        only_rank_attackers,
                        normalizePerMember,
                        ignore2dInactives,
                        rankByNation,
                        warType,
                        statuses
                )
        ));
    }

    @Command(desc = "Rank the alliances by the % (or total) attacks by type.", viewable = true)
    @ReturnType(WebRankingResult.class)
    public WebRankingResult attackTypeRanking(
            @Timestamp long time,
            AttackType type,
            Set<DBAlliance> alliances,
            @Range(min = 1, max = 9999) @Switch("x") Integer only_top_x,
            @Switch("p") boolean percent,
            @Switch("o") boolean only_off_wars,
            @Switch("d") boolean only_def_wars
    ) {
        return WebRankingAdapter.toWeb(WarRankingService.attackTypeRanking(
                WarRankingService.AttackTypeRequest.normalize(
                        time,
                        type,
                        alliances,
                        only_top_x,
                        percent,
                        only_off_wars,
                        only_def_wars
                )
        ));
    }
}

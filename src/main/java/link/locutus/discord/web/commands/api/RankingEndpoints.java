package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.ArgChoice;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingResult;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.BaseballRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.IncentiveRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.OffshoreRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.TradeRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.TradeProfitRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.NationValueRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RecruitmentRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.AllianceRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarStatusRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.AllianceRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.BaseballRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.IncentiveRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.NationRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.OffshoreRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.RecruitmentRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.TradeRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.WarRankingRequests;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.WarStatusRankingRequests;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.ReturnType;
import org.json.JSONObject;

import java.util.Set;

public class RankingEndpoints {
    @Command(desc = "Rank alliances by a metric", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult allianceMetricRanking(
            AllianceMetric metric,
            @Default Set<DBAlliance> alliances,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return AllianceRankingService.metricRanking(
                AllianceRankingRequests.metric(alliances, metric, reverseOrder, highlight)
        );
    }

    @Command(desc = "Rank alliances by an alliance attribute", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult allianceAttributeRanking(
            @Me JSONObject command,
            TypedFunction<DBAlliance, Double> attribute,
            @Default Set<DBAlliance> alliances,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return AllianceRankingService.attributeRanking(
                AllianceRankingRequests.attribute(alliances, attribute, reverseOrder, highlight)
        );
    }

    @Command(desc = "Rank alliances by a metric over a specified time period", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult allianceMetricDeltaRanking(
            Set<DBAlliance> alliances,
            AllianceMetric metric,
            @Timestamp long timeStart,
            @Timestamp long timeEnd,
            @Switch("r") boolean reverseOrder,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return AllianceRankingService.deltaRanking(
                AllianceRankingRequests.delta(alliances, metric, timeStart, timeEnd, reverseOrder, highlight)
        );
    }

    @Command(desc = "Get the largest alliance bank loot per score", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult allianceLootRanking(
            @Timestamp long time,
            @Arg("Display the estimated bank size instead of per score") @Switch("t") boolean showTotal,
            @Arg("Ignore alliances without nations above a certain score") @Switch("min") Double minScore,
            @Arg("Ignore alliances without nations below a certain score") @Switch("max") Double maxScore,
            @Switch("h") @AllowDeleted Set<DBAlliance> highlight
    ) {
        return AllianceRankingService.lootRanking(
                AllianceRankingRequests.loot(time, showTotal, minScore, maxScore, highlight)
        );
    }

    @Command(desc = "Rank nations or alliances by baseball games from a specified date", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult baseballRanking(
            BaseballDB db,
            @Arg("Date to start from") @Timestamp long date,
            @Arg("Group the rankings by alliance instead of nations") @Switch("a") boolean byAlliance
    ) {
        return BaseballRankingService.ranking(
                db,
                BaseballRankingRequests.games(date, byAlliance)
        );
    }

    @Command(desc = "Rank nations or alliances by challenge baseball games", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult baseballChallengeRanking(
            BaseballDB db,
            @Arg("Group the rankings by alliance instead of nations") @Switch("a") boolean byAlliance
    ) {
        return BaseballRankingService.ranking(
                db,
                BaseballRankingRequests.challengeGames(byAlliance)
        );
    }

    @Command(desc = "Rank nations or alliances by baseball earnings from a specified date", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult baseballEarningsRanking(
            BaseballDB db,
            @Arg("Date to start from") @Timestamp long date,
            @Arg("Group the rankings by alliance instead of nations") @Switch("a") boolean byAlliance
    ) {
        return BaseballRankingService.ranking(
                db,
                BaseballRankingRequests.earnings(date, byAlliance)
        );
    }

    @Command(desc = "Rank nations or alliances by challenge baseball earnings", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult baseballChallengeEarningsRanking(
            BaseballDB db,
            @Arg("Group the rankings by alliance instead of nations") @Switch("a") boolean byAlliance
    ) {
        return BaseballRankingService.ranking(
                db,
                BaseballRankingRequests.challengeEarnings(byAlliance)
        );
    }

    @Command(desc = "Rank nations by incentive rewards received", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult incentiveRanking(
            @Me GuildDB db,
            @Arg("Date to start from") @Timestamp long timestamp
    ) {
        return IncentiveRankingService.ranking(
                db,
                IncentiveRankingRequests.ranking(timestamp)
        );
    }

    @Command(desc = "List nations or alliances who have bought and sold the most of a resource over a period", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult findTraderRanking(
            TradeManager manager,
            TradeDB db,
            ResourceType type,
            @Arg("Date to start from") @Timestamp long cutoff,
            @ArgChoice(value = {"SOLD", "BOUGHT"}) String buyOrSell,
            @Arg("Group rankings by each nation's current alliance") @Switch("a") boolean groupByAlliance,
            @Arg("Include trades done outside of standard market prices") @Switch("p") boolean includeMoneyTrades,
            @Switch("s") boolean show_absolute,
            @Switch("n") Set<DBNation> nations
    ) {
        return TradeRankingService.ranking(
                manager,
                db,
                TradeRankingRequests.findTrader(
                        type,
                        cutoff,
                        buyOrSell,
                        groupByAlliance,
                        includeMoneyTrades,
                        show_absolute,
                        nations
                )
        );
    }

    @Command(desc = "View an accumulation of all the net trades a nation made, grouped by nation or alliance", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult tradeProfitRanking(
            Set<DBNation> nations,
            @Arg("Date to start from") @Timestamp long time,
            @Arg("Group by alliance instead of nation") @Switch("a") boolean groupByAlliance
    ) {
        return TradeProfitRankingService.ranking(
                TradeRankingRequests.tradeProfit(nations, time, groupByAlliance)
        );
    }

    @Command(desc = "Find potential offshores used by an alliance", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult findOffshoreRanking(
            @AllowDeleted DBAlliance alliance,
            @Default @Timestamp Long cutoffMs,
            @Switch("c") @Arg("Display the transfer count instead of value") boolean transfer_count
    ) {
        return OffshoreRankingService.potentialOffshoreRanking(
                OffshoreRankingRequests.potential(alliance, cutoffMs, transfer_count)
        );
    }

    @Command(desc = "List potential offshore alliances by the value of their bank transfers to nations over a period of time", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult prolificOffshoresRanking(
            @Range(min = 1, max = 365) int days
    ) {
        long cutoffMs = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
        return OffshoreRankingService.prolificOffshoreRanking(
                OffshoreRankingRequests.prolific(cutoffMs)
        );
    }

    @Command(desc = "Rank nations by an attribute", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult nationAttributeRanking(
            @Me @Default GuildDB db,
            NationList nations,
            TypedFunction<DBNation, Double> attribute,
            @Switch("a") boolean groupByAlliance,
            @Switch("r") boolean reverseOrder,
            @Switch("s") @Timestamp Long snapshotDate,
            @Arg("Total value instead of average per nation") @Switch("t") boolean total,
            @Switch("n") String title
    ) {
        return NationValueRankingService.attributeRanking(
                NationRankingRequests.attribute(
                        db == null ? null : db.getGuild(),
                        nations,
                        attribute,
                        groupByAlliance,
                        reverseOrder,
                        snapshotDate,
                        total
                )
        );
    }

    @Command(desc = "Get a ranking of alliances or nations by their net resource production", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult producerRanking(
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
        return NationValueRankingService.productionRanking(
                NationRankingRequests.production(
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
        );
    }

    @Command(desc = "Rank alliances by their new members over a timeframe", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult recruitmentRanking(
            @Arg("Date to start from") @Timestamp long cutoff,
            @Arg("Top X alliances to show in the ranking") @Range(min = 1, max = 150) @Default("80") int topX
    ) {
        return RecruitmentRankingService.ranking(RecruitmentRankingRequests.ranking(cutoff, topX));
    }

    @Command(desc = "Generate ranking of war status by Alliance", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult warStatusRankingByAlliance(Set<DBNation> attackers, Set<DBNation> defenders,
            @Arg("Date to start from") @Timestamp long time) {
        return WarStatusRankingService.ranking(
                WarStatusRankingRequests.status(true, attackers, defenders, time)
        );
    }

    @Command(desc = "Generate ranking of war status by Nation", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult warStatusRankingByNation(Set<DBNation> attackers, Set<DBNation> defenders,
            @Arg("Date to start from") @Timestamp long time) {
        return WarStatusRankingService.ranking(
                WarStatusRankingRequests.status(false, attackers, defenders, time)
        );
    }

    @Command(desc = "Rank war costs between two parties", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult warCostRanking(
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
        return WarRankingService.warCostRanking(
                WarRankingRequests.cost(
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
        );
    }

    @Command(desc = "Rank the number of wars between two coalitions by nation or alliance", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult warRanking(
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
        return WarRankingService.warRanking(
                WarRankingRequests.count(
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
        );
    }

    @Command(desc = "Rank the alliances by the % (or total) attacks by type.", viewable = true)
    @ReturnType(RankingResult.class)
    public RankingResult attackTypeRanking(
            @Timestamp long time,
            AttackType type,
            Set<DBAlliance> alliances,
            @Range(min = 1, max = 9999) @Switch("x") Integer only_top_x,
            @Switch("p") boolean percent,
            @Switch("o") boolean only_off_wars,
            @Switch("d") boolean only_def_wars
    ) {
        return WarRankingService.attackTypeRanking(
                WarRankingRequests.attackType(
                        time,
                        type,
                        alliances,
                        only_top_x,
                        percent,
                        only_off_wars,
                        only_def_wars
                )
        );
    }
}

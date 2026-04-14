package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.TimeUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class WarStatusRankingService {
    private WarStatusRankingService() {
    }

    public record Request(boolean byAlliance, Set<DBNation> attackers, Set<DBNation> defenders, long timeStartMs) {
        public static Request normalize(boolean byAlliance, Set<DBNation> attackers, Set<DBNation> defenders, long timeStartMs) {
            return new Request(byAlliance, normalizeCoalition(attackers), normalizeCoalition(defenders), timeStartMs);
        }
    }

    private static Set<DBNation> normalizeCoalition(Set<DBNation> coalition) {
        if (coalition == null || coalition.isEmpty()) {
            return null;
        }
        return Set.copyOf(coalition);
    }

    public static RankingResult ranking(Request request) {
        RankingEntityType entityType = request.byAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        BiFunction<Boolean, DBWar, Integer> getId = request.byAlliance()
                ? (primary, war) -> primary ? war.getAttacker_aa() : war.getDefender_aa()
                : (primary, war) -> primary ? war.getAttacker_id() : war.getDefender_id();

        WarParser parser = WarParser.ofAANatobj(null, request.attackers(), null, request.defenders(), request.timeStartMs(), Long.MAX_VALUE);

        Map<Integer, Integer> victoryByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> lossesByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> expireByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> peaceByEntity = new Int2IntOpenHashMap();

        for (Map.Entry<Integer, DBWar> entry : parser.getWars().entrySet()) {
            DBWar war = entry.getValue();
            boolean primary = parser.getIsPrimary().apply(war);

            if (war.getStatus() == WarStatus.DEFENDER_VICTORY) {
                primary = !primary;
            }
            int id = getId.apply(primary, war);

            if (war.getStatus() == WarStatus.ATTACKER_VICTORY || war.getStatus() == WarStatus.DEFENDER_VICTORY) {
                victoryByEntity.put(id, victoryByEntity.getOrDefault(id, 0) + 1);
                int otherId = getId.apply(!primary, war);
                lossesByEntity.put(otherId, lossesByEntity.getOrDefault(otherId, 0) + 1);
            } else if (war.getStatus() == WarStatus.EXPIRED) {
                expireByEntity.put(id, expireByEntity.getOrDefault(id, 0) + 1);
            } else if (war.getStatus() == WarStatus.PEACE) {
                peaceByEntity.put(id, peaceByEntity.getOrDefault(id, 0) + 1);
            }
        }

        RankingMetricDescriptor metric = new RankingMetricDescriptor("count", "Count", RankingNumericType.INTEGER, RankingValueFormat.COUNT);
        java.util.function.Function<Integer, String> displayHint = request.byAlliance() ? WarStatusRankingService::allianceName : WarStatusRankingService::nationName;
        List<RankingQueryField> sectionMetadata = RankingSupport.sectionMetadata("WAR", RankingAggregationMode.COUNT);

        List<RankingSection> sections = List.of(
                RankingBuilders.singleMetricSection("victories", "Victories", entityType, metric, RankingSortDirection.DESC, victoryByEntity, Set.of(), displayHint, sectionMetadata, List.of()),
                RankingBuilders.singleMetricSection("losses", "Losses", entityType, metric, RankingSortDirection.DESC, lossesByEntity, Set.of(), displayHint, sectionMetadata, List.of()),
                RankingBuilders.singleMetricSection("expired", "Expired", entityType, metric, RankingSortDirection.DESC, expireByEntity, Set.of(), displayHint, sectionMetadata, List.of()),
                RankingBuilders.singleMetricSection("peace", "Peace", entityType, metric, RankingSortDirection.DESC, peaceByEntity, Set.of(), displayHint, sectionMetadata, List.of())
        );

        return new RankingResult(
                request.byAlliance() ? "war_status_by_alliance" : "war_status_by_nation",
                request.byAlliance() ? "War Status by Alliance" : "War Status by Nation",
                List.of(RankingSupport.field("start_ms", "Start", request.timeStartMs())),
                RankingBuilders.totalRowCount(sections),
                TimeUtil.getTimeFromTurn(TimeUtil.getTurn()),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                sections
        );
    }

    private static String allianceName(int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        return alliance == null ? null : alliance.getName();
    }

    private static String nationName(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        return nation == null ? null : nation.getName();
    }
}

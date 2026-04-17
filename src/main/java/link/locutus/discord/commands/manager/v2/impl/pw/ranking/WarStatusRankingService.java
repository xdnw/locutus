package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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

        long endMs = System.currentTimeMillis();
        WarParser parser = WarParser.ofAANatobj(null, request.attackers(), null, request.defenders(), request.timeStartMs(), endMs);

        Map<Integer, Integer> victoryByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> lossesByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> expireByEntity = new Int2IntOpenHashMap();
        Map<Integer, Integer> peaceByEntity = new Int2IntOpenHashMap();

        for (DBWar war : parser.getWars().values()) {
            boolean primary = parser.getIsPrimary().apply(war);
            if (war.getStatus() == WarStatus.DEFENDER_VICTORY) {
                primary = !primary;
            }

            int primaryId = getId.apply(primary, war);
            int secondaryId = getId.apply(!primary, war);

            if (war.getStatus() == WarStatus.ATTACKER_VICTORY || war.getStatus() == WarStatus.DEFENDER_VICTORY) {
                increment(victoryByEntity, primaryId);
                increment(lossesByEntity, secondaryId);
            } else if (war.getStatus() == WarStatus.EXPIRED) {
                increment(expireByEntity, primaryId);
                if (secondaryId != primaryId) {
                    increment(expireByEntity, secondaryId);
                }
            } else if (war.getStatus() == WarStatus.PEACE) {
                increment(peaceByEntity, primaryId);
                if (secondaryId != primaryId) {
                    increment(peaceByEntity, secondaryId);
                }
            }
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.WAR_STATUS,
                entityType,
                RankingValueDescriptor.warCount(RankingValueFormat.COUNT, RankingNumericType.INTEGER, RankingNormalizationMode.NONE),
                List.of(
                        RankingBuilders.singleMetricSection(RankingSectionKind.VICTORIES, RankingSortDirection.DESC, victoryByEntity),
                        RankingBuilders.singleMetricSection(RankingSectionKind.LOSSES, RankingSortDirection.DESC, lossesByEntity),
                        RankingBuilders.singleMetricSection(RankingSectionKind.EXPIRED, RankingSortDirection.DESC, expireByEntity),
                        RankingBuilders.singleMetricSection(RankingSectionKind.PEACE, RankingSortDirection.DESC, peaceByEntity)
                ),
                Set.of(),
                TimeUtil.getTimeFromTurn(TimeUtil.getTurn())
        );
    }

    private static void increment(Map<Integer, Integer> values, int entityId) {
        if (entityId == 0) {
            return;
        }
        values.merge(entityId, 1, Integer::sum);
    }
}

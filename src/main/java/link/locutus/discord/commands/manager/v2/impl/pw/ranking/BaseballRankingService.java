package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.politicsandwar.graphql.model.BBGame;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.entities.DBNation;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public final class BaseballRankingService {
    private BaseballRankingService() {
    }

    public enum ValueMode {
        GAMES,
        EARNINGS
    }

    public record Request(
            RankingKind kind,
            long timeStartMs,
            boolean challengeOnly,
            boolean byAlliance,
            ValueMode valueMode
    ) {
        public Request {
            kind = Objects.requireNonNull(kind, "kind");
            valueMode = Objects.requireNonNull(valueMode, "valueMode");
            if (timeStartMs < 0L) {
                throw new IllegalArgumentException("timeStartMs cannot be negative");
            }
        }
    }

    public static RankingResult ranking(BaseballDB db, Request request) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(request, "request");

        List<BBGame> games = db.getBaseballGames(null);
        return ranking(request, games == null ? List.of() : games);
    }

    static RankingResult ranking(Request request, List<BBGame> games) {
        return ranking(request, games, BaseballRankingService::allianceIdForNation);
    }

    static RankingResult ranking(Request request, List<BBGame> games, IntUnaryOperator allianceResolver) {
        Objects.requireNonNull(request, "request");
        List<BBGame> sourceGames = games == null ? List.of() : games;

        RankingEntityType entityType = request.byAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        RankingValueFormat valueFormat = request.valueMode() == ValueMode.EARNINGS
                ? RankingValueFormat.MONEY
                : RankingValueFormat.COUNT;

        RankingSectionSpec section;
        if (request.valueMode() == ValueMode.GAMES) {
            Int2IntOpenHashMap values = new Int2IntOpenHashMap();
            for (BBGame game : sourceGames) {
                if (!matches(request, game)) {
                    continue;
                }
                increment(values, resolveEntityId(game.getHome_nation_id(), request.byAlliance(), allianceResolver));
                increment(values, resolveEntityId(game.getAway_nation_id(), request.byAlliance(), allianceResolver));
            }
            section = RankingBuilders.singleMetricSection(
                    RankingSectionKind.forEntityType(entityType),
                    RankingSortDirection.DESC,
                    values
            );
        } else {
            Int2DoubleOpenHashMap values = new Int2DoubleOpenHashMap();
            for (BBGame game : sourceGames) {
                if (!matches(request, game)) {
                    continue;
                }
                int winnerNationId = winnerNationId(game);
                if (winnerNationId == 0) {
                    continue;
                }

                int entityId = resolveEntityId(winnerNationId, request.byAlliance(), allianceResolver);
                if (entityId == 0) {
                    continue;
                }

                double value = request.challengeOnly() ? game.getWager() : game.getSpoils();
                if (Double.isFinite(value) && value != 0d) {
                    values.merge(entityId, value, Double::sum);
                }
            }
            section = RankingBuilders.singleMetricSection(
                    RankingSectionKind.forEntityType(entityType),
                    RankingSortDirection.DESC,
                    values
            );
        }

        return RankingBuilders.singleMetricRanking(
                request.kind(),
                entityType,
                valueFormat,
                List.of(section),
                null,
                asOfMs(sourceGames)
        );
    }

    private static void increment(Int2IntOpenHashMap values, int entityId) {
        if (entityId != 0) {
            values.merge(entityId, 1, Integer::sum);
        }
    }

    private static int resolveEntityId(int nationId, boolean byAlliance, IntUnaryOperator allianceResolver) {
        if (!byAlliance) {
            return nationId;
        }
        return allianceResolver.applyAsInt(nationId);
    }

    private static int allianceIdForNation(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        if (nation == null) {
            return 0;
        }
        return nation.getAlliance_id();
    }

    private static int winnerNationId(BBGame game) {
        if (game.getHome_score() > game.getAway_score()) {
            return game.getHome_nation_id();
        }
        if (game.getAway_score() > game.getHome_score()) {
            return game.getAway_nation_id();
        }
        return 0;
    }

    private static long asOfMs(List<BBGame> games) {
        long latest = 0L;
        for (BBGame game : games) {
            if (game.getDate() != null) {
                latest = Math.max(latest, game.getDate().toEpochMilli());
            }
        }
        return latest == 0L ? System.currentTimeMillis() : latest;
    }

    private static boolean matches(Request request, BBGame game) {
        if (request.timeStartMs() > 0L && (game.getDate() == null || game.getDate().toEpochMilli() <= request.timeStartMs())) {
            return false;
        }
        return !request.challengeOnly() || game.getWager() > 0d;
    }
}

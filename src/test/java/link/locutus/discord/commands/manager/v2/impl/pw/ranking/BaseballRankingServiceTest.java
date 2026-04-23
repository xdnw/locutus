package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.politicsandwar.graphql.model.BBGame;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.BaseballRankingRequests;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseballRankingServiceTest {
    @Test
    void gamesRankingCountsAppearancesPerNationAndSortsDeterministically() {
        RankingResult result = BaseballRankingService.ranking(
                BaseballRankingRequests.games(0L, false),
                List.of(
                        game(1, 1_000L, 1, 2, 3, 1, 0d, 0d),
                        game(2, 2_000L, 1, 3, 1, 2, 0d, 0d),
                        game(3, 3_000L, 2, 3, 2, 1, 0d, 0d),
                        game(4, 4_000L, 1, 4, 4, 2, 0d, 0d)
                )
        );

        assertEquals(RankingKind.BASEBALL_GAMES, result.kind());
        assertEquals(RankingEntityType.NATION, result.keyType());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.keyIds());
        assertEquals(List.of(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("1")), result.valueColumns().get(0).values());
        assertEquals(List.of(new RankingSectionRange(RankingSectionKind.NATIONS, 0, 4)), result.sectionRanges());
        assertEquals(4_000L, result.asOfMs());
    }

    @Test
    void challengeEarningsRankingAggregatesWinnerWagerByAlliance() {
        RankingResult result = BaseballRankingService.ranking(
                BaseballRankingRequests.challengeEarnings(true),
                List.of(
                        game(1, 1_000L, 10, 12, 5, 3, 6d, 5d),
                        game(2, 2_000L, 11, 13, 4, 1, 7d, 7d),
                        game(3, 3_000L, 12, 14, 3, 0, 4d, 4d)
                ),
                nationId -> switch (nationId) {
                    case 10, 11 -> 100;
                    case 12 -> 200;
                    default -> 0;
                }
        );

        assertEquals(RankingKind.BASEBALL_CHALLENGE_EARNINGS, result.kind());
        assertEquals(RankingEntityType.ALLIANCE, result.keyType());
        assertEquals(List.of(100L, 200L), result.keyIds());
        assertEquals(RankingValueFormat.MONEY, result.valueColumns().get(0).format());
        assertEquals(List.of(new BigDecimal("12.0"), new BigDecimal("4.0")), result.valueColumns().get(0).values());
    }

    private static BBGame game(int id, long dateMs, int homeNationId, int awayNationId, int homeScore, int awayScore,
            double spoils, double wager) {
        BBGame game = new BBGame();
        game.setId(id);
        game.setDate(Instant.ofEpochMilli(dateMs));
        game.setHome_nation_id(homeNationId);
        game.setAway_nation_id(awayNationId);
        game.setHome_score(homeScore);
        game.setAway_score(awayScore);
        game.setSpoils(spoils);
        game.setWager(wager);
        return game;
    }
}

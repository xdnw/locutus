package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.impl.pw.ranking.BaseballRankingService;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingKind;

public final class BaseballRankingRequests {
    private BaseballRankingRequests() {
    }

    public static BaseballRankingService.Request games(long timeStartMs, boolean byAlliance) {
        return new BaseballRankingService.Request(
                RankingKind.BASEBALL_GAMES,
                timeStartMs,
                false,
                byAlliance,
                BaseballRankingService.ValueMode.GAMES
        );
    }

    public static BaseballRankingService.Request challengeGames(boolean byAlliance) {
        return new BaseballRankingService.Request(
                RankingKind.BASEBALL_CHALLENGE_GAMES,
                0L,
                true,
                byAlliance,
                BaseballRankingService.ValueMode.GAMES
        );
    }

    public static BaseballRankingService.Request earnings(long timeStartMs, boolean byAlliance) {
        return new BaseballRankingService.Request(
                RankingKind.BASEBALL_EARNINGS,
                timeStartMs,
                false,
                byAlliance,
                BaseballRankingService.ValueMode.EARNINGS
        );
    }

    public static BaseballRankingService.Request challengeEarnings(boolean byAlliance) {
        return new BaseballRankingService.Request(
                RankingKind.BASEBALL_CHALLENGE_EARNINGS,
                0L,
                true,
                byAlliance,
                BaseballRankingService.ValueMode.EARNINGS
        );
    }
}

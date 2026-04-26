package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.impl.pw.ranking.IncentiveRankingService;

public final class IncentiveRankingRequests {
    private IncentiveRankingRequests() {
    }

    public static IncentiveRankingService.Request ranking(long timeStartMs) {
        return new IncentiveRankingService.Request(timeStartMs);
    }
}

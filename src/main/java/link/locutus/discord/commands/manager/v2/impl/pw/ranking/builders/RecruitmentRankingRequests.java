package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RecruitmentRankingService;

public final class RecruitmentRankingRequests {
    private RecruitmentRankingRequests() {
    }

    public static RecruitmentRankingService.Request ranking(long cutoffMs, int topX) {
        return new RecruitmentRankingService.Request(cutoffMs, topX);
    }
}

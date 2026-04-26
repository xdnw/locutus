package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.impl.pw.ranking.OffshoreRankingService;
import link.locutus.discord.db.entities.DBAlliance;

public final class OffshoreRankingRequests {
    private OffshoreRankingRequests() {
    }

    public static OffshoreRankingService.PotentialRequest potential(DBAlliance alliance, Long cutoffMs, boolean transferCount) {
        long resolvedCutoffMs = cutoffMs == null ? System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(200) : cutoffMs;
        return new OffshoreRankingService.PotentialRequest(
                alliance.getAlliance_id(),
                resolvedCutoffMs,
                transferCount
        );
    }

    public static OffshoreRankingService.ProlificRequest prolific(long cutoffMs) {
        return new OffshoreRankingService.ProlificRequest(cutoffMs);
    }
}

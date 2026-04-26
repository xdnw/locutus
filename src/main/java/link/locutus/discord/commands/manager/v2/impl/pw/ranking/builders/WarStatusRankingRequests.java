package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarStatusRankingService;
import link.locutus.discord.db.entities.DBNation;

import java.util.Set;

public final class WarStatusRankingRequests {
    private WarStatusRankingRequests() {
    }

    public static WarStatusRankingService.Request status(
            boolean byAlliance,
            Set<DBNation> attackers,
            Set<DBNation> defenders,
            long timeStartMs
    ) {
        return new WarStatusRankingService.Request(
                byAlliance,
                RankingRequestSupport.nullableNationSet(attackers),
                RankingRequestSupport.nullableNationSet(defenders),
                timeStartMs
        );
    }
}

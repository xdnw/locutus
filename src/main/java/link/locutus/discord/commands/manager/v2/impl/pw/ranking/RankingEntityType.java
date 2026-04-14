package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.Locale;

public enum RankingEntityType {
    ALLIANCE,
    NATION;

    public String key(long entityId) {
        return name().toLowerCase(Locale.ROOT) + ":" + entityId;
    }
}

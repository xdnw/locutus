package link.locutus.discord.event.game;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class TurnChangeEvent extends GuildScopeEvent { // todo post to all guilds?
    private final long previous;
    private final long current;

    public TurnChangeEvent(long previousTurn, long currentTurn) {
        this.previous = previousTurn;
        this.current = currentTurn;
    }

    public long getPrevious() {
        return previous;
    }

    public long getCurrent() {
        return current;
    }

    @Override
    protected void postToGuilds() {
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            DBAlliance alliance = db.getAlliance();
            if (alliance == null) continue;
            boolean hasActiveLeaderOrHeir = alliance
                    .getNations(true, 7200, true)
                    .stream().anyMatch(f -> f.getPositionEnum().id >= Rank.HEIR.id);
            if (!hasActiveLeaderOrHeir) continue;

            post(db);
        }

    }
}

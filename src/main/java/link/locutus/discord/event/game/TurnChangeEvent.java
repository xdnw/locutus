package link.locutus.discord.event.game;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.guild.GuildScopeEvent;
import link.locutus.discord.pnw.AllianceList;

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
            AllianceList alliances = db.getAllianceList();
            if (alliances == null || alliances.isEmpty()) continue;
            boolean hasAlliance = false;
            for (DBAlliance alliance : alliances.getAlliances()) {
                if (alliance
                        .getNations(true, 7200, true)
                        .stream().anyMatch(f -> f.getPositionEnum().id >= Rank.HEIR.id)) {
                    hasAlliance = true;
                }
            }
            if (hasAlliance) post(db);
        }

    }
}

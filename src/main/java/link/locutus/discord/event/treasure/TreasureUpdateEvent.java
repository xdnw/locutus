package link.locutus.discord.event.treasure;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class TreasureUpdateEvent extends Event {
    private final DBTreasure previous;
    private final DBTreasure current;

    public TreasureUpdateEvent(DBTreasure previous, DBTreasure current) {
        this.previous = previous;
        this.current = current;
    }

    public DBTreasure getPrevious() {
        return previous;
    }

    public DBTreasure getCurrent() {
        return current;
    }
}

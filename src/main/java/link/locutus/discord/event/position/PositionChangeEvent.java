package link.locutus.discord.event.position;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class PositionChangeEvent extends GuildScopeEvent {
    private final DBAlliancePosition previous;
    private final DBAlliancePosition current;

    public PositionChangeEvent(DBAlliancePosition previous, DBAlliancePosition current) {
        this.previous = previous;
        this.current = current;
    }

    public DBAlliancePosition getPrevious() {
        return previous;
    }

    public DBAlliancePosition getCurrent() {
        return current;
    }

    @Override
    protected void postToGuilds() {
        GuildDB db = null;
        if (previous != null && previous.getAlliance_id() != 0) {
            db = previous.getGuildDB();
            if (db != null) {
                post(db);
            }
        }
        if (current != null && (previous == null || current.getAlliance_id() != previous.getAlliance_id())) {
            GuildDB db2 = current.getGuildDB();
            if (db2 != db) {
                post(db2);
            }
        }
    }
}

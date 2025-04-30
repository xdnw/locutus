package link.locutus.discord.event.nation;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class NationChangeEvent2 extends GuildScopeEvent {
    private final DBNation previous;
    private final DBNation current;

    public NationChangeEvent2(DBNation original, DBNation changed) {
        this.previous = original;
        this.current = changed;
    }

    public DBNation getPrevious() {
        return previous;
    }

    public DBNation getCurrent() {
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

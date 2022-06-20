package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;

public class NationChangeEvent2 extends Event {
    private final DBNation original;
    private final DBNation changed;

    public NationChangeEvent2(DBNation original, DBNation changed) {
        this.original = original;
        this.changed = changed;
    }

    public DBNation getPrevious() {
        return original;
    }

    public DBNation getCurrent() {
        return changed;
    }
}

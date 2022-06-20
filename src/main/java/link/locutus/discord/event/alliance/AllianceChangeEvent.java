package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.Event;

public class AllianceChangeEvent extends Event {
    private final DBAlliance current;
    private final DBAlliance previous;

    public AllianceChangeEvent(DBAlliance previous, DBAlliance current) {
        this.previous = previous;
        this.current = current;
    }

    public DBAlliance getPrevious() {
        return previous;
    }

    public DBAlliance getCurrent() {
        return current;
    }
}

package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.event.Event;

public class PositionChangeEvent extends Event {
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
}

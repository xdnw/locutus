package com.boydti.discord.event;

import com.boydti.discord.db.entities.DBWar;

public class WarUpdateEvent {
    private final DBWar previous;
    private final DBWar current;

    public WarUpdateEvent(DBWar previous, DBWar current) {
        this.previous = previous;
        this.current = current;
    }

    public DBWar getPrevious() {
        return previous;
    }

    public DBWar getCurrent() {
        return current;
    }
}

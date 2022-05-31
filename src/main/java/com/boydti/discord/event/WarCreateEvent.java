package com.boydti.discord.event;

import com.boydti.discord.db.entities.DBWar;

public class WarCreateEvent {
    private final DBWar war;

    public WarCreateEvent(DBWar war) {
        this.war = war;
    }

    public DBWar getWar() {
        return war;
    }
}

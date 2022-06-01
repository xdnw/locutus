package link.locutus.discord.event;

import link.locutus.discord.db.entities.DBWar;

public class WarCreateEvent {
    private final DBWar war;

    public WarCreateEvent(DBWar war) {
        this.war = war;
    }

    public DBWar getWar() {
        return war;
    }
}

package link.locutus.discord.event.war;

import link.locutus.discord.db.entities.DBWar;

public class WarStatusChangeEvent extends WarUpdateEvent {
    public WarStatusChangeEvent(DBWar previous, DBWar current) {
        super(previous, current);
    }
}

package link.locutus.discord.event.war;

import link.locutus.discord.db.entities.DBWar;

public class WarPeaceStatusEvent extends WarUpdateEvent {
    public WarPeaceStatusEvent(DBWar previous, DBWar current) {
        super(previous, current);
    }
}

package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceCreateEvent extends AllianceChangeEvent {
    public AllianceCreateEvent(DBAlliance current) {
        super(null, current);
        setTime(current.getDateCreated());
    }
}

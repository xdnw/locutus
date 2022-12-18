package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceDeleteEvent extends AllianceChangeEvent {
    public AllianceDeleteEvent(DBAlliance current) {
        super(current, null);
    }
}

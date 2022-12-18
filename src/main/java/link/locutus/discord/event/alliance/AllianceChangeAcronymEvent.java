package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeAcronymEvent extends AllianceChangeEvent {
    public AllianceChangeAcronymEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeNameEvent extends AllianceChangeEvent {
    public AllianceChangeNameEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

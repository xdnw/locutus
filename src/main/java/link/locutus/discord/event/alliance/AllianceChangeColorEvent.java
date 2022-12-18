package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeColorEvent extends AllianceChangeEvent {
    public AllianceChangeColorEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

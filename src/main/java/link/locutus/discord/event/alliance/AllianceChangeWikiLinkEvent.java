package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeWikiLinkEvent extends AllianceChangeEvent {
    public AllianceChangeWikiLinkEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

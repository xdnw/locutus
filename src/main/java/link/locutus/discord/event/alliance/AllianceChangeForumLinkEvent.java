package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeForumLinkEvent extends AllianceChangeEvent {
    public AllianceChangeForumLinkEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

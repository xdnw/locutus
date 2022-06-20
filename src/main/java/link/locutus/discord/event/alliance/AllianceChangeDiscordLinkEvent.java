package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeDiscordLinkEvent extends AllianceChangeEvent {
    public AllianceChangeDiscordLinkEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

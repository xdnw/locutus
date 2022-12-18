package link.locutus.discord.event.alliance;

import link.locutus.discord.db.entities.DBAlliance;

public class AllianceChangeFlagEvent extends AllianceChangeEvent {
    public AllianceChangeFlagEvent(DBAlliance previous, DBAlliance current) {
        super(previous, current);
    }
}

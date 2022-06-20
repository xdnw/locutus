package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeAllianceEvent extends NationChangeEvent2 {
    public NationChangeAllianceEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

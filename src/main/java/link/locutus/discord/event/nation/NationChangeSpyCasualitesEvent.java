package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeSpyCasualitesEvent extends NationChangeEvent2 {
    public NationChangeSpyCasualitesEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

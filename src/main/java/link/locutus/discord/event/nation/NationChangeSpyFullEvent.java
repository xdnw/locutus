package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeSpyFullEvent extends NationChangeEvent2 {
    public NationChangeSpyFullEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

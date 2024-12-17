package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeDefEvent extends NationChangeEvent2 {
    public NationChangeDefEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

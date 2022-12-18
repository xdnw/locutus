package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeActiveEvent extends NationChangeEvent2 {
    public NationChangeActiveEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

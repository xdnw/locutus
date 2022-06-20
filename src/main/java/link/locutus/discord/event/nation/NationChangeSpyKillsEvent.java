package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeSpyKillsEvent extends NationChangeEvent2 {
    public NationChangeSpyKillsEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

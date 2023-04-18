package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeGNIEvent extends NationChangeEvent2 {
    public NationChangeGNIEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

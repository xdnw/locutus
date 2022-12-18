package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeColorEvent extends NationChangeEvent2 {
    public NationChangeColorEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeDCEvent extends NationChangeEvent2 {
    public NationChangeDCEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

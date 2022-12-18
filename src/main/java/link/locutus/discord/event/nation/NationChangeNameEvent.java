package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeNameEvent extends NationChangeEvent2 {
    public NationChangeNameEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

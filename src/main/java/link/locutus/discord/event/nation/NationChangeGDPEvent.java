package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeGDPEvent extends NationChangeEvent2 {
    public NationChangeGDPEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

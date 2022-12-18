package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeRankEvent extends NationChangeEvent2 {
    public NationChangeRankEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

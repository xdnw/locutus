package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeLeaderEvent extends NationChangeEvent2 {
    public NationChangeLeaderEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

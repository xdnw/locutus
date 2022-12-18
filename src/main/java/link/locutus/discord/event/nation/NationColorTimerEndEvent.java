package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationColorTimerEndEvent extends NationChangeEvent2{
    public NationColorTimerEndEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

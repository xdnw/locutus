package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationProjectTimerEndEvent extends NationChangeEvent2{
    public NationProjectTimerEndEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

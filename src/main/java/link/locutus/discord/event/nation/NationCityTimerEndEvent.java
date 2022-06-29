package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationCityTimerEndEvent extends NationChangeEvent2{
    public NationCityTimerEndEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

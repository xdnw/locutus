package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeVacationEvent extends NationChangeEvent2 {
    public NationChangeVacationEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationLeaveVacationEvent extends NationChangeEvent2{
    public NationLeaveVacationEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

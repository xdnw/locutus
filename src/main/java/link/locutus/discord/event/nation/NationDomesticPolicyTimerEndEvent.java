package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationDomesticPolicyTimerEndEvent extends NationChangeEvent2{
    public NationDomesticPolicyTimerEndEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

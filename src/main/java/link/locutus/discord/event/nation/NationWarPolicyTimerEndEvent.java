package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationWarPolicyTimerEndEvent extends NationChangeEvent2{
    public NationWarPolicyTimerEndEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

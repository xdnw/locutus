package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationLeaveBeigeEvent extends NationChangeEvent2{
    public NationLeaveBeigeEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeCitiesEvent extends NationChangeEvent2 {
    public NationChangeCitiesEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

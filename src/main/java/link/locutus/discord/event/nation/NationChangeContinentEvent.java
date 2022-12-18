package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeContinentEvent extends NationChangeEvent2 {
    public NationChangeContinentEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeSpyCasualtiesEvent extends NationChangeEvent2 {
    public NationChangeSpyCasualtiesEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

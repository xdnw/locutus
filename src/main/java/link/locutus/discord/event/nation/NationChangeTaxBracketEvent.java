package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeTaxBracketEvent extends NationChangeEvent2 {
    public NationChangeTaxBracketEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

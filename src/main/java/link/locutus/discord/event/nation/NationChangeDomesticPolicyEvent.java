package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeDomesticPolicyEvent extends NationChangeEvent2 {
    public NationChangeDomesticPolicyEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

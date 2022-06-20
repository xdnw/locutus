package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationChangeWarPolicyEvent extends NationChangeEvent2 {
    public NationChangeWarPolicyEvent(DBNation original, DBNation changed) {
        super(original, changed);
    }
}

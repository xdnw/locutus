package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;

public class NationDeleteEvent extends NationChangeEvent2 {
    public NationDeleteEvent(DBNation original) {
        super(original, null);
    }
}

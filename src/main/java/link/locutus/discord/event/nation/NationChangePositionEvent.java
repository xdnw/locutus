package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.nation.NationChangeEvent2;

public class NationChangePositionEvent extends NationChangeEvent2 {
    public NationChangePositionEvent(DBNation previous, DBNation current) {
        super(previous, current);
    }
}

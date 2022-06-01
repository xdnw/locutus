package link.locutus.discord.event;

import link.locutus.discord.pnw.DBNation;

public class NationChangePositionEvent extends NationChangeEvent {
    public NationChangePositionEvent(DBNation previous, DBNation current) {
        super(previous, current);
    }
}

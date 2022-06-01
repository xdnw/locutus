package link.locutus.discord.event;

import link.locutus.discord.pnw.DBNation;

public class NationBuyCityEvent extends NationChangeEvent {
    public NationBuyCityEvent(DBNation previous, DBNation current) {
        super(previous, current);
    }
}

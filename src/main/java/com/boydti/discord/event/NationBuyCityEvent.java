package com.boydti.discord.event;

import com.boydti.discord.pnw.DBNation;

public class NationBuyCityEvent extends NationChangeEvent {
    public NationBuyCityEvent(DBNation previous, DBNation current) {
        super(previous, current);
    }
}

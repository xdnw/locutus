package com.boydti.discord.event;

import com.boydti.discord.pnw.DBNation;

public class NationActiveEvent5 extends Event{
    private final DBNation previous;
    private final DBNation current;

    public NationActiveEvent5(DBNation previous, DBNation current) {
        this.previous = previous;
        this.current = current;
    }

    public DBNation getPrevious() {
        return previous;
    }

    public DBNation getCurrent() {
        return current;
    }
}

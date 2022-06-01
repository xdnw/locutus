package link.locutus.discord.event;

import link.locutus.discord.pnw.DBNation;

public class NationActiveEvent120 extends Event{
    private final DBNation previous;
    private final DBNation current;

    public NationActiveEvent120(DBNation previous, DBNation current) {
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

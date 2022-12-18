package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.event.Event;

public class TradeEvent extends Event {
    private final DBTrade previous;
    private final DBTrade current;

    public TradeEvent(DBTrade previous, DBTrade current) {
        this.previous = previous;
        this.current = current;
        if (current != null && current.getDate_accepted() > 0) {
            setTime(current.getDate_accepted());
        } else if (current == null) {
            setTime(previous.getDate());
        }
    }

    public DBTrade getPrevious() {
        return previous;
    }

    public DBTrade getCurrent() {
        return current;
    }
}

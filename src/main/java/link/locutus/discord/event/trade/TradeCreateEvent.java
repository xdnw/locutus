package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;

public class TradeCreateEvent extends TradeEvent{
    public TradeCreateEvent(DBTrade current) {
        super(null, current);
    }
}

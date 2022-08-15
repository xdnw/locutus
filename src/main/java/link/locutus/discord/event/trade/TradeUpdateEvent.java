package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;

public class TradeUpdateEvent extends TradeEvent{
    public TradeUpdateEvent(DBTrade previous, DBTrade current) {
        super(previous, current);
    }
}

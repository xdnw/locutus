package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;

public class TradeDeleteEvent extends TradeEvent{
    public TradeDeleteEvent(DBTrade previous) {
        super(previous, null);
    }
}

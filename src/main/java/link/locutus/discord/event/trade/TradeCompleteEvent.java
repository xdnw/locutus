package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;

public class TradeCompleteEvent extends TradeEvent{
    public TradeCompleteEvent(DBTrade previous, DBTrade current) {
        super(previous, current);
        setTime(current.getDate_accepted());
    }
}

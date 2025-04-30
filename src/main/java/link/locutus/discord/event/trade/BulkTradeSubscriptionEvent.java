package link.locutus.discord.event.trade;

import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.event.Event;

import java.util.List;

public class BulkTradeSubscriptionEvent extends Event {
    private final List<TradeSubscription> subscriptions;
    private final DBTrade oldTopBuy;
    private final DBTrade oldTopSell;
    private final DBTrade newTopBuy;
    private final DBTrade newTopSell;


    public BulkTradeSubscriptionEvent(List<TradeSubscription> subscriptions, DBTrade oldTopBuy, DBTrade oldTopSell, DBTrade newTopBuy, DBTrade newTopSell) {
        this.oldTopBuy = oldTopBuy;
        this.oldTopSell = oldTopSell;
        this.newTopBuy = newTopBuy;
        this.newTopSell = newTopSell;
        this.subscriptions = subscriptions;

    }

    public List<TradeSubscription> getSubscriptions() {
        return subscriptions;
    }

    public DBTrade getPreviousTop(boolean isBuy) {
        return isBuy ? oldTopBuy : oldTopSell;
    }

    public DBTrade getCurrentTop(boolean isBuy) {
        return isBuy ? newTopBuy : newTopSell;
    }
}

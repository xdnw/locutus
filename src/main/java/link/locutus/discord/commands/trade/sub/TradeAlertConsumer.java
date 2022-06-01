package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.db.TradeDB;

import java.util.Set;

public interface TradeAlertConsumer {
    public void accept(Set<Long> pings, TradeDB.TradeAlertType alertType, TradeAlert alert, boolean checkRole);
}
